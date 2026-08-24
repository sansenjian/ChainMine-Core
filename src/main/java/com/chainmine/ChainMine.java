package com.chainmine;

import com.chainmine.config.ChainMineConfig;
import com.chainmine.network.ChainMineNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChainMine 主入口。
 *
 * <p>职责：
 * <ul>
 *   <li>注册 mod 标志性物品（红宝石，Phase 1 环境验证用）</li>
 *   <li>注册 /chainmine reload|status 命令（Phase 4，OP 权限）</li>
 *   <li>注册网络 Payload 类型（Phase 5，双端共需）</li>
 * </ul>
 */
public class ChainMine implements ModInitializer {
    public static final String MOD_ID = "chainmine-core";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 测试/标志物品：红宝石。可用 /give @s chainmine-core:ruby 获取 */
    public static final Item RUBY = new Item(new Item.Settings());

    @Override
    public void onInitialize() {
        // Phase 1: 注册测试物品，验证注册表与资源加载链路
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "ruby"), RUBY);

        // Phase 4: 配置加载 + 热重载命令
        ChainMineConfig.load();
        registerCommands();

        // 性能优化: 分 tick 连锁破坏执行器（服务端主线程 tick 处理）
        com.chainmine.util.ChainMineBreaker.init();

        // Phase 5: 网络 Payload 类型（playS2C 需双端注册）
        ChainMineNetworking.registerCommon();

        LOGGER.info("[ChainMine] initialized. Ruby registered: {}", RUBY);
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal(MOD_ID)
                        // OP 权限（权限等级 2）才可执行
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("reload")
                                .executes(ctx -> {
                                    ChainMineConfig.reload();
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal("[ChainMine] Config reloaded."), true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("status")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "[ChainMine] " + ChainMineConfig.get().describe()), false);
                                    return 1;
                                }))
                ));
    }
}
