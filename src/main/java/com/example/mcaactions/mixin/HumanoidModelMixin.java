package com.example.mcaactions.mixin;

import com.example.mcaactions.client.ClientSceneState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after vanilla's humanoid animation (MCA's villager model calls it via super), so scene limbs win. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void mcaActions$applyScene(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                       float netHeadYaw, float headPitch, CallbackInfo ci) {
        ClientSceneState.State st = ClientSceneState.get(entity.getId());
        if (st == null) return;
        float[] p = ClientSceneState.evaluate(st, ageInTicks);
        if (p == null) return;
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        set(m.head, p, 6);
        set(m.body, p, 9);
        set(m.rightArm, p, 12);
        set(m.leftArm, p, 15);
        set(m.rightLeg, p, 18);
        set(m.leftLeg, p, 21);
        m.hat.copyFrom(m.head);
    }

    private static void set(ModelPart part, float[] p, int o) {
        part.xRot = (float) Math.toRadians(p[o]);
        part.yRot = (float) Math.toRadians(p[o + 1]);
        part.zRot = (float) Math.toRadians(p[o + 2]);
    }
}
