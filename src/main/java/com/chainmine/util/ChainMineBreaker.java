package com.chainmine.util;

import com.chainmine.config.ChainMineConfig;
import com.chainmine.network.ChainMineNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 连锁破坏执行器（参考 SgtVeinminer / Viper Vein Miner 的成熟设计）。
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
 * <p>线程模型：所有操作（加入任务、tick 处理）都发生在服务端主线程
 * （{@code ServerTickEvents.END_SERVER_TICK} 与 {@code tryBreakBlock} 注入点均如此），
 * 因此内部列表无需加锁。
 */
public final class ChainMineBreaker {

    private static final List<ActiveChain> ACTIVE = new ArrayList<>();
    private static boolean registered = false;

    private ChainMineBreaker() {
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

    /** 注册 tick 处理器（幂等，主类 onInitialize 调用一次）。 */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(ChainMineBreaker::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    /** 把一个连锁任务加入执行队列（由 Mixin 的 RETURN 注入调用）。 */
    public static void startChain(ServerPlayerEntity player, BlockPos origin, Block target,
                                  List<BlockPos> blocks, int perTick) {
        if (blocks.isEmpty()) {
            return;
        }
        ACTIVE.add(new ActiveChain(player, origin, target, blocks, Math.max(1, perTick)));
    }

    private static void tick(MinecraftServer server) {
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

            // 1.21.2+：getServerWorld() 移除，改 getEntityWorld()（服务端 tick 场景安全强转）
            ServerWorld world = (ServerWorld) chain.player.getEntityWorld();
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
                    ChainMineNetworking.sendChainCount(chain.player, chain.broken, chain.origin);
                }
                it.remove();
            }
        }
    }
}
