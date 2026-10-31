package com.mcaactions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown while cuddling: a single "Stop" button where MCA's menu buttons were.
 * Closing it with Escape keeps cuddling; Shift still gets you up.
 */
public class StopScreen extends Screen {
    public StopScreen() {
        super(Component.translatable("gui.mcaactions.stop_title"));
    }

    @Override
    protected void init() {
        // Same spot and size as MCA's first menu button (top-right, x -90, y 10, 80 x 20)
        addRenderableWidget(Button.builder(Component.translatable("gui.button.mcaactions.stop"), b -> {
            ClientActions.requestStop();
            onClose();
        }).bounds(this.width - 90, 10, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // no dark background, so the cuddle stays visible
        super.render(graphics, mouseX, mouseY, partialTick);
        Component hint = Component.translatable("gui.mcaactions.stop_hint");
        graphics.drawString(this.font, hint, this.width - 10 - this.font.width(hint), 34, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
