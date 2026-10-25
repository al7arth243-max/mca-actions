package com.example.mcaactions.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** Camera + on-screen buttons while the local player is in a scene. */
public final class SceneControls {
    private static CameraType previous;

    private SceneControls() {}

    public static void begin(Scene scene, float sceneYaw) {
        Minecraft mc = Minecraft.getInstance();
        if (previous == null) previous = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (mc.player != null && scene != null) {
            float yaw = sceneYaw + scene.camYaw;
            mc.player.setYRot(yaw);
            mc.player.setXRot(scene.camPitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = scene.camPitch;
            mc.player.yHeadRot = yaw;
        }
        mc.setScreen(new SceneControlsScreen());
    }

    public static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (previous != null) {
            mc.options.setCameraType(previous);
            previous = null;
        }
        if (mc.screen instanceof SceneControlsScreen) mc.setScreen(null);
    }
}
