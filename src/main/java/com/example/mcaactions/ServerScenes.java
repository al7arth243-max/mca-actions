package com.example.mcaactions;

import com.example.mcaactions.net.ActionNetwork;
import com.example.mcaactions.net.ApproachPacket;
import com.example.mcaactions.net.SceneSyncPacket;
import forge.net.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Server side of the scenes. Two phases per (player, villager) pair:
 *  1. APPROACH: both really walk to their starting spot (villager via pathfinding, the player is auto-walked
 *     by the client). Shift cancels.
 *  2. PLAY: both are held on a pivot point while the clients play keyframes relative to it, so they are free
 *     to overlap (one bed, one hug) instead of using vanilla slots. Leave plays the sequence out and puts both
 *     back on their feet beside where they were.
 */
@Mod.EventBusSubscriber(modid = McaActions.MOD_ID)
public final class ServerScenes {
    private static final int BED_SEARCH_RADIUS = 16;
    private static final int APPROACH_TIMEOUT_TICKS = 200;
    private static final int SWITCH_COOLDOWN_TICKS = 24;
    private static final double BED_TOP = 9.0 / 16.0;

    private static final class Session {
        final ActionType type;
        final UUID player, villager;
        final long created;
        final Vec3 pivot;
        final float sceneYaw;
        boolean playing, swapped, leaving, cancelled;
        long playStart, switchedAt = -1000, leaveAt;

        Session(ActionType type, UUID player, UUID villager, long created, Vec3 pivot, float sceneYaw) {
            this.type = type;
            this.player = player;
            this.villager = villager;
            this.created = created;
            this.pivot = pivot;
            this.sceneYaw = sceneYaw;
        }

        int playerRole() { return swapped ? 1 : 0; }
    }

    private record BedPlace(Vec3 pivot, Direction head) {}

    private static final List<Session> SESSIONS = new ArrayList<>();

    private ServerScenes() {}

    /**
     * Where a role stands before the scene starts / after it ends, in the scene frame (1/16 block):
     * {x, y, z, yaw}. Must match the first key of "enter" (or the loop pose) in the scene JSON.
     */
    private static float[] rest(ActionType type, int role) {
        return switch (type) {
            case HUG -> role == 0 ? new float[]{0, 0, -6, 0} : new float[]{0, 0, 6, 180};
            case CUDDLE -> role == 0 ? new float[]{23, -9, 0, -90} : new float[]{-23, -9, 8, 90};
        };
    }

    private static Vec3 sceneToWorld(Session s, double px, double py, double pz) {
        double rad = Math.toRadians(s.sceneYaw), c = Math.cos(rad), sn = Math.sin(rad);
        double x = px / 16.0, z = pz / 16.0;
        return new Vec3(s.pivot.x + x * c - z * sn, s.pivot.y + py / 16.0, s.pivot.z + x * sn + z * c);
    }

    private static Vec3 restWorld(Session s, int role) {
        float[] r = rest(s.type, role);
        return sceneToWorld(s, r[0], r[1], r[2]);
    }

    // ---------- starting ----------

    public static void request(ServerPlayer player, int entityId, String actionId) {
        Entity e = player.serverLevel().getEntity(entityId);
        if (!(e instanceof VillagerEntityMCA) || !(e instanceof Mob mob)) return;
        if (!mob.isAlive() || player.isSpectator() || player.distanceToSqr(mob) > 64) return;
        ActionType type = ActionType.byId(actionId);
        if (type == null) return;

        MinecraftServer server = player.server;
        for (Session old : new ArrayList<>(SESSIONS)) {
            if (old.player.equals(player.getUUID()) || old.villager.equals(mob.getUUID())) {
                finish(server, old);
                SESSIONS.remove(old);
            }
        }

        Vec3 pivot;
        float yaw;
        if (type == ActionType.HUG) {
            Vec3 mid = player.position().add(mob.position()).scale(0.5);
            pivot = new Vec3(mid.x, player.getY(), mid.z);
            double dx = mob.getX() - player.getX(), dz = mob.getZ() - player.getZ();
            yaw = dx * dx + dz * dz < 1e-4
                    ? player.getYRot()
                    : (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f; // scene +z points player -> villager
        } else {
            BedPlace bed = findBed(player.serverLevel(), player.blockPosition());
            if (bed == null) {
                player.displayClientMessage(Component.translatable("mca_actions.message.no_double_bed"), true);
                return;
            }
            pivot = bed.pivot;
            yaw = bed.head.toYRot() + 180f; // scene -z points to the head of the bed
        }

        Session s = new Session(type, player.getUUID(), mob.getUUID(), mob.level().getGameTime(), pivot,
                Mth.wrapDegrees(yaw));
        SESSIONS.add(s);

        player.stopRiding();
        mob.stopRiding();
        if (mob.isSleeping()) mob.stopSleeping();

        // Phase 1: walk. The player is auto-walked by the client, the villager paths there.
        Vec3 pt = restWorld(s, s.playerRole()), mt = restWorld(s, 1 - s.playerRole());
        ActionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ApproachPacket(true, pt.x, pt.z));
        mob.getNavigation().moveTo(mt.x, mt.y, mt.z, 1.0);
    }

    private static void beginPlay(Session s, ServerPlayer p, Mob m, long now) {
        s.playing = true;
        s.playStart = now;
        ActionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ApproachPacket(false, 0, 0));

        // Tell clients where everyone stands (relative to the pivot) BEFORE moving them, so they can blend in.
        send(p, packet(0, p, s, s.playerRole(), 0));
        send(m, packet(0, m, s, 1 - s.playerRole(), 0));

        p.teleportTo(s.pivot.x, s.pivot.y, s.pivot.z);
        m.moveTo(s.pivot.x, s.pivot.y, s.pivot.z, m.getYRot(), m.getXRot());
        m.getNavigation().stop();
        hearts(m);
    }

    private static BedPlace findBed(ServerLevel level, BlockPos origin) {
        BedPlace bestDouble = null, bestSingle = null;
        double dDouble = Double.MAX_VALUE, dSingle = Double.MAX_VALUE;
        int r = BED_SEARCH_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-r, -4, -r), origin.offset(r, 4, r))) {
            BlockState a = level.getBlockState(p);
            if (!isFreeBedHead(a)) continue;
            Direction f = a.getValue(BedBlock.FACING);
            double dist = p.distSqr(origin);

            BlockPos other = p.relative(f.getClockWise());
            BlockState b = level.getBlockState(other);
            if (isFreeBedHead(b) && b.getValue(BedBlock.FACING) == f) {
                if (dist < dDouble) {
                    dDouble = dist;
                    bestDouble = new BedPlace(center(p.getY(), p, other, p.relative(f.getOpposite()),
                            other.relative(f.getOpposite())), f);
                }
            } else if (dist < dSingle) {
                dSingle = dist;
                bestSingle = new BedPlace(center(p.getY(), p, p.relative(f.getOpposite())), f);
            }
        }
        return bestDouble != null ? bestDouble : bestSingle;
    }

    private static Vec3 center(int y, BlockPos... blocks) {
        double x = 0, z = 0;
        for (BlockPos b : blocks) {
            x += b.getX() + 0.5;
            z += b.getZ() + 0.5;
        }
        return new Vec3(x / blocks.length, y + BED_TOP, z / blocks.length);
    }

    private static boolean isFreeBedHead(BlockState s) {
        return s.is(BlockTags.BEDS)
                && s.hasProperty(BedBlock.PART) && s.getValue(BedBlock.PART) == BedPart.HEAD
                && s.hasProperty(BedBlock.FACING)
                && !(s.hasProperty(BedBlock.OCCUPIED) && s.getValue(BedBlock.OCCUPIED));
    }

    // ---------- buttons ----------

    public static void control(ServerPlayer player, int control) {
        for (Session s : SESSIONS) {
            if (!s.player.equals(player.getUUID()) || s.leaving) continue;
            MinecraftServer server = player.server;
            long now = player.level().getGameTime();
            if (!s.playing) { // still walking: any button / sneak cancels
                if (control == 0) s.cancelled = true;
                return;
            }
            if (now - s.playStart < s.type.enterTicks) return; // still climbing in
            Mob m = findVillager(server, s.villager);
            int elapsed = (int) (now - s.playStart);
            if (control == 0) {
                s.leaving = true;
                s.leaveAt = now;
                send(player, packet(2, player, s, s.playerRole(), elapsed));
                if (m != null) send(m, packet(2, m, s, 1 - s.playerRole(), elapsed));
            } else if (control == 1 && now - s.switchedAt >= SWITCH_COOLDOWN_TICKS) {
                s.swapped = !s.swapped;
                s.switchedAt = now;
                send(player, packet(1, player, s, s.playerRole(), elapsed));
                if (m != null) send(m, packet(1, m, s, 1 - s.playerRole(), elapsed));
            }
            return;
        }
    }

    // ---------- per tick ----------

    private static double horizontal(Entity e, Vec3 v) {
        return Math.hypot(e.getX() - v.x, e.getZ() - v.z);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Session> it = SESSIONS.iterator();
        while (it.hasNext()) {
            Session s = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(s.player);
            Mob m = findVillager(server, s.villager);
            if (p == null || m == null || !p.isAlive() || !m.isAlive() || p.level() != m.level() || s.cancelled) {
                finish(server, s);
                it.remove();
                continue;
            }
            long now = m.level().getGameTime();

            if (!s.playing) {
                Vec3 pt = restWorld(s, s.playerRole()), mt = restWorld(s, 1 - s.playerRole());
                double pd = horizontal(p, pt), md = horizontal(m, mt);
                if (pd < 0.3 && md < 0.45) {
                    beginPlay(s, p, m, now);
                } else if (now - s.created > APPROACH_TIMEOUT_TICKS) {
                    if (pd < 1.5 && md < 1.5) {
                        beginPlay(s, p, m, now); // close enough, the start blend covers the rest
                    } else {
                        p.displayClientMessage(Component.translatable("mca_actions.message.cant_reach"), true);
                        finish(server, s);
                        it.remove();
                    }
                } else {
                    if ((now - s.created) % 10 == 0 || m.getNavigation().isDone()) {
                        if (md > 0.3) m.getNavigation().moveTo(mt.x, mt.y, mt.z, 1.0);
                    }
                    m.getLookControl().setLookAt(s.type == ActionType.HUG ? p.getEyePosition() : mt.add(0, 1.4, 0));
                }
                continue;
            }

            if (s.leaving && now - s.leaveAt >= s.type.outroTicks) {
                finish(server, s);
                it.remove();
                continue;
            }
            // Keep both exactly on the pivot; the clients offset them visually.
            if (p.position().distanceToSqr(s.pivot) > 0.09) p.teleportTo(s.pivot.x, s.pivot.y, s.pivot.z);
            p.fallDistance = 0;
            if (m.position().distanceToSqr(s.pivot) > 0.0004) {
                m.moveTo(s.pivot.x, s.pivot.y, s.pivot.z, m.getYRot(), m.getXRot());
            }
            m.getNavigation().stop();
            m.setDeltaMovement(Vec3.ZERO);
            if ((now - s.playStart) % 60 == 30) hearts(m);
        }
    }

    /** Ends a session in any phase: tells clients, and puts both characters on their feet at their rest spots. */
    private static void finish(MinecraftServer server, Session s) {
        ServerPlayer p = server.getPlayerList().getPlayer(s.player);
        Mob m = findVillager(server, s.villager);
        if (p != null) ActionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ApproachPacket(false, 0, 0));
        if (m != null) m.getNavigation().stop();
        if (!s.playing) return;

        if (p != null) {
            send(p, packet(3, p, s, 0, 0));
            if (p.isAlive()) placeAtRest(s, p, s.playerRole());
        }
        if (m != null) {
            send(m, packet(3, m, s, 0, 0));
            if (m.isAlive()) placeAtRest(s, m, 1 - s.playerRole());
        }
    }

    private static void placeAtRest(Session s, LivingEntity e, int role) {
        float[] r = rest(s.type, role);
        Vec3 w = sceneToWorld(s, r[0], r[1], r[2]);
        float yaw = Mth.wrapDegrees(s.sceneYaw - r[3]);
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(sp.serverLevel(), w.x, w.y, w.z, yaw, sp.getXRot());
        } else {
            e.moveTo(w.x, w.y, w.z, yaw, 0f);
        }
        e.setYBodyRot(yaw);
        e.setYHeadRot(yaw);
    }

    private static Mob findVillager(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Mob mob) return mob;
        }
        return null;
    }

    // ---------- networking helpers ----------

    private static SceneSyncPacket packet(int kind, LivingEntity e, Session s, int role, int elapsed) {
        double rad = Math.toRadians(s.sceneYaw), cos = Math.cos(rad), sin = Math.sin(rad);
        Vec3 d = e.position().subtract(s.pivot);
        float sx = (float) ((d.x * cos + d.z * sin) * 16.0);
        float sz = (float) ((-d.x * sin + d.z * cos) * 16.0);
        float sy = (float) (d.y * 16.0);
        float startYaw = Mth.wrapDegrees(s.sceneYaw - e.yBodyRot);
        return new SceneSyncPacket(kind, e.getId(), s.type.id, role, s.sceneYaw, sx, sy, sz, startYaw, elapsed);
    }

    private static void send(Entity entity, SceneSyncPacket pkt) {
        ActionNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), pkt);
    }

    private static void hearts(Mob mob) {
        if (mob.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 0.9, mob.getZ(), 4, 0.4, 0.2, 0.4, 0.02);
        }
    }

    // ---------- events ----------

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        UUID id = e.getEntity().getUUID();
        for (Session s : SESSIONS) {
            if (s.villager.equals(id) || s.player.equals(id)) s.cancelled = true;
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getTarget() instanceof LivingEntity target)) return;
        for (Session s : SESSIONS) {
            if (!s.playing) continue;
            boolean isPlayer = s.player.equals(target.getUUID());
            if (!isPlayer && !s.villager.equals(target.getUUID())) continue;
            int role = isPlayer ? s.playerRole() : 1 - s.playerRole();
            int elapsed = (int) (target.level().getGameTime() - s.playStart);
            ActionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), packet(0, target, s, role, elapsed));
            if (s.leaving) {
                ActionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), packet(2, target, s, role, elapsed));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        SESSIONS.clear();
    }
}
