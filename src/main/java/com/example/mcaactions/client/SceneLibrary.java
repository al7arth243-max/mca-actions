package com.example.mcaactions.client;

import com.example.mcaactions.McaActions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/** Loads assets/mca_actions/scenes/*.json on every resource reload (resource packs can override them). */
public final class SceneLibrary extends SimpleJsonResourceReloadListener {
    public static final SceneLibrary INSTANCE = new SceneLibrary();

    private Map<String, Scene> scenes = Map.of();

    private SceneLibrary() {
        super(new Gson(), "scenes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> in, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, Scene> out = new HashMap<>();
        in.forEach((id, json) -> {
            if (!id.getNamespace().equals(McaActions.MOD_ID)) return;
            try {
                out.put(id.getPath(), Scene.parse(json.getAsJsonObject()));
            } catch (RuntimeException ex) {
                LogUtils.getLogger().error("MCA Actions: bad scene file {}", id, ex);
            }
        });
        scenes = out;
    }

    public Scene get(String id) {
        return scenes.get(id);
    }
}
