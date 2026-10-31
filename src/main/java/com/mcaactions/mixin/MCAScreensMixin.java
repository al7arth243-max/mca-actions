package com.mcaactions.mixin;

import forge.net.conczin.mca.client.gui.Button;
import forge.net.conczin.mca.client.gui.MCAScreens;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * MCA builds its menus from assets/mca/api/gui/*.json. This appends an "Actions" button below the
 * existing buttons of the main villager menu, without replacing MCA's own file.
 */
@Mixin(value = MCAScreens.class, remap = false)
public abstract class MCAScreensMixin {
    @Unique
    private static final Map<Button[], Button[]> mcaactions$patched = new WeakHashMap<>();

    @Inject(method = "getScreen", at = @At("RETURN"), cancellable = true)
    private void mcaactions$addActionsButton(String guiKey, CallbackInfoReturnable<Optional<Button[]>> cir) {
        if (!"main".equals(guiKey)) return;
        Optional<Button[]> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;

        Button[] patched = mcaactions$patched.computeIfAbsent(result.get(), original -> {
            int nextY = 10;
            for (Button b : original) {
                if (b.identifier().equals("gui.button.mcaactions.actions")) return original;
                nextY = Math.max(nextY, b.y() + 21);
            }
            Button[] copy = Arrays.copyOf(original, original.length + 1);
            copy[original.length] = new Button("gui.button.mcaactions.actions", null,
                    -90, 0, nextY, 0, 80, 20, false, false, "", false, false);
            return copy;
        });
        cir.setReturnValue(Optional.of(patched));
    }
}
