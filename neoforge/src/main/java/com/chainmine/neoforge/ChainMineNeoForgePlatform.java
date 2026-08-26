package com.chainmine.neoforge;

import com.chainmine.core.breaker.ChainBreaker;
import com.chainmine.core.platform.ChainMinePlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;

/** NeoForge 平台实现 —— 把 common 的抽象能力接到 NeoForge API。 */
@EventBusSubscriber(modid = ChainMineNeoForge.MOD_ID)
public final class ChainMineNeoForgePlatform implements ChainMinePlatform {

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void registerServerTick(Runnable tickHandler) {
        // NeoForge 21 的服务器 tick 事件（Post 阶段，服务端主线程）
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tickHandler.run());
    }

    /** 服务器停止清理（由事件总线订阅触发）。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ChainBreaker.clear();
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        ChainBreaker.clear();
    }

    @Override
    public void sendChainCount(ServerPlayer player, int count, BlockPos origin) {
        // TODO(本地验证): NeoForge 侧 HUD 计数网络包（fabric 版用 payload S2C）。
        // 当前先用系统消息兜底；后续可接 NeoForge 的 PlayPayloadTypeRegistry。
        if (count > 1) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "[ChainMine] 连锁破坏 " + count + " 个方块"));
        }
    }
}
