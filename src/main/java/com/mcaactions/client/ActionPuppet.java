package com.mcaactions.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * One shared GeckoLib animatable that stands in for any player or villager during an action.
 * GeckoLib keeps separate animation state per entity id, so many pairs can animate at once.
 */
public final class ActionPuppet implements GeoReplacedEntity {
    public static final ActionPuppet INSTANCE = new ActionPuppet();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private ActionPuppet() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 6-tick blend from standing into the pose (e.g. smoothly lying down)
        controllers.add(new AnimationController<>(this, "action", 6, this::predicate));
    }

    private PlayState predicate(AnimationState<ActionPuppet> state) {
        Entity entity = state.getData(DataTickets.ENTITY);
        ClientActions.Role role = entity == null ? null : ClientActions.role(entity.getId());
        if (role == null) {
            return PlayState.STOP;
        }
        if (role.fresh) {
            role.fresh = false;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(role.animation);
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return EntityType.PLAYER;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
