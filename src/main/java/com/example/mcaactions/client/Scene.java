package com.example.mcaactions.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A two-role keyframe scene loaded from assets/mca_actions/scenes/<id>.json.
 *
 * Every role has 8 tracks of [x,y,z] keys. All tracks loop over "length" seconds and are
 * interpolated with a cyclic cardinal-Hermite curve (smooth velocity, no stop-start).
 *   root_position  px (1/16 block) in the scene frame: +z = towards the feet of the bed, y up, bed top = 0
 *   root_rotation  degrees [pitch, roll, yaw]: yaw about the vertical, pitch lays the body down (-90 = on its
 *                  back, head towards -z), roll turns the body about its own long axis (90 = onto its side)
 *   head/body/right_arm/left_arm/right_leg/left_leg  degrees [xRot, yRot, zRot] exactly like vanilla model parts
 */
public final class Scene {
    private static final String[] TRACKS = {"root_position", "root_rotation", "head", "body",
            "right_arm", "left_arm", "right_leg", "left_leg"};

    private record Key(float t, float x, float y, float z) {
        float c(int i) { return i == 0 ? x : i == 1 ? y : z; }
    }

    public final float length, intro, outro, camYaw, camPitch;
    /** Seconds of the one-shot "getting into position" sequence (0 = none). Played in reverse when leaving. */
    public final float enterLength;
    public final float[] swapArc = new float[3];
    private final Key[][][] roles;      // [role][track][key], looping
    private final Key[][][] enterRoles; // same layout, plays once; may be null

    private Scene(float length, float intro, float outro, float enterLength, float camYaw, float camPitch,
                  float[] arc, Key[][][] roles, Key[][][] enterRoles) {
        this.length = length;
        this.intro = Math.max(0.05f, intro);
        this.outro = Math.max(0.1f, outro);
        this.enterLength = enterRoles == null ? 0f : enterLength;
        this.enterRoles = enterRoles;
        this.camYaw = camYaw;
        this.camPitch = camPitch;
        System.arraycopy(arc, 0, swapArc, 0, 3);
        this.roles = roles;
    }

    public static Scene parse(JsonObject o) {
        float length = o.get("length").getAsFloat();
        float intro = o.has("intro") ? o.get("intro").getAsFloat() : 1f;
        JsonObject cam = o.has("camera") ? o.getAsJsonObject("camera") : new JsonObject();
        float camYaw = cam.has("yaw") ? cam.get("yaw").getAsFloat() : 0f;
        float camPitch = cam.has("pitch") ? cam.get("pitch").getAsFloat() : 20f;
        float[] arc = new float[3];
        if (o.has("swap_arc")) {
            JsonArray a = o.getAsJsonArray("swap_arc");
            for (int i = 0; i < 3; i++) arc[i] = a.get(i).getAsFloat();
        }
        Key[][][] roles = parseRoles(o.getAsJsonArray("roles"));
        float outro = o.has("outro") ? o.get("outro").getAsFloat() : 1.2f;
        Key[][][] enter = null;
        float enterLength = 0f;
        if (o.has("enter")) {
            JsonObject eo = o.getAsJsonObject("enter");
            enterLength = eo.get("length").getAsFloat();
            enter = parseRoles(eo.getAsJsonArray("roles"));
        }
        return new Scene(length, intro, outro, enterLength, camYaw, camPitch, arc, roles, enter);
    }

    private static Key[][][] parseRoles(JsonArray ra) {
        Key[][][] roles = new Key[ra.size()][TRACKS.length][];
        for (int r = 0; r < ra.size(); r++) {
            JsonObject ro = ra.get(r).getAsJsonObject();
            for (int ti = 0; ti < TRACKS.length; ti++) {
                List<Key> keys = new ArrayList<>();
                if (ro.has(TRACKS[ti])) {
                    for (Map.Entry<String, com.google.gson.JsonElement> k : ro.getAsJsonObject(TRACKS[ti]).entrySet()) {
                        JsonArray v = k.getValue().getAsJsonArray();
                        keys.add(new Key(Float.parseFloat(k.getKey()),
                                v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()));
                    }
                }
                keys.sort(Comparator.comparingDouble(Key::t));
                roles[r][ti] = keys.toArray(new Key[0]);
            }
        }
        return roles;
    }


    /** 24 floats: root pos(3), root rot(3), then head, body, right_arm, left_arm, right_leg, left_leg (3 each). */
    public float[] pose(int role, float t) {
        Key[][] r = roles[Math.min(role, roles.length - 1)];
        float[] out = new float[TRACKS.length * 3];
        for (int ti = 0; ti < TRACKS.length; ti++) sample(r[ti], t, length, out, ti * 3);
        return out;
    }

    /** One-shot sequence (not looping), clamped to its first/last pose. Same layout as pose(). */
    public float[] enterPose(int role, float t) {
        Key[][] r = enterRoles[Math.min(role, enterRoles.length - 1)];
        float[] out = new float[TRACKS.length * 3];
        for (int ti = 0; ti < TRACKS.length; ti++) sampleClamped(r[ti], t, out, ti * 3);
        return out;
    }

    private static void sampleClamped(Key[] k, float t, float[] out, int o) {
        int n = k.length;
        if (n == 0) return;
        if (n == 1 || t <= k[0].t || t >= k[n - 1].t) {
            Key e = (n == 1 || t <= k[0].t) ? k[0] : k[n - 1];
            out[o] = e.x;
            out[o + 1] = e.y;
            out[o + 2] = e.z;
            return;
        }
        int i = 0;
        for (int j = 0; j < n - 1; j++) {
            if (k[j].t <= t) i = j;
        }
        int i0 = Math.max(i - 1, 0), i1 = i + 1, i2 = Math.min(i + 2, n - 1);
        float ta = k[i0].t, tb = k[i].t, tc = k[i1].t, td = k[i2].t;
        float dt = tc - tb, u = (t - tb) / dt, u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        for (int c = 0; c < 3; c++) {
            float a = k[i0].c(c), b = k[i].c(c), cc = k[i1].c(c), d = k[i2].c(c);
            float mb = i0 == i ? 0f : (cc - a) / (tc - ta);
            float mc = i2 == i1 ? 0f : (d - b) / (td - tb);
            out[o + c] = h00 * b + h10 * dt * mb + h01 * cc + h11 * dt * mc;
        }
    }

    private static void sample(Key[] k, float t, float len, float[] out, int o) {
        int n = k.length;
        if (n == 0) return;
        if (n == 1) {
            out[o] = k[0].x;
            out[o + 1] = k[0].y;
            out[o + 2] = k[0].z;
            return;
        }
        t = ((t % len) + len) % len;
        int i = n - 1;
        for (int j = 0; j < n; j++) {
            if (k[j].t <= t) i = j;
            else break;
        }
        if (i == n - 1 && t < k[0].t) t += len;
        int i0 = (i - 1 + n) % n, i1 = (i + 1) % n, i2 = (i + 2) % n;
        float tb = k[i].t;
        float ta = k[i0].t - (i0 > i ? len : 0);
        float tc = k[i1].t + (i1 <= i ? len : 0);
        float td = k[i2].t + (i2 <= i ? len : 0);
        float dt = tc - tb, u = (t - tb) / dt, u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        for (int c = 0; c < 3; c++) {
            float a = k[i0].c(c), b = k[i].c(c), cc = k[i1].c(c), d = k[i2].c(c);
            float mb = (cc - a) / (tc - ta), mc = (d - b) / (td - tb);
            out[o + c] = h00 * b + h10 * dt * mb + h01 * cc + h11 * dt * mc;
        }
    }
}
