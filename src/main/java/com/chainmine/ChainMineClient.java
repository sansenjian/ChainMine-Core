package com.chainmine;

import com.chainmine.network.ChainMineClientState;
import com.chainmine.network.ChainMineNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * ChainMine 客户端入口。
 *
 * <p>职责：
 * <ul>
 *   <li>注册 S2C 网络接收器（连锁计数）</li>
 *   <li>渲染 HUD 提示：连锁触发后 3 秒内显示 "ChainMine: +N blocks"</li>
 * </ul>
 *
 * <p>说明：连锁时的方块破坏粒子与低音量碎裂音效由服务端
 * {@code World#breakBlock} 广播的 2001 世界事件自动触发，无需在此处理。
 */
public class ChainMineClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ChainMineNetworking.registerClient();
        HudRenderCallback.EVENT.register(ChainMineClient::renderChainHud);
    }

    private static void renderChainHud(DrawContext context, RenderTickCounter tickCounter) {
        long now = System.currentTimeMillis();
        int count = ChainMineClientState.lastChainCount;
        if (count <= 0 || now - ChainMineClientState.lastChainTimeMs > ChainMineClientState.DISPLAY_MS) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || client.player == null) {
            return;
        }
        context.drawText(client.textRenderer,
                Text.translatable("hud.chainmine.count", count),
                8, 8, 0xFFFFFFFF, true);
    }
}
