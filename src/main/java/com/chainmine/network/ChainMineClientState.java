package com.chainmine.network;

import net.minecraft.util.math.BlockPos;

/**
 * 客户端 HUD 状态（供渲染线程读取）。
 *
 * <p>更新发生在渲染线程（{@code ClientPlayNetworking} 接收回调中通过
 * {@code client.execute} 封送），因此渲染时直接读取无需加锁。
 */
public final class ChainMineClientState {

    /** 最近一次连锁的方块数（0 = 无显示） */
    public static int lastChainCount = 0;
    /** 最近一次连锁的发生时间（毫秒时间戳） */
    public static long lastChainTimeMs = 0;
    /** 最近一次连锁的触发点 */
    public static BlockPos lastChainOrigin = null;
    /** HUD 显示时长（毫秒） */
    public static final long DISPLAY_MS = 3000;

    private ChainMineClientState() {
    }

    public static void onChain(int count, BlockPos origin) {
        lastChainCount = count;
        lastChainOrigin = origin;
        lastChainTimeMs = System.currentTimeMillis();
    }
}
