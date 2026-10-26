package com.chunkswap;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChunkSwapMod implements ModInitializer {
	public static final String MOD_ID = "chunkswap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Сколько наносекунд за один тик можно тратить на замену блоков (6 мс).
	// Чем меньше число, тем меньше лагов, но дольше превращается чанк.
	private static final long BUDGET_NANOS = 6_000_000L;

	private static volatile boolean enabled = true;
	private static Path configPath;
	private static final List<Job> JOBS = new ArrayList<>();

	@Override
	public void onInitialize() {
		configPath = FabricLoader.getInstance().getConfigDir().resolve("chunkswap.txt");
		loadState();

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!enabled || !(world instanceof ServerWorld serverWorld)) {
				return;
			}
			queueSwap(serverWorld, pos, state);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> tickJobs());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(CommandManager.literal("chunkswap")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> status(ctx))
				.then(CommandManager.literal("on").executes(ctx -> setEnabled(ctx, true)))
				.then(CommandManager.literal("off").executes(ctx -> setEnabled(ctx, false)))));
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		String text = enabled ? "ChunkSwap сейчас ВКЛЮЧЁН" : "ChunkSwap сейчас ВЫКЛЮЧЕН";
		ctx.getSource().sendFeedback(() -> Text.literal(text), false);
		return 1;
	}

	private static int setEnabled(CommandContext<ServerCommandSource> ctx, boolean value) {
		enabled = value;
		if (!value) {
			JOBS.clear();
		}
		saveState();
		String text = value ? "ChunkSwap включён" : "ChunkSwap выключен";
		ctx.getSource().sendFeedback(() -> Text.literal(text), true);
		return 1;
	}

	private static void queueSwap(ServerWorld world, BlockPos brokenPos, BlockState brokenState) {
		Block brokenBlock = brokenState.getBlock();
		if (brokenState.isAir() || brokenBlock == Blocks.BEDROCK) {
			return;
		}
		ChunkPos chunkPos = new ChunkPos(brokenPos);
		// Если этот чанк уже превращается, старое задание заменяется новым.
		JOBS.removeIf(job -> job.world == world && job.chunkPos.equals(chunkPos));
		JOBS.add(new Job(world, chunkPos, brokenBlock.getDefaultState()));
	}

	private static void tickJobs() {
		if (JOBS.isEmpty()) {
			return;
		}
		long deadline = System.nanoTime() + BUDGET_NANOS;
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
		final ChunkPos chunkPos;
		final BlockState target;
		final int minY;
		final long total;
		long index = 0;
		final BlockPos.Mutable cursor = new BlockPos.Mutable();

		Job(ServerWorld world, ChunkPos chunkPos, BlockState target) {
			this.world = world;
			this.chunkPos = chunkPos;
			this.target = target;
			this.minY = world.getBottomY();
			this.total = 256L * (world.getTopY() - world.getBottomY());
		}

		/** Возвращает true, если работа ещё не закончена. */
		boolean step(long deadline) {
			if (!world.isChunkLoaded(chunkPos.getStartPos())) {
				return false;
			}
			int startX = chunkPos.getStartX();
			int startZ = chunkPos.getStartZ();
			int counter = 0;
			while (index < total) {
				int y = minY + (int) (index / 256);
				int rem = (int) (index % 256);
				int x = startX + rem / 16;
				int z = startZ + rem % 16;
				index++;

				cursor.set(x, y, z);
				BlockState current = world.getBlockState(cursor);
				if (!current.isAir() && !current.isOf(Blocks.BEDROCK) && current != target) {
					// Убираем блок-сущность заранее, чтобы содержимое сундуков и т.п. не выпадало предметами.
					if (current.hasBlockEntity()) {
						world.removeBlockEntity(cursor);
					}
					world.setBlockState(cursor, target, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
				}

				if ((++counter & 255) == 0 && System.nanoTime() >= deadline) {
					return true;
				}
			}
			return false;
		}
	}

	private static void loadState() {
		try {
			if (Files.exists(configPath)) {
				enabled = Boolean.parseBoolean(Files.readString(configPath).trim());
			}
		} catch (IOException e) {
			LOGGER.warn("Не удалось прочитать {}", configPath, e);
		}
	}

	private static void saveState() {
		try {
			Files.writeString(configPath, Boolean.toString(enabled));
		} catch (IOException e) {
			LOGGER.warn("Не удалось сохранить {}", configPath, e);
		}
	}
}
