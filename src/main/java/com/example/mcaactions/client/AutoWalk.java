package com.example.mcaactions.client;

import com.example.mcaactions.McaActions;
import com.example.mcaactions.net.ActionNetwork;
import com.example.mcaactions.net.SceneControlPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While the server says "walk to (x, z)", the player really walks there (normal movement, normal walk animation).
 * Pressing sneak cancels. When close enough the player just stands still and the server starts the scene.
 */
@Mod.EventBusSubscriber(modid = McaActions.MOD_ID, value = Dist.CLIENT)
public final class AutoWalk {
    private static boolean active;
    private static double tx, tz;

    private AutoWalk() {}

    public static void start(double x, double z) {
        tx = x;
        tz = z;
        active = true;
    }

    public static void stop() {
        active = false;
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        if (!active) return;
        var in = e.getInput();
        if (in.shiftKeyDown) { // cancel
            active = false;
            ActionNetwork.CHANNEL.sendToServer(new SceneControlPacket(0));
            return;
        }
        Player p = e.getEntity();
        double dx = tx - p.getX(), dz = tz - p.getZ();
        in.leftImpulse = 0;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        if (Math.hypot(dx, dz) < 0.12) {
            in.forwardImpulse = 0;
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
        p.setYRot(Mth.approachDegrees(p.getYRot(), yaw, 35f));
        p.yHeadRot = p.getYRot();
        in.forwardImpulse = 1f;
        in.up = true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        active = false;
    }
}
