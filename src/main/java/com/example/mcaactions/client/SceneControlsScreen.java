package com.example.mcaactions.client;

import com.example.mcaactions.net.ActionNetwork;
import com.example.mcaactions.net.SceneControlPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Transparent screen with "Leave" and "Switch" at the top. Esc also leaves. Doesn't pause the game. */
public class SceneControlsScreen extends net.minecraft.client.gui.screens.Screen {
    private Button leave, swap;
    private int swapCooldown, missing;
    private boolean leaving;

    protected SceneControlsScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        leave = addRenderableWidget(Button.builder(Component.translatable("mca_actions.leave"),
                b -> requestLeave()).bounds(cx - 84, 8, 80, 20).build());
        swap = addRenderableWidget(Button.builder(Component.translatable("mca_actions.switch"), b -> {
            ActionNetwork.CHANNEL.sendToServer(new SceneControlPacket(1));
            swapCooldown = 24;
        }).bounds(cx + 4, 8, 80, 20).build());
    }

    private void requestLeave() {
        ActionNetwork.CHANNEL.sendToServer(new SceneControlPacket(0));
        leaving = true;
        if (leave != null) leave.active = false;
        if (swap != null) swap.active = false;
    }

    @Override
    public void tick() {
        if (swapCooldown > 0) swapCooldown--;
        Minecraft mc = Minecraft.getInstance();
        // Buttons stay greyed out while the characters are still getting into position
        boolean ready = mc.player != null && ClientSceneState.controlsReady(mc.player.getId(), mc.player.tickCount);
        if (leave != null) leave.active = ready && !leaving;
        if (swap != null) swap.active = ready && !leaving && swapCooldown == 0;
        // Safety net: if the scene vanished without an END packet, don't leave the player stuck here.
        if (mc.player != null && ClientSceneState.get(mc.player.getId()) == null && ++missing > 20) {
            SceneControls.end();
        }
    }

    @Override
    public void onClose() {
        requestLeave(); // Esc = Leave
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
