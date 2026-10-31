package com.mcaactions.server;

import com.mcaactions.network.ActionEndPacket;
import com.mcaactions.network.ActionStatePacket;
import com.mcaactions.network.Network;
import forge.net.conczin.mca.entity.VillagerLike;
import forge.net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs actions on the server: who is doing what with whom, keeping both participants in place,
 * awarding MCA hearts, and cleaning up afterwards.
 */
public final class ActionManager {
    public static final String HUG = "hug";
    public static final String CUDDLE = "cuddle";

    // Animation names (assets/mcaactions/animations/actions.animation.json)
    private static final String ANIM_HUG_HIGH = "animation.mcaactions.hug_high";
    private static final String ANIM_HUG_LOW = "animation.mcaactions.hug_low";
    private static final String ANIM_HOLD_RIGHT = "animation.mcaactions.cuddle_hold_right";
    private static final String ANIM_HOLD_LEFT = "animation.mcaactions.cuddle_hold_left";
    private static final String ANIM_SNUGGLE_RIGHT = "animation.mcaactions.cuddle_snuggle_right";
    private static final String ANIM_SNUGGLE_LEFT = "animation.mcaactions.cuddle_snuggle_left";

    // Tuning
    private static final int HUG_TICKS = 60;                // matches the 3 s hug animation
    private static final int HUG_HEARTS = 4;
    private static final int HUG_COOLDOWN_TICKS = 20 * 60;  // hearts from hugging the same villager at most once a minute
    private static final int CUDDLE_HEART_INTERVAL = 20 * 10;
    private static final int CUDDLE_MAX_HEARTS = 10;        // per cuddle session
    private static final double HUG_DISTANCE = 0.4;         // blocks between the two bodies' centres
    private static final int BED_SEARCH_RADIUS = 8;
    private static final double MAX_START_DISTANCE_SQR = 6 * 6;

    private static final String TAG_FROZEN = "mcaactions_frozen";
    private static final String TAG_PREV_NO_AI = "mcaactions_prev_noai";

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, Long> HUG_COOLDOWNS = new HashMap<>();

    private ActionManager() {}

    private static final class Session {
        final ServerPlayer player;
        final Mob villager;
        final String type;
        final Vec3 villagerPos;
        final float villagerYaw;
        final Vec3 playerAnchor;
        BlockPos bedA;
        BlockPos bedB;
        int ticks;
        int heartsGiven;

        Session(ServerPlayer player, Mob villager, String type, Vec3 villagerPos, float villagerYaw, Vec3 playerAnchor) {
            this.player = player;
            this.villager = villager;
            this.type = type;
            this.villagerPos = villagerPos;
            this.villagerYaw = villagerYaw;
            this.playerAnchor = playerAnchor;
        }
    }

    private record BedPair(BlockPos a, BlockPos b, Direction facing) {}

    // ------------------------------------------------------------------ starting

    public static void start(ServerPlayer player, int villagerId, String action) {
        Entity entity = player.level().getEntity(villagerId);
        if (!(entity instanceof Mob villager) || !(entity instanceof VillagerLike<?> mca) || !villager.isAlive()) {
            return;
        }
        stop(player);

        if (isBusy(villager)) {
            say(player, "message.mcaactions.busy", villager.getName());
            return;
        }
        if (player.distanceToSqr(villager) > MAX_START_DISTANCE_SQR) {
            say(player, "message.mcaactions.too_far", villager.getName());
            return;
        }

        switch (action) {
            case HUG -> startHug(player, villager);
            case CUDDLE -> startCuddle(player, villager, mca);
            default -> { }
        }
    }

    private static void startHug(ServerPlayer player, Mob villager) {
        Vec3 pp = player.position();
        Vec3 dir = new Vec3(villager.getX() - pp.x, 0, villager.getZ() - pp.z);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = Vec3.directionFromRotation(0, player.getYRot());
            dir = new Vec3(dir.x, 0, dir.z);
        }
        dir = dir.normalize();

        float playerYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        float villagerYaw = playerYaw + 180.0F;
        Vec3 villagerPos = new Vec3(pp.x + dir.x * HUG_DISTANCE, pp.y, pp.z + dir.z * HUG_DISTANCE);

        prepareVillager(villager);
        Session s = new Session(player, villager, HUG, villagerPos, villagerYaw, pp);
        SESSIONS.put(player.getUUID(), s);
        pin(villager, villagerPos, villagerYaw);

        Network.sendAround(player, new ActionStatePacket(player.getId(), villager.getId(), ANIM_HUG_HIGH, ANIM_HUG_LOW,
                playerYaw, villagerYaw, pp.x, pp.y, pp.z, false));
    }

    private static void startCuddle(ServerPlayer player, Mob villager, VillagerLike<?> mca) {
        if (mca.getAgeState() != AgeState.ADULT) {
            say(player, "message.mcaactions.not_adult", villager.getName());
            return;
        }

        ServerLevel level = player.serverLevel();
        BedPair beds = findDoubleBed(level, player.blockPosition(), BED_SEARCH_RADIUS);
        if (beds == null) {
            say(player, "message.mcaactions.no_bed");
            return;
        }

        // The player takes whichever of the two beds is closer to them
        BlockPos playerBed = beds.a();
        BlockPos villagerBed = beds.b();
        if (player.blockPosition().distSqr(villagerBed) < player.blockPosition().distSqr(playerBed)) {
            playerBed = beds.b();
            villagerBed = beds.a();
        }

        Direction facing = beds.facing();                       // foot -> head (towards the pillow)
        double dx = facing.getStepX(), dz = facing.getStepZ();
        double lx = Integer.signum(villagerBed.getX() - playerBed.getX());   // unit vector: player bed -> villager bed
        double lz = Integer.signum(villagerBed.getZ() - playerBed.getZ());

        Vec3 cp = bedCenter(playerBed, facing);
        Vec3 cv = bedCenter(villagerBed, facing);
        // Shift both towards the shared edge; the villager lies a little lower so their head rests on the player's shoulder
        Vec3 playerPos = cp.add(-dx * 0.25 + lx * 0.25, 0, -dz * 0.25 + lz * 0.25);
        Vec3 villagerPos = cv.add(-dx * 0.4375 - lx * 0.3, 0, -dz * 0.4375 - lz * 0.3);
        float yaw = facing.getOpposite().toYRot();

        // Is the villager on the player's right-hand side (while lying on their back)?
        boolean partnerOnRight = lx * dz + lz * -dx > 0;
        String playerAnim = partnerOnRight ? ANIM_HOLD_RIGHT : ANIM_HOLD_LEFT;
        String villagerAnim = partnerOnRight ? ANIM_SNUGGLE_LEFT : ANIM_SNUGGLE_RIGHT;

        prepareVillager(villager);
        player.stopRiding();
        player.connection.teleport(playerPos.x, playerPos.y, playerPos.z, yaw, player.getXRot());

        Session s = new Session(player, villager, CUDDLE, villagerPos, yaw, playerPos);
        s.bedA = playerBed;
        s.bedB = villagerBed;
        SESSIONS.put(player.getUUID(), s);
        setOccupied(level, playerBed, true);
        setOccupied(level, villagerBed, true);
        pin(villager, villagerPos, yaw);

        Network.sendAround(player, new ActionStatePacket(player.getId(), villager.getId(), playerAnim, villagerAnim,
                yaw, yaw, playerPos.x, playerPos.y, playerPos.z, true));
        say(player, "message.mcaactions.cuddle.start", villager.getName());
    }

    // ------------------------------------------------------------------ stopping

    public static void stop(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) {
            end(s, false);
        }
    }

    private static void end(Session s, boolean finishedNaturally) {
        SESSIONS.remove(s.player.getUUID());
        unfreeze(s.villager);

        if (CUDDLE.equals(s.type) && s.villager.level() instanceof ServerLevel level) {
            setOccupied(level, s.bedA, false);
            setOccupied(level, s.bedB, false);
            if (!s.player.hasDisconnected()) {
                say(s.player, "message.mcaactions.cuddle.end", s.villager.getName());
            }
        }

        ActionEndPacket packet = new ActionEndPacket(s.player.getId(), s.villager.getId());
        Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> s.villager), packet);
        if (!s.player.hasDisconnected()) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> s.player), packet);
        }
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            tick(s);
        }
    }

    private static void tick(Session s) {
        ServerPlayer player = s.player;
        Mob villager = s.villager;

        if (player.isRemoved() || !player.isAlive() || villager.isRemoved() || !villager.isAlive()
                || player.level() != villager.level()
                || player.position().distanceToSqr(s.playerAnchor) > 1.5 * 1.5) {
            end(s, false);
            return;
        }

        pin(villager, s.villagerPos, s.villagerYaw);
        s.ticks++;

        if (HUG.equals(s.type)) {
            if (s.ticks >= HUG_TICKS) {
                rewardHug(player, villager);
                end(s, true);
            }
            return;
        }

        // cuddle
        ServerLevel level = (ServerLevel) villager.level();
        if (!(level.getBlockState(s.bedA).getBlock() instanceof BedBlock) || !(level.getBlockState(s.bedB).getBlock() instanceof BedBlock)) {
            end(s, false);
            return;
        }
        if (s.ticks % CUDDLE_HEART_INTERVAL == 0 && s.heartsGiven < CUDDLE_MAX_HEARTS) {
            s.heartsGiven++;
            addHearts(player, villager, 1);
            hearts(level, villager, 3);
            say(player, "message.mcaactions.cuddle.hearts", villager.getName(), 1);
        }
    }

    private static void rewardHug(ServerPlayer player, Mob villager) {
        ServerLevel level = (ServerLevel) villager.level();
        String key = player.getUUID() + "|" + villager.getUUID();
        long now = level.getGameTime();
        Long last = HUG_COOLDOWNS.get(key);
        hearts(level, villager, 6);
        if (last == null || now - last >= HUG_COOLDOWN_TICKS) {
            HUG_COOLDOWNS.put(key, now);
            addHearts(player, villager, HUG_HEARTS);
            say(player, "message.mcaactions.hug.done", villager.getName(), HUG_HEARTS);
        } else {
            say(player, "message.mcaactions.hug.again", villager.getName());
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        endInvolving(event.getEntity());
    }

    /** Getting hurt makes you get out of bed. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (CUDDLE.equals(s.type) && (s.player == event.getEntity() || s.villager == event.getEntity())) {
                end(s, false);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            end(s, false);
        }
        HUG_COOLDOWNS.clear();
    }

    /** Safety net: if the game closed mid-action, un-freeze the villager the next time it loads. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob
                && mob.getPersistentData().getBoolean(TAG_FROZEN) && !isBusy(mob)) {
            unfreeze(mob);
        }
    }

    private static void endInvolving(Entity entity) {
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (s.player == entity || s.villager == entity) {
                end(s, false);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isBusy(Mob villager) {
        for (Session s : SESSIONS.values()) {
            if (s.villager == villager) return true;
        }
        return false;
    }

    private static void prepareVillager(Mob villager) {
        if (villager.isSleeping()) {
            villager.stopSleeping();
        }
        villager.stopRiding();
        CompoundTag data = villager.getPersistentData();
        if (!data.getBoolean(TAG_FROZEN)) {
            data.putBoolean(TAG_PREV_NO_AI, villager.isNoAi());
            data.putBoolean(TAG_FROZEN, true);
        }
        villager.setNoAi(true);
        villager.getNavigation().stop();
    }

    private static void unfreeze(Mob villager) {
        CompoundTag data = villager.getPersistentData();
        if (data.getBoolean(TAG_FROZEN)) {
            villager.setNoAi(data.getBoolean(TAG_PREV_NO_AI));
            data.remove(TAG_FROZEN);
            data.remove(TAG_PREV_NO_AI);
        }
    }

    private static void pin(Mob villager, Vec3 pos, float yaw) {
        villager.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        villager.setYHeadRot(yaw);
        villager.setYBodyRot(yaw);
        villager.setDeltaMovement(Vec3.ZERO);
    }

    private static void addHearts(ServerPlayer player, Mob villager, int amount) {
        if (villager instanceof VillagerLike<?> mca) {
            mca.getVillagerBrain().getMemoriesForPlayer(player).modHearts(amount);
        }
    }

    private static void hearts(ServerLevel level, Entity at, int count) {
        level.sendParticles(ParticleTypes.HEART, at.getX(), at.getY() + at.getBbHeight() + 0.3, at.getZ(), count, 0.4, 0.2, 0.4, 0.0);
    }

    private static void say(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static Vec3 bedCenter(BlockPos head, Direction facing) {
        BlockPos foot = head.relative(facing.getOpposite());
        return new Vec3((head.getX() + foot.getX()) / 2.0 + 0.5, head.getY() + 0.5625, (head.getZ() + foot.getZ()) / 2.0 + 0.5);
    }

    private static void setOccupied(ServerLevel level, BlockPos head, boolean occupied) {
        if (head == null) return;
        BlockState state = level.getBlockState(head);
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.OCCUPIED) != occupied) {
            level.setBlock(head, state.setValue(BedBlock.OCCUPIED, occupied), 3);
        }
    }

    /** Two complete, free beds placed side by side, facing the same way. */
    private static BedPair findDoubleBed(ServerLevel level, BlockPos center, int r) {
        BedPair best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            BlockState state = level.getBlockState(mutable);
            if (!isFreeHead(level, mutable, state)) continue;

            BlockPos pos = mutable.immutable();
            Direction facing = state.getValue(BedBlock.FACING);
            for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                BlockPos other = pos.relative(side);
                BlockState otherState = level.getBlockState(other);
                if (isFreeHead(level, other, otherState) && otherState.getValue(BedBlock.FACING) == facing) {
                    double dist = pos.distSqr(center) + other.distSqr(center);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = new BedPair(pos, other, facing);
                    }
                }
            }
        }
        return best;
    }

    private static boolean isFreeHead(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD || state.getValue(BedBlock.OCCUPIED)) {
            return false;
        }
        BlockState foot = level.getBlockState(pos.relative(state.getValue(BedBlock.FACING).getOpposite()));
        return foot.getBlock() instanceof BedBlock && foot.getValue(BedBlock.PART) == BedPart.FOOT;
    }
}
