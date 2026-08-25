package com.chainmine.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 跨加载器共享常量。
 *
 * <p>{@code MOD_ID} 在 Fabric 与 NeoForge 下保持一致，
 * 确保配置路径、物品 ID、命令前缀在两种加载器上完全相同。
 */
public final class ChainMineConstants {

    public static final String MOD_ID = "chainmine-core";

    /** 跨模块统一日志器（避免依赖各加载器主类的 Logger）。 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ChainMineConstants() {
    }
}
