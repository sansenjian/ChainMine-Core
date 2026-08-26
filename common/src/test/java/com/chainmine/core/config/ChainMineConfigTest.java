package com.chainmine.core.config;

import com.chainmine.core.platform.ChainMinePlatform;
import com.chainmine.core.platform.Platforms;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChainMineConfig 单元测试 —— 覆盖 JSON 解析、非法输入回退、数值上限 clamp。
 *
 * <p>与 VeinScannerTest 相同的 bootstrap 约束：Minecraft 注册表必须先初始化。
 */
class ChainMineConfigTest {

    static {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    private static Path configDir;

    @BeforeAll
    static void setup() throws Exception {
        configDir = Files.createTempDirectory("chainmine-config-test");
        Platforms.set(new ChainMinePlatform() {
            @Override public Path getConfigDir() { return configDir; }
            @Override public void registerServerTick(Runnable r) { }
            @Override public void onServerStopped(MinecraftServer s) { }
            @Override public void sendChainCount(ServerPlayerEntity p, int c, BlockPos o) { }
        });
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清掉测试生成的配置文件，保证每个用例从干净状态开始
        Files.deleteIfExists(configDir.resolve("chainmine-core.json"));
    }

    private Path configFile() {
        return configDir.resolve("chainmine-core.json");
    }

    @Test
    void load_createsDefaultConfig_whenMissing() {
        ChainMineConfig.load();
        assertTrue(Files.exists(configFile()), "缺少配置文件时应自动生成");
        ChainMineConfig cfg = ChainMineConfig.get();
        assertEquals(64, cfg.maxBlocks, "默认 maxBlocks 应为 64");
        assertTrue(cfg.requireSneak, "默认 requireSneak 应为 true");
        assertEquals("CUBE", cfg.scanMode);
    }

    @Test
    void reload_appliesValidJson() throws IOException {
        Files.writeString(configFile(), """
                {
                  "requireSneak": false,
                  "maxBlocks": 32,
                  "scanMode": "CONNECTED",
                  "scanSize": 5,
                  "allowEmptyHand": false,
                  "durabilityProtection": false
                }
                """);
        ChainMineConfig.reload();
        ChainMineConfig cfg = ChainMineConfig.get();
        assertFalse(cfg.requireSneak);
        assertEquals(32, cfg.maxBlocks);
        assertEquals("CONNECTED", cfg.scanMode);
        assertEquals(5, cfg.scanSize);
        assertFalse(cfg.allowEmptyHand);
        assertFalse(cfg.durabilityProtection);
    }

    @Test
    void reload_keepsCurrentValues_onInvalidJson() throws IOException {
        ChainMineConfig.load(); // 先有默认配置
        Files.writeString(configFile(), "{ not valid json !!! ");
        ChainMineConfig.reload();
        // 解析失败时应保留当前值而不是崩溃
        assertEquals(64, ChainMineConfig.get().maxBlocks);
    }

    @Test
    void reload_clampsMaxBlocksToLimit() throws IOException {
        Files.writeString(configFile(), "{\"maxBlocks\": 9999}");
        ChainMineConfig.reload();
        assertEquals(256, ChainMineConfig.get().maxBlocks, "maxBlocks 超上限应 clamp 到 256");
    }

    @Test
    void reload_clampsMaxBlocksToMinimum() throws IOException {
        Files.writeString(configFile(), "{\"maxBlocks\": 0}");
        ChainMineConfig.reload();
        assertEquals(1, ChainMineConfig.get().maxBlocks, "maxBlocks 低于下限应 clamp 到 1");
    }

    @Test
    void reload_handlesEmptyFile() throws IOException {
        ChainMineConfig.load();
        Files.writeString(configFile(), "");
        ChainMineConfig.reload();
        // 空文件应视为解析失败，保留当前值
        assertEquals(64, ChainMineConfig.get().maxBlocks);
    }
}
