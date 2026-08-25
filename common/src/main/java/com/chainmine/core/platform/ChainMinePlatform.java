package com.chainmine.core.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;

/**
 * 平台抽象层 —— common 代码通过本接口访问加载器相关的能力，
 * 由 Fabric / NeoForge 模块各自提供实现并注册到 {@link Platforms}。
 *
 * <p>设计原则：common 只依赖 Minecraft 类（yarn 映射）与本接口，
 * 不 import 任何加载器 API。这样同一份源码可被两个加载器共享。
 */
public interface ChainMinePlatform {

    /** 配置目录（Fabric 与 NeoForge 均为 config/）。 */
    Path getConfigDir();

    /** 注册服务端每 tick 回调（Fabric: ServerTickEvents；NeoForge: ServerTickEvent）。 */
    void registerServerTick(Runnable tickHandler);

    /** 服务器停止时的清理回调（丢弃未完成连锁任务）。 */
    void onServerStopped(MinecraftServer server);

    /** 向指定玩家发送连锁计数（Fabric: payload；NeoForge: 自定义网络包）。 */
    void sendChainCount(ServerPlayerEntity player, int count, BlockPos origin);
}
