package com.chainmine.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu 集成：在 Mod Menu 配置列表中显示 "Config" 按钮。
 */
public class ChainMineModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ChainMineConfigScreen::new;
    }
}
