package com.mcaactions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import forge.net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

public class ActionPuppetRenderer extends GeoReplacedEntityRenderer<LivingEntity, ActionPuppet> {
    private float actionYaw;

    public ActionPuppetRenderer(EntityRendererProvider.Context context) {
        super(context, new ActionPuppetModel(), ActionPuppet.INSTANCE);
        this.shadowRadius = 0.5F;
    }

    public void renderFor(LivingEntity entity, ClientActions.Role role, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        this.actionYaw = role.yaw;
        ActionPuppetModel.current = entity;
        poseStack.pushPose();
        if (entity instanceof VillagerLike<?> villager) {
            // MCA villagers vary in size (genetics, age)
            float w = villager.getRawHorizontalScaleFactor();
            float h = villager.getRawVerticalScaleFactor();
            poseStack.scale(w, h, w);
        }
        try {
            render(entity, role.yaw, partialTick, poseStack, buffers, light);
        } finally {
            poseStack.popPose();
            ActionPuppetModel.current = null;
        }
    }

    /** Face the direction the action chose (towards the partner, or along the bed), ignoring the entity's own look. */
    @Override
    protected void applyRotations(ActionPuppet animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - this.actionYaw));
    }

    @Override
    public ResourceLocation getTextureLocation(ActionPuppet animatable) {
        return ActionPuppetModel.skinOf(this.currentEntity);
    }
}
