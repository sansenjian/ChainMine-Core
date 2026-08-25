package com.chainmine.core.scan;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VeinScanner BFS 单元测试 —— 使用内存 Map 虚拟世界（无需启动 Minecraft）。
 *
 * <p>覆盖：单方块、线性、六向（含 Y 轴）、maxBlocks 上限、类型严格匹配、
 * BFS 访问顺序、非法参数防御、CUBE 规则立方体扫描。
 */
class VeinScannerTest {

    /**
     * 关键：Minecraft 注册表必须在任何 Minecraft 类（Blocks/SoundEvents 等）静态初始化
     * 之前完成 bootstrap，否则访问注册表会抛 "Not bootstrapped"。
     * 写法：
     *   1) SharedConstants.createGameVersion() —— 设置游戏版本（Bootstrap 依赖它）
     *   2) Bootstrap.initialize() —— 初始化注册表
     * 而 yarn 映射下 SimpleRegistry/RegistryEntry 跨包导致的 IllegalAccessError，
     * 由 fabric-loader-junit 依赖（应用 access widener）解决。
     * Java 保证 static 块按声明顺序运行，因此置于所有静态字段之前。
     */
    static {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    private static final BlockState STONE = Blocks.STONE.getDefaultState();
    private static final BlockState DIRT = Blocks.DIRT.getDefaultState();
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    /** 把内存 Map 包装成 BlockProvider（缺省 = 空气）。 */
    private static BlockProvider provider(Map<BlockPos, BlockState> map) {
        return pos -> map.getOrDefault(pos, AIR);
    }

    private static List<BlockPos> scan(Map<BlockPos, BlockState> world, BlockPos origin, Block target, int max) {
        return VeinScanner.bfsScan(provider(world), null, origin, target, max);
    }

    @Test
    void singleBlock_noChain() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos origin = new BlockPos(0, 64, 0);
        world.put(origin, STONE);
        assertEquals(List.of(origin), scan(world, origin, Blocks.STONE, 64));
    }

    @Test
    void linearChain_threeBlocks() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(1, 64, 0);
        BlockPos c = new BlockPos(2, 64, 0);
        world.put(a, STONE);
        world.put(b, STONE);
        world.put(c, STONE);
        // 从 a 出发：a -> b -> c（BFS 顺序）
        assertEquals(List.of(a, b, c), scan(world, a, Blocks.STONE, 64));
    }

    @Test
    void sixDirection_includesVertical() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos origin = new BlockPos(5, 64, 5);
        BlockPos up = new BlockPos(5, 65, 5);
        BlockPos down = new BlockPos(5, 63, 5);
        BlockPos east = new BlockPos(6, 64, 5);
        BlockPos west = new BlockPos(4, 64, 5);
        BlockPos south = new BlockPos(5, 64, 6);
        BlockPos north = new BlockPos(5, 64, 4);
        world.put(origin, STONE);
        for (BlockPos p : List.of(up, down, east, west, south, north)) {
            world.put(p, STONE);
        }
        List<BlockPos> result = scan(world, origin, Blocks.STONE, 64);
        assertEquals(7, result.size());
        assertTrue(result.containsAll(List.of(origin, up, down, east, west, south, north)));
    }

    @Test
    void maxBlocks_capsExpansion() {
        // 一条 10 块长的直线，上限 4 → 只返回 4 个（含 origin）
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos origin = new BlockPos(0, 64, 0);
        for (int i = 0; i < 10; i++) {
            world.put(new BlockPos(i, 64, 0), STONE);
        }
        List<BlockPos> result = scan(world, origin, Blocks.STONE, 4);
        assertEquals(4, result.size());
    }

    @Test
    void differentBlockType_notChained() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos stone1 = new BlockPos(0, 64, 0);
        BlockPos dirt = new BlockPos(1, 64, 0);
        BlockPos stone2 = new BlockPos(2, 64, 0);
        world.put(stone1, STONE);
        world.put(dirt, DIRT);
        world.put(stone2, STONE);
        // 石头-泥土-石头：中间被泥土隔断，两侧石头不连通
        assertEquals(List.of(stone1), scan(world, stone1, Blocks.STONE, 64));
    }

    @Test
    void bfsOrder_correct() {
        // origin 在 (1,64,0)，邻居 (0/2,64,0) 与 (1,64,±1)，它们各自的次级邻居 (2,64,1)
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos o = new BlockPos(1, 64, 0);
        BlockPos n1 = new BlockPos(0, 64, 0);
        BlockPos n2 = new BlockPos(2, 64, 0);
        BlockPos n3 = new BlockPos(1, 64, 1);
        BlockPos n4 = new BlockPos(1, 64, -1);
        BlockPos deep = new BlockPos(2, 64, 1); // n2 的邻居，BFS 第二层
        world.put(o, STONE);
        for (BlockPos p : List.of(n1, n2, n3, n4, deep)) {
            world.put(p, STONE);
        }
        List<BlockPos> result = scan(world, o, Blocks.STONE, 64);
        // BFS 顺序：第一层 = 所有邻居（按六向枚举顺序），第二层 = deep
        assertEquals(6, result.size());
        assertEquals(o, result.get(0));
        assertTrue(result.subList(1, 5).containsAll(List.of(n1, n2, n3, n4)));
        assertEquals(deep, result.get(5));
    }

    @Test
    void maxBlocksNonPositive_returnsEmpty() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        world.put(new BlockPos(0, 64, 0), STONE);
        assertTrue(scan(world, new BlockPos(0, 64, 0), Blocks.STONE, 0).isEmpty());
    }

    @Test
    void airTarget_returnsEmpty() {
        Map<BlockPos, BlockState> world = new HashMap<>();
        world.put(new BlockPos(0, 64, 0), STONE);
        assertTrue(scan(world, new BlockPos(0, 64, 0), Blocks.AIR, 64).isEmpty());
    }

    @Test
    void diagonal_isNotConnected() {
        // 对角线相邻（x+1,z+1）不属于六向连通，不应被连锁
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos diag = new BlockPos(1, 64, 1);
        world.put(origin, STONE);
        world.put(diag, STONE);
        assertEquals(List.of(origin), scan(world, origin, Blocks.STONE, 64));
    }

    // ------------------------------------------------------------------
    // 规则立方体扫描（scanMode=CUBE）
    // ------------------------------------------------------------------

    @Test
    void cube3x3_fullCube() {
        // 3×3×3 全石头（含 origin）= 27 块
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos origin = new BlockPos(0, 64, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.put(origin.add(dx, dy, dz), STONE);
                }
            }
        }
        List<BlockPos> result = VeinScanner.cubeScan(provider(world), null, origin, Blocks.STONE, 3, 64);
        assertEquals(27, result.size());
        assertTrue(result.contains(origin));
        // 字典序 y→x→z：origin 位于 dy=0 层（第 2 层），第 10~18 个
        assertEquals(origin, result.get(13));
    }

    @Test
    void cube3x3_doesNotIncludeOutside() {
        // 立方体（半径1）之外的石头不应被扫描
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos inside = new BlockPos(1, 64, 1);       // 对角也在半径1内（3×3×3 包含对角）
        BlockPos outside = new BlockPos(2, 64, 0);      // 距离 2，超出半径 1
        BlockPos aboveOutside = new BlockPos(0, 66, 0); // Y 超出
        world.put(origin, STONE);
        world.put(inside, STONE);
        world.put(outside, STONE);
        world.put(aboveOutside, STONE);
        List<BlockPos> result = VeinScanner.cubeScan(provider(world), null, origin, Blocks.STONE, 3, 64);
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of(origin, inside)));
    }

    @Test
    void cube3x3_onlySameType() {
        // 立方体内的不同类型方块不连锁
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos dirtInside = new BlockPos(1, 64, 0);
        world.put(origin, STONE);
        world.put(dirtInside, DIRT);
        List<BlockPos> result = VeinScanner.cubeScan(provider(world), null, origin, Blocks.STONE, 3, 64);
        assertEquals(List.of(origin), result);
    }

    @Test
    void cube5x5_maxBlocksCaps() {
        // 5×5×5 = 125 块，maxBlocks=64 截断
        Map<BlockPos, BlockState> world = new HashMap<>();
        BlockPos origin = new BlockPos(0, 64, 0);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.put(origin.add(dx, dy, dz), STONE);
                }
            }
        }
        List<BlockPos> result = VeinScanner.cubeScan(provider(world), null, origin, Blocks.STONE, 5, 64);
        assertEquals(64, result.size());
    }
}
