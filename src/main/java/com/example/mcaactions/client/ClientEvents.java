package com.example.mcaactions.client;

import com.example.mcaactions.ActionType;
import com.example.mcaactions.McaActions;
import com.example.mcaactions.net.ActionNetwork;
import forge.net.mca.entity.VillagerEntityMCA;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import import forge.net.mca.client.gui.AbstractDynamicScreen;
import forge.net.mca.client.gui.Button;
import forge.net.mca.client.gui.InteractScreen;
import forge.net.mca.client.gui.MCAScreens;
import forge.net.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/**
 * Client glue:
 *  1. Appends an "Actions" button to MCA's Interact page (MCA's pages are data driven, but a mod can't
 *     edit another mod's JSON without replacing it, so we add the button to MCA's loaded layout instead).
 *  2. Handles clicks on that button and on the buttons of our own page (assets/mca/api/gui/actions.json).
 */
@Mod.EventBusSubscriber(modid = McaActions.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String PREFIX = "gui.button.mca_actions.";
    private static final String OPEN = PREFIX + "open";

    private static Method getApiButton;

    private ClientEvents() {}

    // ---------- GUI ----------

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onScreenInit(ScreenEvent.Init.Pre e) {
        if (!(e.getScreen() instanceof InteractScreen)) return;
        try {
            Field f = MCAScreens.class.getDeclaredField("buttons");
            f.setAccessible(true);
            Map<String, Button[]> map = (Map<String, Button[]>) f.get(MCAScreens.getInstance());
            Button[] cur = map.get("interact");
            if (cur == null) return;
            for (Button b : cur) if (OPEN.equals(b.identifier())) return; // already added

            Field rowField = Button.class.getDeclaredField("row");
            rowField.setAccessible(true);
            int row = 0;
            for (Button b : cur) row = Math.max(row, rowField.getInt(b));
            row++;

            Button mine = GSON.fromJson("{\"identifier\":\"" + OPEN + "\",\"x\":-90,\"y\":10,\"row\":" + row
                    + ",\"width\":80,\"height\":20,\"notifyServer\":false,\"targetServer\":false,\"constraints\":\"\"}",
                    Button.class);
            Button[] next = Arrays.copyOf(cur, cur.length + 1);
            next[cur.length] = mine;
            map.put("interact", next);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.warn("MCA Actions: could not add the Actions button to MCA's Interact page", ex);
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre e) {
        if (e.getButton() != 0 || !(e.getScreen() instanceof AbstractDynamicScreen screen)) return;
        String id = hoveredButtonId(screen, e.getMouseX(), e.getMouseY());
        if (id == null) return;

        if (OPEN.equals(id)) {
            click();
            screen.setLayout("actions");
            e.setCanceled(true);
        } else if (id.startsWith(PREFIX) && screen instanceof InteractScreen) {
            String action = id.substring(PREFIX.length());
            if (ActionType.byId(action) == null) return;
            int entityId = villagerId(screen);
            if (entityId < 0) return;
            click();
            ActionNetwork.CHANNEL.sendToServer(new PerformActionPacket(entityId, action));
            screen.onClose();
            e.setCanceled(true);
        } else if ("gui.button.backarrow".equals(id) && "actions".equals(screen.getActiveScreen())) {
            click();
            screen.setLayout("interact");
            e.setCanceled(true);
        }
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static String hoveredButtonId(AbstractDynamicScreen screen, double mx, double my) {
        try {
            for (GuiEventListener l : screen.children()) {
                if (l instanceof AbstractWidget w && w.visible && w.active && w.isMouseOver(mx, my)) {
                    if (getApiButton == null) {
                        getApiButton = w.getClass().getDeclaredMethod("getApiButton");
                        getApiButton.setAccessible(true);
                    }
                    if (getApiButton.getDeclaringClass().isInstance(w)) {
                        return ((Button) getApiButton.invoke(w)).identifier();
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.debug("MCA Actions: button lookup failed", ex);
        }
        return null;
    }

    private static int villagerId(AbstractDynamicScreen screen) {
        try {
            Field f = InteractScreen.class.getDeclaredField("villager");
            f.setAccessible(true);
            return ((VillagerLike<?>) f.get(screen)).asEntity().getId();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.warn("MCA Actions: could not read the villager from InteractScreen", ex);
            return -1;
        }
    }

    // ---------- Rendering ----------

    /**
     * Places a scene participant. The renderer rotates by the entity's own body yaw right after this event,
     * so we wrap our transform in +yaw/-yaw to make it act in the scene frame instead.
     * world = R(-sceneYaw) * L * F, where L = translate(pos) * Ry(yaw) * Rx(pitch) * Ry(roll).
     */
    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        ClientSceneState.State st = ClientSceneState.get(le.getId());
        if (st == null) return;
        float pt = e.getPartialTick();
        float[] p = ClientSceneState.evaluate(st, le.tickCount + pt);
        if (p == null) return;

        float bodyYaw = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
        PoseStack ps = e.getPoseStack();
        ps.mulPose(Axis.YP.rotationDegrees(-st.sceneYaw()));
        ps.translate(p[0] / 16f, p[1] / 16f, p[2] / 16f);
        ps.mulPose(Axis.YP.rotationDegrees(p[5]));
        ps.mulPose(Axis.XP.rotationDegrees(p[3]));
        ps.mulPose(Axis.YP.rotationDegrees(p[4]));
        ps.mulPose(Axis.YP.rotationDegrees(bodyYaw));
    }

    // ---------- Cleanup ----------

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide) ClientSceneState.remove(e.getEntity().getId());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientSceneState.clear();
        SceneControls.end();
    }
}
