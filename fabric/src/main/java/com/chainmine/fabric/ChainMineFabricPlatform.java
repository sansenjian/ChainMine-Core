package com.chainmine.fabric;

import com.chainmine.core.breaker.ChainBreaker;
import com.chainmine.core.platform.ChainMinePlatform;
import com.chainmine.network.ChainMineNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;

/** Fabric 平台实现 —— 把 common 的抽象能力接到 Fabric API。 */
public final class ChainMineFabricPlatform implements ChainMinePlatform {

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public void registerServerTick(Runnable tickHandler) {
        ServerTickEvents.END_SERVER_TICK.register(server -> tickHandler.run());
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        ChainBreaker.clear();
    }

    @Override
    public void sendChainCount(ServerPlayerEntity player, int count, BlockPos origin) {
        ChainMineNetworking.sendChainCount(player, count, origin);
    }
}
