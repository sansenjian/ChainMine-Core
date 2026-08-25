package com.chainmine.core.scan;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * 方块状态读取接口 —— 将"如何读世界"与 BFS 逻辑解耦，便于单元测试。
 * （从原 VeinScanner 内部接口拆出，common 与测试共用）
 */
@FunctionalInterface
public interface BlockProvider {

    BlockState getState(BlockPos pos);
}
