package com.chainmine.neoforge;

import com.chainmine.core.breaker.ChainBreaker;
import com.chainmine.core.config.ChainMineConfig;
import com.chainmine.core.platform.Platforms;
import com.chainmine.core.scan.VeinScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.server.command.ConfigCommand;

import java.util.List;

/**
 * NeoForge 平台入口 —— 与 Fabric 版共享 common 核心（VeinScanner / ChainMineConfig / ChainBreaker）。
 *
 * <p>挖掘拦截走 NeoForge 的 {@link BlockEvent.BreakEvent}（方块破坏前触发，可取消），
 * 替代 Fabric 版的 Mixin tryBreakBlock —— 不需要触碰中间层，是更干净的接入点。
 */
@Mod(ChainMineNeoForge.MOD_ID)
@EventBusSubscriber(modid = ChainMineNeoForge.MOD_ID)
public final class ChainMineNeoForge {

    public static final String MOD_ID = "chainmine-core";

    public ChainMineNeoForge() {
        // 注册平台实现（common 依赖它获取配置目录/事件/网络）
        Platforms.set(new ChainMineNeoForgePlatform());
        ChainMineConfig.load();
        ChainBreaker.init();
    }

    /** 挖掘拦截：玩家破坏方块时触发连锁扫描（与 Fabric 的 tryBreakBlock 注入等价）。 */
    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ChainMineConfig config = ChainMineConfig.get();
        // 潜行触发
        if (config.requireSneak && !player.isShiftKeyDown()) {
            return;
        }
        // 空手模式：不允许空手触发时直接放行
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() && !config.allowEmptyHand) {
            return;
        }
        // 工具白名单
        if (!stack.isEmpty() && !config.isToolAllowed(stack.getItem())) {
            return;
        }

        BlockPos pos = event.getPos();
        Block target = event.getState().getBlock();
        // 黑名单
        if (config.isBlockBlacklisted(target)) {
            return;
        }
        if (target == net.minecraft.world.level.block.Blocks.AIR) {
            return;
        }
        if (!(player.level() instanceof ServerLevel world)) {
            return;
        }
        // 挖掘等级门槛：主块必须可采集（连锁块由 ChainBreaker 内 breakBlock 校验）
        if (!player.hasCorrectToolForDrops(event.getState())) {
            return;
        }

        // 扫描（CUBE 规则形状 / CONNECTED 连通 BFS）
        List<BlockPos> vein;
        if ("CONNECTED".equalsIgnoreCase(config.scanMode)) {
            vein = VeinScanner.bfsScan(world, pos, target, config.maxBlocks);
        } else {
            vein = VeinScanner.cubeScan(world, pos, target, config.scanSize, config.maxBlocks);
        }

        // 交给分 tick 破坏器（主块由原版流程破坏；连锁块后续 tick 内 destroyBlock）
        ChainBreaker.startChain(player, pos, target, vein, config.blocksPerTick);
    }

    /** 命令注册：/chainmine reload|status（OP 等级 2）。 */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // TODO(本地验证): mojmap 1.21.11 权限 API 若为 Permission 系统，改 hasPermission 写法
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal(MOD_ID)
                        .requires(source -> source.hasPermission(2))
                        .then(net.minecraft.commands.Commands.literal("reload")
                                .executes(ctx -> {
                                    ChainMineConfig.reload();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[ChainMine] Config reloaded."), true);
                                    return 1;
                                }))
                        .then(net.minecraft.commands.Commands.literal("status")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[ChainMine] "
                                                    + ChainMineConfig.get().describe()), false);
                                    return 1;
                                })));
    }
}
