package com.chunkswap;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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

public class ChunkSwapMod implements ModInitializer {
	public static final String MOD_ID = "chunkswap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile boolean enabled = true;
	private static Path configPath;

	@Override
	public void onInitialize() {
		configPath = FabricLoader.getInstance().getConfigDir().resolve("chunkswap.txt");
		loadState();

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!enabled || !(world instanceof ServerWorld serverWorld)) {
				return;
			}
			swapChunk(serverWorld, pos, state);
		});

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
		saveState();
		String text = value ? "ChunkSwap включён" : "ChunkSwap выключен";
		ctx.getSource().sendFeedback(() -> Text.literal(text), true);
		return 1;
	}

	private static void swapChunk(ServerWorld world, BlockPos brokenPos, BlockState brokenState) {
		Block brokenBlock = brokenState.getBlock();
		if (brokenState.isAir() || brokenBlock == Blocks.BEDROCK) {
			return;
		}

		BlockState target = brokenBlock.getDefaultState();
		ChunkPos chunkPos = new ChunkPos(brokenPos);
		int minY = world.getBottomY();
		int maxY = world.getTopY();
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
			for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
				for (int y = minY; y < maxY; y++) {
					cursor.set(x, y, z);
					BlockState current = world.getBlockState(cursor);
					if (current.isAir() || current.isOf(Blocks.BEDROCK) || current == target) {
						continue;
					}
					if (current.hasBlockEntity()) {
						world.removeBlockEntity(cursor);
					}
					world.setBlockState(cursor, target, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
				}
			}
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
