package com.chunkswap.client;

import com.chunkswap.ChunkSwapConfig;
import com.chunkswap.ChunkSwapConfig.Mode;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Экран настроек на основе Cloth Config.
 */
public class ChunkSwapConfigScreen {
	public static Screen create(Screen parent) {
		ChunkSwapConfig cfg = ChunkSwapConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Text.translatable("chunkswap.config.title"))
			.setSavingRunnable(ChunkSwapConfig::save);

		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(Text.translatable("chunkswap.config.category.general"));

		general.addEntry(entries
			.startBooleanToggle(Text.translatable("chunkswap.config.enabled"), cfg.enabled)
			.setDefaultValue(true)
			.setTooltip(Text.translatable("chunkswap.config.enabled.tooltip"))
			.setSaveConsumer(value -> cfg.enabled = value)
			.build());

		general.addEntry(entries
			.startEnumSelector(Text.translatable("chunkswap.config.mode"), Mode.class, cfg.mode)
			.setDefaultValue(Mode.CHUNK)
			.setEnumNameProvider(value -> Text.translatable("chunkswap.mode." + value.name().toLowerCase(Locale.ROOT)))
			.setTooltip(Text.translatable("chunkswap.config.mode.tooltip"))
			.setSaveConsumer(value -> cfg.mode = value)
			.build());

		general.addEntry(entries
			.startIntSlider(Text.translatable("chunkswap.config.radius"), cfg.radius, 1, 32)
			.setDefaultValue(8)
			.setTooltip(Text.translatable("chunkswap.config.radius.tooltip"))
			.setSaveConsumer(value -> cfg.radius = value)
			.build());

		general.addEntry(entries
			.startIntSlider(Text.translatable("chunkswap.config.thickness"), cfg.layerThickness, 1, 32)
			.setDefaultValue(1)
			.setTooltip(Text.translatable("chunkswap.config.thickness.tooltip"))
			.setSaveConsumer(value -> cfg.layerThickness = value)
			.build());

		general.addEntry(entries
			.startBooleanToggle(Text.translatable("chunkswap.config.random"), cfg.randomBlock)
			.setDefaultValue(false)
			.setTooltip(Text.translatable("chunkswap.config.random.tooltip"))
			.setSaveConsumer(value -> cfg.randomBlock = value)
			.build());

		general.addEntry(entries
			.startIntSlider(Text.translatable("chunkswap.config.chance"), cfg.chance, 1, 100)
			.setDefaultValue(100)
			.setTooltip(Text.translatable("chunkswap.config.chance.tooltip"))
			.setSaveConsumer(value -> cfg.chance = value)
			.build());

		general.addEntry(entries
			.startIntSlider(Text.translatable("chunkswap.config.speed"), cfg.budgetMs, 1, 40)
			.setDefaultValue(6)
			.setTooltip(Text.translatable("chunkswap.config.speed.tooltip"))
			.setSaveConsumer(value -> cfg.budgetMs = value)
			.build());

		return builder.build();
	}
}
