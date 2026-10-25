package com.example.mcaactions.client;

import com.example.mcaactions.net.SceneSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/** Which entities are in a scene, and the maths that turns that into a pose for a given moment. */
public final class ClientSceneState {
    private static final float SWITCH_SECONDS = 1.0f;

    public static final class State {
        String scene;
        int role, prevRole;
        float sceneYaw;

        public float sceneYaw() { return sceneYaw; }
        float sx, sy, sz, startYaw;
        float startTick;
        float switchTick = -1_000_000f;
        boolean leaving;
        float leaveNow;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private ClientSceneState() {}

    public static State get(int entityId) {
        return STATES.isEmpty() ? null : STATES.get(entityId);
    }

    public static void remove(int entityId) {
        STATES.remove(entityId);
    }

    public static void clear() {
        STATES.clear();
    }

    public static void handle(SceneSyncPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        int tc = e != null ? e.tickCount : 0;
        boolean local = mc.player != null && mc.player.getId() == p.entityId();

        switch (p.kind()) {
            case 0 -> {
                State st = new State();
                st.scene = p.scene();
                st.role = st.prevRole = p.role();
                st.sceneYaw = p.sceneYaw();
                st.sx = p.sx();
                st.sy = p.sy();
                st.sz = p.sz();
                st.startYaw = p.startYaw();
                st.startTick = tc - p.elapsed();
                STATES.put(p.entityId(), st);
                if (local) SceneControls.begin(SceneLibrary.INSTANCE.get(p.scene()), p.sceneYaw());
            }
            case 1 -> {
                State st = STATES.get(p.entityId());
                if (st == null) return;
                st.prevRole = st.role;
                st.role = p.role();
                st.switchTick = tc;
            }
            case 2 -> {
                State st = STATES.get(p.entityId());
                if (st == null) return;
                st.leaving = true;
                st.leaveNow = tc;
            }
            case 3 -> {
                STATES.remove(p.entityId());
                if (local) SceneControls.end();
            }
            default -> {}
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static float[] lerp(float[] a, float[] b, float k) {
        float[] o = new float[a.length];
        for (int i = 0; i < a.length; i++) o[i] = a[i] + (b[i] - a[i]) * k;
        o[5] = a[5] + Mth.wrapDegrees(b[5] - a[5]) * k; // root yaw: shortest way round
        return o;
    }

    private static float[] standing(State st) {
        float[] s = new float[24];
        s[0] = st.sx;
        s[1] = st.sy;
        s[2] = st.sz;
        s[5] = st.startYaw;
        return s;
    }

    /** True once the getting-into-position sequence is over, so Leave / Switch can be used. */
    public static boolean controlsReady(int entityId, float now) {
        State st = get(entityId);
        if (st == null || st.leaving) return false;
        Scene sc = SceneLibrary.INSTANCE.get(st.scene);
        return sc != null && (now - st.startTick) / 20f >= sc.enterLength;
    }

    /**
     * Pose for this entity at `now` (entity tickCount + partial tick), or null if unknown.
     * Layout: see Scene.pose. Position is in 1/16 blocks relative to the scene pivot.
     * Timeline: [enter sequence once] -> [looping scene]; Switch blends between roles; Leave plays the enter
     * sequence backwards (or, for scenes without one, just relaxes the limbs).
     */
    public static float[] evaluate(State st, float now) {
        Scene sc = SceneLibrary.INSTANCE.get(st.scene);
        if (sc == null) return null;

        float nowEff = st.leaving ? st.leaveNow : now;
        float t = (nowEff - st.startTick) / 20f;
        float el = sc.enterLength;
        boolean entering = t < el;
        float tl = Math.max(0f, t - el);
        float[] p = entering ? sc.enterPose(st.role, t) : sc.pose(st.role, tl);

        float ks = smooth((nowEff - st.switchTick) / 20f / SWITCH_SECONDS);
        if (ks < 1f && !entering) { // switching places: blend from the old role, arcing so they don't clip through
            p = lerp(sc.pose(st.prevRole, tl), p, ks);
            float arc = (float) Math.sin(Math.PI * ks), sign = st.role == 0 ? 1f : -1f;
            p[0] += sc.swapArc[0] * arc * sign;
            p[1] += sc.swapArc[1] * arc;
            p[2] += sc.swapArc[2] * arc * sign;
        }

        float ki = smooth(t / sc.intro); // absorbs the last bit of walking error at the start
        if (ki < 1f) p = lerp(standing(st), p, ki);

        if (st.leaving) {
            float ko = Mth.clamp((now - st.leaveNow) / 20f / sc.outro, 0f, 1f);
            float[] target;
            float w;
            if (el > 0f) { // climb back out: the enter sequence in reverse
                target = sc.enterPose(st.role, el * (1f - smooth(ko)));
                w = smooth(ko / 0.2f);
            } else {       // just relax: arms down, body upright
                target = p.clone();
                for (int i = 6; i < 24; i++) target[i] = 0f;
                target[3] = 0f;
                target[4] = 0f;
                w = smooth(ko);
            }
            p = lerp(p, target, w);
        }
        return p;
    }
}
