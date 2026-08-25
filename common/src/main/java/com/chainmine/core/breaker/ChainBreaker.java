package com.chainmine.core.breaker;

import com.chainmine.core.config.ChainMineConfig;
import com.chainmine.core.platform.ChainMinePlatform;
import com.chainmine.core.platform.Platforms;
import com.chainmine.core.platform.VersionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 连锁破坏执行器（参考 SgtVeinminer / Viper Vein Miner 的成熟设计）。
 * （跨加载器共享核心：tick 注册与网络回调通过 {@link ChainMinePlatform} 抽象，
 * 不依赖任何加载器 API）
 *
 * <p>解决的问题：
 * <ul>
 *   <li><b>单 tick 全破导致服务端 spike</b>：把一次连锁的破坏动作按
 *       {@code blocksPerTick} 分散到多个游戏 tick 执行，挖 64 块石头不再瞬间压垮 TPS；</li>
 *   <li><b>工具保护</b>（{@code durabilityProtection}）：连锁途中工具剩余耐久降到 1 时
 *       立即截断剩余方块，绝不把镐磨爆 —— 对应 Viper 的
 *       "stops before the tool would break"；</li>
 *   <li><b>玩家断开/移除安全</b>：玩家登出或切世界时丢弃未完成的连锁，不残留。</li>
 * </ul>
 *
 * <p>线程模型：所有操作（加入任务、tick 处理）都发生在服务端主线程，
 * 因此内部列表无需加锁。
 */
public final class ChainBreaker {

    private static final List<ActiveChain> ACTIVE = new ArrayList<>();
    private static boolean registered = false;

    private ChainBreaker() {
    }

    /** 进行中的连锁任务（不可变引用 + 游标推进）。 */
    private static final class ActiveChain {
        final ServerPlayerEntity player;
        final BlockPos origin;
        final Block target;
        final List<BlockPos> blocks;
        final int perTick;
        int cursor = 0;
        int broken = 0;

        ActiveChain(ServerPlayerEntity player, BlockPos origin, Block target,
                    List<BlockPos> blocks, int perTick) {
            this.player = player;
            this.origin = origin;
            this.target = target;
            this.blocks = blocks;
            this.perTick = perTick;
        }
    }

    /**
     * 注册 tick 处理器（幂等，加载器入口调用一次）。
     * 服务器停止清理由平台 {@link ChainMinePlatform#onServerStopped} 触发 {@link #clear()}。
     */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        ChainMinePlatform platform = Platforms.get();
        platform.registerServerTick(ChainBreaker::tick);
    }

    /** 服务器停止时清空全部未完成连锁（由平台生命周期回调调用）。 */
    public static void clear() {
        ACTIVE.clear();
    }

    /** 把一个连锁任务加入执行队列（由各加载器的挖掘拦截器调用）。 */
    public static void startChain(ServerPlayerEntity player, BlockPos origin, Block target,
                                  List<BlockPos> blocks, int perTick) {
        if (blocks.isEmpty()) {
            return;
        }
        ACTIVE.add(new ActiveChain(player, origin, target, blocks, Math.max(1, perTick)));
    }

    /** 每 tick 推进所有进行中的连锁（由平台注册的 tick 回调驱动）。 */
    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        ChainMineConfig config = ChainMineConfig.get();
        Iterator<ActiveChain> it = ACTIVE.iterator();
        while (it.hasNext()) {
            ActiveChain chain = it.next();

            // 玩家已登出/被移除：丢弃任务
            if (chain.player.isRemoved() || chain.player.networkHandler == null
                    || !chain.player.networkHandler.isConnectionOpen()) {
                it.remove();
                continue;
            }

            // 版本感知：getServerWorld() 在 1.21.2+ 移除，改用 VersionCompat 反射兼容
            //（1.21.9+ 走 getEntityWorld，1.21.2~1.21.8 走 getWorld）
            ServerWorld world = VersionCompat.getServerWorld(chain.player);
            int done = 0;
            while (chain.cursor < chain.blocks.size() && done < chain.perTick) {
                BlockPos pos = chain.blocks.get(chain.cursor++);
                BlockState state = world.getBlockState(pos);
                // 防御：类型在排队期间被改变（红石/其他玩家）则跳过
                if (state.getBlock() != chain.target) {
                    continue;
                }
                if (config.isBlockBlacklisted(state.getBlock())) {
                    continue;
                }

                ItemStack stack = chain.player.getMainHandStack();
                // 工具保护：剩余耐久 ≤ 1 时立即截断剩余连锁
                if (config.durabilityProtection && !stack.isEmpty() && stack.isDamageable()
                        && stack.getDamage() >= stack.getMaxDamage() - 1) {
                    chain.cursor = chain.blocks.size();
                    break;
                }

                if (world.breakBlock(pos, true, chain.player)) {
                    chain.broken++;
                    done++;
                    if (!stack.isEmpty()) {
                        // 正常消耗耐久；postMine 内部按 Unbreaking 附魔判定是否免耗
                        // yarn 签名：postMine(World, BlockState, BlockPos, PlayerEntity)
                        stack.postMine(world, state, pos, chain.player);
                    }
                }
            }

            if (chain.cursor >= chain.blocks.size()) {
                if (chain.broken > 0) {
                    Platforms.get().sendChainCount(chain.player, chain.broken, chain.origin);
                }
                it.remove();
            }
        }
    }
}
