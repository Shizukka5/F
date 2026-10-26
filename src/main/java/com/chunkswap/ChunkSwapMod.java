package com.chunkswap;

import com.chunkswap.ChunkSwapConfig.Mode;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChunkSwapMod implements ModInitializer {
	public static final String MOD_ID = "chunkswap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int MAX_JOBS = 200;
	private static final List<Job> JOBS = new ArrayList<>();
	private static List<Block> randomCandidates;

	@Override
	public void onInitialize() {
		ChunkSwapConfig.load();

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			ChunkSwapConfig cfg = ChunkSwapConfig.get();
			if (!cfg.enabled || !(world instanceof ServerWorld serverWorld)) {
				return;
			}
			queueSwap(serverWorld, pos, state, cfg);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> tickJobs());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(CommandManager.literal("chunkswap")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> status(ctx))
				.then(CommandManager.literal("on").executes(ctx -> setEnabled(ctx, true)))
				.then(CommandManager.literal("off").executes(ctx -> setEnabled(ctx, false)))
				.then(CommandManager.literal("reload").executes(ctx -> reload(ctx)))));
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		ChunkSwapConfig cfg = ChunkSwapConfig.get();
		String text = (cfg.enabled ? "ChunkSwap: ВКЛЮЧЁН" : "ChunkSwap: ВЫКЛЮЧЕН")
			+ " | режим: " + cfg.mode
			+ " | радиус: " + cfg.radius
			+ " | шанс: " + cfg.chance + "%"
			+ " | случайный блок: " + (cfg.randomBlock ? "да" : "нет");
		ctx.getSource().sendFeedback(() -> Text.literal(text), false);
		return 1;
	}

	private static int setEnabled(CommandContext<ServerCommandSource> ctx, boolean value) {
		ChunkSwapConfig.get().enabled = value;
		if (!value) {
			JOBS.clear();
		}
		ChunkSwapConfig.save();
		String text = value ? "ChunkSwap включён" : "ChunkSwap выключен";
		ctx.getSource().sendFeedback(() -> Text.literal(text), true);
		return 1;
	}

	private static int reload(CommandContext<ServerCommandSource> ctx) {
		ChunkSwapConfig.load();
		ctx.getSource().sendFeedback(() -> Text.literal("Настройки ChunkSwap перезагружены"), true);
		return 1;
	}

	private static void queueSwap(ServerWorld world, BlockPos brokenPos, BlockState brokenState, ChunkSwapConfig cfg) {
		Block brokenBlock = brokenState.getBlock();
		if (brokenState.isAir() || brokenBlock == Blocks.BEDROCK) {
			return;
		}
		if (cfg.chance < 100 && ThreadLocalRandom.current().nextInt(100) >= cfg.chance) {
			return;
		}

		BlockState target = brokenBlock.getDefaultState();
		if (cfg.randomBlock) {
			BlockState random = pickRandom(world);
			if (random != null) {
				target = random;
			}
		}

		Job job = new Job(world, brokenPos, target, cfg.mode, cfg.radius, cfg.layerThickness);
		if (job.chunkBased) {
			// Если этот чанк уже превращается, старое задание заменяется новым.
			JOBS.removeIf(j -> j.chunkBased && j.world == world && j.chunkX == job.chunkX && j.chunkZ == job.chunkZ);
		}
		if (JOBS.size() < MAX_JOBS) {
			JOBS.add(job);
		}
	}

	/** Выбирает случайный цельный блок. Блоки с инвентарём, падающие и неломаемые не берутся. */
	private static BlockState pickRandom(ServerWorld world) {
		if (randomCandidates == null) {
			List<Block> list = new ArrayList<>();
			for (Block block : Registries.BLOCK) {
				BlockState state = block.getDefaultState();
				if (state.isAir()
					|| state.hasBlockEntity()
					|| block instanceof FallingBlock
					|| state.getHardness(world, BlockPos.ORIGIN) < 0
					|| !state.isFullCube(world, BlockPos.ORIGIN)) {
					continue;
				}
				list.add(block);
			}
			randomCandidates = list;
		}
		if (randomCandidates.isEmpty()) {
			return null;
		}
		return randomCandidates.get(ThreadLocalRandom.current().nextInt(randomCandidates.size())).getDefaultState();
	}

	private static void tickJobs() {
		if (JOBS.isEmpty()) {
			return;
		}
		long budget = Math.max(1, ChunkSwapConfig.get().budgetMs) * 1_000_000L;
		long deadline = System.nanoTime() + budget;
		Iterator<Job> iterator = JOBS.iterator();
		while (iterator.hasNext()) {
			Job job = iterator.next();
			if (!job.step(deadline)) {
				iterator.remove();
			}
			if (System.nanoTime() >= deadline) {
				break;
			}
		}
	}

	private static final class Job {
		final ServerWorld world;
		final BlockState target;
		final Mode mode;
		final BlockPos origin;
		final boolean chunkBased;
		final int chunkX;
		final int chunkZ;
		final int minX;
		final int minY;
		final int minZ;
		final int sizeX;
		final int sizeZ;
		final int radiusSq;
		final long total;
		long index = 0;

		final BlockPos.Mutable cursor = new BlockPos.Mutable();
		int lastChunkX = Integer.MIN_VALUE;
		int lastChunkZ = Integer.MIN_VALUE;
		boolean lastLoaded = false;

		Job(ServerWorld world, BlockPos origin, BlockState target, Mode mode, int radius, int thickness) {
			this.world = world;
			this.origin = origin;
			this.target = target;
			this.mode = mode;
			this.radiusSq = radius * radius;

			int bottom = world.getBottomY();
			int top = world.getTopY() - 1;
			this.chunkX = origin.getX() >> 4;
			this.chunkZ = origin.getZ() >> 4;

			int x0 = chunkX << 4;
			int x1 = x0 + 15;
			int z0 = chunkZ << 4;
			int z1 = z0 + 15;
			int y0 = bottom;
			int y1 = top;
			boolean byChunk = true;

			switch (mode) {
				case LAYER -> {
					int lo = origin.getY() - thickness / 2;
					y0 = Math.max(bottom, lo);
					y1 = Math.min(top, lo + thickness - 1);
				}
				case SPHERE, CUBE -> {
					byChunk = false;
					x0 = origin.getX() - radius;
					x1 = origin.getX() + radius;
					z0 = origin.getZ() - radius;
					z1 = origin.getZ() + radius;
					y0 = Math.max(bottom, origin.getY() - radius);
					y1 = Math.min(top, origin.getY() + radius);
				}
				case ABOVE -> y0 = Math.max(bottom, origin.getY());
				case BELOW -> y1 = Math.min(top, origin.getY());
				default -> {
				}
			}

			this.chunkBased = byChunk;
			this.minX = x0;
			this.minY = y0;
			this.minZ = z0;
			this.sizeX = x1 - x0 + 1;
			this.sizeZ = z1 - z0 + 1;
			int sizeY = Math.max(0, y1 - y0 + 1);
			this.total = (long) sizeX * sizeZ * sizeY;
		}

		private boolean inShape(int x, int y, int z) {
			return switch (mode) {
				case SPHERE -> {
					int dx = x - origin.getX();
					int dy = y - origin.getY();
					int dz = z - origin.getZ();
					yield dx * dx + dy * dy + dz * dz <= radiusSq;
				}
				case CHECKER -> ((x + y + z) & 1) == 0;
				default -> true;
			};
		}

		/** Возвращает true, если работа ещё не закончена. */
		boolean step(long deadline) {
			long layerSize = (long) sizeX * sizeZ;
			int counter = 0;
			while (index < total) {
				if ((++counter & 255) == 0 && System.nanoTime() >= deadline) {
					return true;
				}

				int y = minY + (int) (index / layerSize);
				int rem = (int) (index % layerSize);
				int x = minX + rem / sizeZ;
				int z = minZ + rem % sizeZ;
				index++;

				if (!inShape(x, y, z)) {
					continue;
				}

				int cx = x >> 4;
				int cz = z >> 4;
				if (cx != lastChunkX || cz != lastChunkZ) {
					lastChunkX = cx;
					lastChunkZ = cz;
					lastLoaded = world.isChunkLoaded(cursor.set(x, y, z));
				}
				if (!lastLoaded) {
					if (chunkBased) {
						return false;
					}
					continue;
				}

				cursor.set(x, y, z);
				BlockState current = world.getBlockState(cursor);
				if (current.isAir() || current.isOf(Blocks.BEDROCK) || current == target) {
					continue;
				}
				// Убираем блок-сущность заранее, чтобы содержимое сундуков и т.п. не выпадало предметами.
				if (current.hasBlockEntity()) {
					world.removeBlockEntity(cursor);
				}
				world.setBlockState(cursor, target, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
			return false;
		}
	}
}
