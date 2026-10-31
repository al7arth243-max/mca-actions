package com.mcaactions.client;

import com.mcaactions.McaActions;
import com.mcaactions.network.ActionEndPacket;
import com.mcaactions.network.ActionStatePacket;
import com.mcaactions.network.Network;
import com.mcaactions.network.StopActionPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side state: which entities are currently playing an action animation, plus locking the local player
 * in place, switching to third person while it plays, and swapping in the GeckoLib renderer.
 */
@Mod.EventBusSubscriber(modid = McaActions.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientActions {

    public static final class Role {
        public final RawAnimation animation;
        public final float yaw;
        public boolean fresh = true;

        Role(String animationName, float yaw, boolean loop) {
            this.animation = loop ? RawAnimation.begin().thenLoop(animationName) : RawAnimation.begin().thenPlayAndHold(animationName);
            this.yaw = yaw;
        }
    }

    private static final Map<Integer, Role> ROLES = new HashMap<>();

    // local player's own action
    private static Vec3 anchor;
    private static CameraType previousCamera;
    private static boolean stopRequested;

    private static ActionPuppetRenderer renderer;

    private ClientActions() {}

    public static Role role(int entityId) {
        return ROLES.get(entityId);
    }

    static void setRenderer(ActionPuppetRenderer r) {
        renderer = r;
    }

    // ------------------------------------------------------------------ packets

    public static void onStart(ActionStatePacket p) {
        boolean loop = p.playerAnim.contains("cuddle");
        ROLES.put(p.playerId, new Role(p.playerAnim, p.playerYaw, loop));
        ROLES.put(p.villagerId, new Role(p.villagerAnim, p.villagerYaw, loop));

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == p.playerId) {
            anchor = new Vec3(p.anchorX, p.anchorY, p.anchorZ);
            stopRequested = false;
            if (previousCamera == null) {
                previousCamera = mc.options.getCameraType();
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (p.showStop) {
                mc.setScreen(new StopScreen());
            }
        }
    }

    public static void onEnd(ActionEndPacket p) {
        ROLES.remove(p.playerId);
        ROLES.remove(p.villagerId);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == p.playerId) {
            clearLocal(mc);
        }
    }

    private static void clearLocal(Minecraft mc) {
        anchor = null;
        stopRequested = false;
        if (previousCamera != null) {
            mc.options.setCameraType(previousCamera);
            previousCamera = null;
        }
        if (mc.screen instanceof StopScreen) {
            mc.setScreen(null);
        }
    }

    static void requestStop() {
        if (!stopRequested) {
            stopRequested = true;
            Network.CHANNEL.sendToServer(new StopActionPacket());
        }
    }

    // ------------------------------------------------------------------ events

    /** Keep the local player still while an action plays; Shift asks to stop. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (anchor == null) return;
        Input input = event.getInput();
        if (input.shiftKeyDown) {
            requestStop();
        }
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            if (!ROLES.isEmpty() || anchor != null) {
                ROLES.clear();
                clearLocal(mc);
            }
            return;
        }
        if (anchor != null) {
            player.setDeltaMovement(Vec3.ZERO);
            if (player.position().distanceToSqr(anchor) > 1.0E-4) {
                player.setPos(anchor.x, anchor.y, anchor.z);
                player.setOldPosAndRot();
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ROLES.clear();
            anchor = null;
            previousCamera = null;
        }
    }

    /** Draw action participants with the bendy GeckoLib model instead of their normal renderer. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Role role = ROLES.get(entity.getId());
        if (role == null || renderer == null) return;

        event.setCanceled(true);
        renderer.renderFor(entity, role, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }
}
