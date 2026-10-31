package com.mcaactions.mixin;

import com.mcaactions.network.Network;
import com.mcaactions.network.StartActionPacket;
import forge.net.conczin.mca.client.gui.AbstractDynamicScreen;
import forge.net.conczin.mca.client.gui.Button;
import forge.net.conczin.mca.client.gui.InteractScreen;
import forge.net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles our buttons in MCA's villager menu:
 *  - "Actions" opens the actions page (assets/mcaactions/api/gui/mcaactions_actions.json)
 *  - each action button asks the server to start that action, then closes the menu
 * MCA's own back arrow already returns from our page to the main page.
 */
@Mixin(value = InteractScreen.class, remap = false)
public abstract class InteractScreenMixin {
    @Shadow
    @Final
    private VillagerLike<?> villager;

    @Inject(method = "buttonPressed", at = @At("HEAD"), cancellable = true)
    private void mcaactions$onButton(Button button, CallbackInfo ci) {
        String id = button.identifier();
        if ("gui.button.mcaactions.actions".equals(id)) {
            ((AbstractDynamicScreen) (Object) this).setLayout("mcaactions_actions");
            ci.cancel();
        } else if (id.startsWith("gui.button.mcaactions.do.")) {
            String action = id.substring("gui.button.mcaactions.do.".length());
            Network.CHANNEL.sendToServer(new StartActionPacket(villager.asEntity().getId(), action));
            ((Screen) (Object) this).onClose();
            ci.cancel();
        }
    }
}
