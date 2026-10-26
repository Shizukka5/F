package com.chunkswap.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Кнопка настроек в Mod Menu. Работает, только если установлены Mod Menu и Cloth Config.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> FabricLoader.getInstance().isModLoaded("cloth-config")
			? ChunkSwapConfigScreen.create(parent)
			: parent;
	}
}
