package com.chainmine.core.scan;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 连锁矿脉扫描器 —— 基于迭代 BFS 的六向连通块搜索。
 * （跨加载器共享核心：Fabric 与 NeoForge 均消费本类，仅依赖 Minecraft 类）
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>迭代 + 显式队列</b>：绝不递归，杜绝深矿脉/深树导致的栈溢出；</li>
 *   <li><b>maxBlocks 硬上限</b>：队列出队数达到上限立即停止，防止连锁扩散拖垮服务端；</li>
 *   <li><b>严格 Block 类型匹配</b>：使用 {@code ==} 比较 Block 实例，矿石/原木/泥土各自成组，
 *       不会把状态不同的同类型方块混为一谈（例如锁链 vs 铁块）；</li>
 *   <li><b>可测试核心</b>：内部扫描逻辑只依赖 {@link BlockProvider} 函数式接口，
 *       单元测试可用内存 Map 构造虚拟世界，无需启动 Minecraft；</li>
 *   <li><b>越界安全</b>：Y 轴 0~255 之外与超出世界边界的坐标直接忽略。</li>
 * </ul>
 */
public final class VeinScanner {

    /** 六向扩散向量：上、下、东、西、南、北 */
    private static final Direction[] DIRECTIONS = {
            Direction.UP, Direction.DOWN,
            Direction.EAST, Direction.WEST,
            Direction.SOUTH, Direction.NORTH
    };

    private VeinScanner() {
    }

    /**
     * 以 origin 为中心做六向 BFS，收集所有与 targetBlock 严格同类型的连通方块。
     *
     * @param world      真实世界（服务端/客户端均可）
     * @param origin     被玩家直接挖掘的方块
     * @param targetBlock 要匹配的方块类型（== 严格比较）
     * @param maxBlocks  单次连锁的方块数量上限（含 origin），必须 &gt; 0
     * @return 按 BFS 访问顺序排列的方块坐标列表，始终以 origin 开头，最少包含 1 个元素
     */
    public static List<BlockPos> bfsScan(World world, BlockPos origin, Block targetBlock, int maxBlocks) {
        return bfsScan(world::getBlockState, world, origin, targetBlock, maxBlocks);
    }

    /**
     * 无 World 依赖的 BFS 核心 —— 测试与生产共用。
     */
    public static List<BlockPos> bfsScan(BlockProvider provider, World world,
                                         BlockPos origin, Block targetBlock, int maxBlocks) {
        List<BlockPos> result = new ArrayList<>();
        if (maxBlocks <= 0) {
            return result;
        }
        if (targetBlock == null || targetBlock == Blocks.AIR) {
            return result;
        }

        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = queue.poll();
            result.add(current);

            for (Direction dir : DIRECTIONS) {
                if (result.size() >= maxBlocks) {
                    break;
                }
                BlockPos neighbor = current.offset(dir);
                if (!visited.add(neighbor)) {
                    continue;
                }
                if (world != null && !world.isInBuildLimit(neighbor)) {
                    continue;
                }
                // 严格按 Block 类型匹配
                if (provider.getState(neighbor).getBlock() == targetBlock) {
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }

    /**
     * 规则立方体扫描 —— 以 origin 为中心，在 {@code size×size×size} 的立方体内
     * 收集所有与 targetBlock 严格同类型的方块（对应 FTB Ultimine 的 3x3x3 形状模式）。
     *
     * <p>与 BFS 连通扫描的区别：不关心方块是否互相连通，只看"是否落在规则区域内"。
     * 挖石头/挖山体时得到的是平整的规则形状，而不是沿矿脉扩散的不规则轮廓。
     *
     * <p>返回列表按 y → x → z 字典序填充，始终以 origin 开头（若 origin 是目标类型）。
     *
     * @param size 立方体边长（应为奇数：3、5、7…），实际半径 = size/2
     */
    public static List<BlockPos> cubeScan(World world, BlockPos origin, Block targetBlock,
                                          int size, int maxBlocks) {
        return cubeScan(world::getBlockState, world, origin, targetBlock, size, maxBlocks);
    }

    /** 无 World 依赖的立方体扫描核心 —— 测试与生产共用。 */
    public static List<BlockPos> cubeScan(BlockProvider provider, World world, BlockPos origin,
                                          Block targetBlock, int size, int maxBlocks) {
        List<BlockPos> result = new ArrayList<>();
        if (size < 1 || maxBlocks <= 0 || targetBlock == null || targetBlock == Blocks.AIR) {
            return result;
        }
        int radius = size / 2;
        for (int dy = -radius; dy <= radius && result.size() < maxBlocks; dy++) {
            for (int dx = -radius; dx <= radius && result.size() < maxBlocks; dx++) {
                for (int dz = -radius; dz <= radius && result.size() < maxBlocks; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (world != null && !world.isInBuildLimit(pos)) {
                        continue;
                    }
                    if (provider.getState(pos).getBlock() == targetBlock) {
                        result.add(pos);
                    }
                }
            }
        }
        return result;
    }
}
