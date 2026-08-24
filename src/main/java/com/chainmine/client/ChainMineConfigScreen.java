package com.chainmine.client;

import com.chainmine.config.ChainMineConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 配置说明屏幕（Mod Menu "Config" 按钮打开）。
 *
 * <p>ChainMine 采用手写 JSON 配置（无需 Cloth Config 依赖），因此本屏幕
 * 仅展示当前配置值与编辑指引，真正的修改通过编辑
 * {@code config/chainmine.json} + 执行 {@code /chainmine reload} 完成。
 */
public class ChainMineConfigScreen extends Screen {

    private static final int PANEL_LEFT = 24;
    private static final int PANEL_RIGHT = 24;

    private final Screen parent;

    public ChainMineConfigScreen(Screen parent) {
        super(Text.translatable("screen.chainmine.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnWidth = 120;
        int btnHeight = 20;
        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.chainmine.done"),
                        button -> this.close())
                .dimensions(this.width / 2 - btnWidth / 2, this.height - 32, btnWidth, btnHeight)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int panelWidth = this.width - PANEL_LEFT - PANEL_RIGHT;
        int y = 32;
        int x = PANEL_LEFT + 8;
        int textWidth = panelWidth - 16;

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.chainmine.title"), this.width / 2, 16, 0xFFFFFFFF);
        y = 44;

        ChainMineConfig cfg = ChainMineConfig.get();
        String[] lines = {
                Text.translatable("screen.chainmine.sneak", cfg.requireSneak).getString(),
                Text.translatable("screen.chainmine.maxblocks", cfg.maxBlocks).getString(),
                Text.translatable("screen.chainmine.tools", String.join(", ", cfg.toolWhitelist)).getString(),
                "",
                Text.translatable("screen.chainmine.hint1").getString(),
                Text.translatable("screen.chainmine.hint2").getString(),
        };
        for (String line : lines) {
            context.drawText(this.textRenderer, Text.literal(line), x, y, 0xFFCCCCCC, false);
            y += 14;
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
