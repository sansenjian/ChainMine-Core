package com.chainmine.mixin;

import com.chainmine.core.platform.VersionCompat;
import com.chainmine.core.config.ChainMineConfig;
import com.chainmine.network.ChainMineNetworking;
import com.chainmine.core.breaker.ChainBreaker;
import com.chainmine.core.scan.VeinScanner;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 挖掘拦截 Mixin —— 在方块被真正破坏时触发连锁。
 *
 * <p>为什么选择拦截 {@code tryBreakBlock} 而不是
 * {@code processBlockBreakingAction}：
 * <ul>
 *   <li>{@code processBlockBreakingAction} 每帧都会被调用（挖掘进度推进），
 *       且包含 START/STOP/ABORT 等多个动作，若在它上面触发连锁极易重复执行；</li>
 *   <li>{@code tryBreakBlock} 是"方块被完全破坏"的唯一出口（STOP 动作与进度满
 *       都会汇聚到这里），HEAD 预扫描 + RETURN 补破坏是最干净、零重复的注入点。</li>
 * </ul>
 *
 * <p>时序：HEAD 注入计算本次连锁候选（不破坏任何方块），RETURN 注入在
 * 原版破坏成功（{@code cir.getReturnValue() == true}）后执行补刀。
 * 补刀根据 {@code blocksPerTick} 配置二选一：
 * <ul>
 *   <li>{@code blocksPerTick > 0}：交给 {@link ChainBreaker} 分 tick 破坏
 *       （防服务端 spike，含工具保护）；</li>
 *   <li>{@code blocksPerTick == 0}：本 tick 立即全部破坏（兼容旧行为，仍含工具保护）。</li>
 * </ul>
 */
@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {

    @Shadow
    @Final
    private ServerPlayerEntity player;

    /** 本次连锁的候选方块（不含玩家直接挖掉的 origin） */
    private final List<BlockPos> chainMine$pending = new ArrayList<>();
    /** 本次连锁的目标方块类型（防御：防止挖掘间隙类型变化导致误连锁） */
    private Block chainMine$targetBlock = null;
    /** 本次连锁的源方块坐标（用于 HUD 定位） */
    private BlockPos chainMine$origin = null;

    /**
     * HEAD：方块破坏前计算连锁候选列表。
     * 触发条件：
     * <ul>
     *   <li>潜行（若配置要求）；</li>
     *   <li>主手为空（若 {@code allowEmptyHand}）或工具在白名单；</li>
     *   <li><b>挖掘等级硬门槛（始终生效）</b>：目标方块需要特定工具等级才掉落
     *       （如钻石矿 needs_iron_tool）时，空手或等级不足的工具不触发连锁；
     *       无等级要求的方块（石头/泥土）空手、任意工具均可连锁；</li>
     *   <li>可选严格模式 {@code requireCorrectTool}：要求工具类型合适（默认关闭）。</li>
     * </ul>
     */
    @Inject(method = "tryBreakBlock", at = @At("HEAD"))
    private void chainmine$prepareChain(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        chainMine$pending.clear();
        chainMine$targetBlock = null;
        chainMine$origin = null;

        ChainMineConfig config = ChainMineConfig.get();
        if (config.requireSneak && !player.isSneaking()) {
            return;
        }
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.isEmpty()) {
            // 空手：仅当配置允许空手触发时放行
            if (!config.allowEmptyHand) {
                return;
            }
        } else if (!config.isToolAllowed(mainHand.getItem())) {
            // 手持工具：必须在白名单内
            return;
        }

        // 版本感知：getServerWorld() 在 1.21.2+ 移除；VersionCompat 兼容 getWorld/getEntityWorld
        ServerWorld world = VersionCompat.getServerWorld(player);
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        // 挖掘等级硬门槛：玩家当前工具（含空手）无法收获该方块掉落时
        // （如钻石矿需铁镐+、煤矿需任意工具），不触发连锁；
        // 无工具需求方块（石头/泥土/沙砾）空手、任意工具均可连锁
        if (!player.canHarvest(state)) {
            return;
        }
        // 可选严格模式：要求工具类型合适（默认关闭）
        if (config.requireCorrectTool && !mainHand.isSuitableFor(state)) {
            return;
        }

        Block target = state.getBlock();
        // 按配置选择扫描模式：CUBE=规则立方体（默认，挖石头形状整齐）；CONNECTED=六向连通 BFS
        List<BlockPos> vein;
        if ("CONNECTED".equalsIgnoreCase(config.scanMode)) {
            vein = VeinScanner.bfsScan(world, pos, target, config.maxBlocks);
        } else {
            vein = VeinScanner.cubeScan(world, pos, target, config.scanSize, config.maxBlocks);
        }
        if (vein.size() > 1) {
            chainMine$pending.addAll(vein.subList(1, vein.size()));
            chainMine$targetBlock = target;
            chainMine$origin = pos;
        }
    }

    /**
     * RETURN：原版破坏成功后执行连锁补刀。
     */
    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void chainmine$executeChain(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (chainMine$pending.isEmpty() || chainMine$targetBlock == null || chainMine$origin == null) {
            return;
        }
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            // 原版破坏失败（例如方块在挖掘间隙被替换），放弃本次连锁
            chainMine$pending.clear();
            chainMine$targetBlock = null;
            chainMine$origin = null;
            return;
        }

        ChainMineConfig config = ChainMineConfig.get();
        if (config.blocksPerTick > 0) {
            // 分 tick 破坏（默认路径）：防服务端 spike，含工具保护
            ChainBreaker.startChain(player, chainMine$origin, chainMine$targetBlock,
                    new ArrayList<>(chainMine$pending), config.blocksPerTick);
        } else {
            // 单 tick 立即破坏（兼容旧行为，仍含工具保护）
            breakAllImmediately(config);
        }
        chainMine$pending.clear();
        chainMine$targetBlock = null;
        chainMine$origin = null;
    }

    /** 单 tick 全部破坏：类型校验 → 黑名单校验 → 工具保护 → breakBlock → postMine。 */
    private void breakAllImmediately(ChainMineConfig config) {
        // 版本感知：getServerWorld() 在 1.21.2+ 移除；VersionCompat 兼容 getWorld/getEntityWorld
        ServerWorld world = VersionCompat.getServerWorld(player);
        int broken = 0;
        for (BlockPos targetPos : chainMine$pending) {
            BlockState state = world.getBlockState(targetPos);
            // 防御：方块类型在挖掘间隙发生变化（其他玩家/红石/掉落物）则跳过
            if (state.getBlock() != chainMine$targetBlock) {
                continue;
            }
            if (config.isBlockBlacklisted(state.getBlock())) {
                continue;
            }
            ItemStack stack = player.getMainHandStack();
            // 工具保护：剩余耐久 ≤ 1 时停止，不磨爆工具
            if (config.durabilityProtection && !stack.isEmpty() && stack.isDamageable()
                    && stack.getDamage() >= stack.getMaxDamage() - 1) {
                break;
            }
            if (world.breakBlock(targetPos, true, player)) {
                broken++;
                if (!stack.isEmpty()) {
                    // 正常消耗耐久；postMine 内部按 Unbreaking 附魔判定是否免耗
                    // yarn 签名：postMine(World, BlockState, BlockPos, PlayerEntity)
                    stack.postMine(world, state, targetPos, player);
                }
            }
        }
        if (broken > 0) {
            ChainMineNetworking.sendChainCount(player, broken, chainMine$origin);
        }
    }
}
