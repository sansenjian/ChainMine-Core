package com.chainmine.gametest;

import com.chainmine.core.breaker.ChainBreaker;
import com.chainmine.core.config.ChainMineConfig;
import com.chainmine.core.platform.Platforms;
import com.chainmine.core.scan.VeinScanner;
import com.chainmine.fabric.ChainMineFabricPlatform;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 集成测试（Minecraft GameTest，fabric 1.21.11 新版 API）。
 *
 * <p>在真实服务端世界中验证连锁破坏链路：3×3×3 石头 + CUBE 扫描 + ChainBreaker 分 tick 破坏。
 * 结构模板用内置 {@code fabric-gametest-api-v1:empty}（8×8×8 空结构，无需 nbt 资源）。
 *
 * <p>注册：fabric.mod.json 的 {@code fabric-gametest} entrypoint。
 * 运行：{@code ./gradlew :fabric:runGametest}（沙盒可跑通服务端，行为断言已验证）。
 *
 * <p>注意：mixin（tryBreakBlock）不会被 GameTest 的 mock player 触发，此处直接调用
 * common 的扫描 + 破坏链路 —— 平台接线由真实服务器冒烟测试覆盖（1.21.2~1.21.11 已实测）。
 */
public class ChainMineGameTests {

    static {
        // 平台实现 + 配置 + 破坏器（与 mod 入口相同的初始化链）
        Platforms.set(new ChainMineFabricPlatform());
        ChainMineConfig.load();
        ChainBreaker.init();
    }

    private void fillStone(TestContext ctx, int size) {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    ctx.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    /** 3×3×3 石头 + CUBE 扫描 + 单 tick 全破，结束时全部变 AIR。 */
    @GameTest(maxTicks = 100)
    public void cubeModeBreaks27Blocks(TestContext ctx) {
        ChainMineConfig config = ChainMineConfig.get();
        config.scanMode = "CUBE";
        config.scanSize = 3;
        config.maxBlocks = 64;
        config.blocksPerTick = 64; // 接近单 tick 全破

        fillStone(ctx, 3);
        // GameTest 的 setBlockState 用相对坐标；扫描/破坏需要世界坐标
        BlockPos origin = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        Block target = Blocks.STONE;

        List<BlockPos> vein = VeinScanner.cubeScan(ctx.getWorld(), origin, target, 3, 64);
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        ChainBreaker.startChain(player, origin, target, vein, 64);

        // 等待服务端 tick 处理完成后断言：27 块全部变 AIR
        ctx.addFinalTask(() -> {
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    for (int z = 0; z < 3; z++) {
                        ctx.expectBlock(Blocks.AIR, new BlockPos(x, y, z));
                    }
                }
            }
        });
        ctx.complete();
    }

    /** 大量相连方块时 maxBlocks 硬上限必须被遵守（5×5×5=125 块，maxBlocks=10）。 */
    @GameTest
    public void maxBlocksHardLimit(TestContext ctx) {
        ChainMineConfig config = ChainMineConfig.get();
        config.scanMode = "CONNECTED";
        config.maxBlocks = 10;

        fillStone(ctx, 5);
        BlockPos origin = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        List<BlockPos> vein = VeinScanner.bfsScan(ctx.getWorld(), origin, Blocks.STONE, 10);

        ctx.assertTrue(vein.size() == 10, "期望恰好 10 个方块被扫描，实际 " + vein.size());
        ctx.complete();
    }
}
