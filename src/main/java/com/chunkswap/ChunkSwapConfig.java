package com.chunkswap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки мода. Хранятся в config/chunkswap.json.
 */
public class ChunkSwapConfig {
	public enum Mode {
		/** Весь чанк по всей высоте. */
		CHUNK,
		/** Горизонтальный слой чанка на высоте сломанного блока. */
		LAYER,
		/** Шар вокруг сломанного блока. */
		SPHERE,
		/** Куб вокруг сломанного блока. */
		CUBE,
		/** Часть чанка от сломанного блока и выше. */
		ABOVE,
		/** Часть чанка от сломанного блока и ниже. */
		BELOW,
		/** Весь чанк, но каждый второй блок (шахматный порядок). */
		CHECKER
	}

	public boolean enabled = true;
	public Mode mode = Mode.CHUNK;
	/** Радиус для режимов SPHERE и CUBE. */
	public int radius = 8;
	/** Толщина слоя для режима LAYER. */
	public int layerThickness = 1;
	/** Превращать не в сломанный, а в случайный блок. */
	public boolean randomBlock = false;
	/** Шанс срабатывания в процентах. */
	public int chance = 100;
	/** Сколько миллисекунд за тик можно тратить на замену блоков. */
	public int budgetMs = 6;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ChunkSwapConfig instance = new ChunkSwapConfig();

	public static ChunkSwapConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("chunkswap.json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				ChunkSwapConfig loaded = GSON.fromJson(Files.readString(path), ChunkSwapConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				ChunkSwapMod.LOGGER.warn("Не удалось прочитать {}, используются значения по умолчанию", path, e);
				instance = new ChunkSwapConfig();
			}
		}
		instance.clamp();
		save();
	}

	public static void save() {
		instance.clamp();
		try {
			Files.writeString(path(), GSON.toJson(instance));
		} catch (IOException e) {
			ChunkSwapMod.LOGGER.warn("Не удалось сохранить {}", path(), e);
		}
	}

	private void clamp() {
		if (mode == null) {
			mode = Mode.CHUNK;
		}
		radius = Math.max(1, Math.min(32, radius));
		layerThickness = Math.max(1, Math.min(32, layerThickness));
		chance = Math.max(1, Math.min(100, chance));
		budgetMs = Math.max(1, Math.min(40, budgetMs));
	}
}
