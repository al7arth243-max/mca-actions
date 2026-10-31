package com.mcaactions.client;

import com.mcaactions.McaActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * Bendy humanoid with elbows, knees and a flexible spine, wearing the entity's own 64x64 skin.
 * MCA builds a single stitched skin (skin + face + clothes + hair) for each villager and hands it out
 * through its renderer's getTextureLocation, so the villager keeps its exact look.
 */
public class ActionPuppetModel extends GeoModel<ActionPuppet> {
    private static final ResourceLocation WIDE = new ResourceLocation(McaActions.MODID, "geo/humanoid_wide.geo.json");
    private static final ResourceLocation SLIM = new ResourceLocation(McaActions.MODID, "geo/humanoid_slim.geo.json");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(McaActions.MODID, "animations/actions.animation.json");

    /** The entity currently being drawn (rendering is single-threaded). */
    static LivingEntity current;

    @Override
    public ResourceLocation getModelResource(ActionPuppet animatable) {
        if (current instanceof AbstractClientPlayer player && "slim".equals(player.getModelName())) {
            return SLIM;
        }
        return WIDE;
    }

    @Override
    public ResourceLocation getTextureResource(ActionPuppet animatable) {
        return skinOf(current);
    }

    @Override
    public ResourceLocation getAnimationResource(ActionPuppet animatable) {
        return ANIMATIONS;
    }

    static ResourceLocation skinOf(LivingEntity entity) {
        if (entity == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }
        EntityRenderer<? super LivingEntity> normal = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        ResourceLocation texture = normal == null ? null : normal.getTextureLocation(entity);
        return texture != null ? texture : DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }
}
