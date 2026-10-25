package com.example.mcaactions.net;

import com.example.mcaactions.client.ClientSceneState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients. kind: 0 = start, 1 = switch roles, 2 = leave (play outro), 3 = end (remove).
 * sceneYaw: yaw of the scene frame. sx/sy/sz: where the entity stood at the start, in the scene frame (1/16 block).
 * startYaw: the entity's facing at the start relative to the scene. elapsed: ticks since the scene began.
 */
public record SceneSyncPacket(int kind, int entityId, String scene, int role, float sceneYaw,
                              float sx, float sy, float sz, float startYaw, int elapsed) {
    public static void encode(SceneSyncPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.kind);
        buf.writeVarInt(p.entityId);
        buf.writeUtf(p.scene, 32);
        buf.writeByte(p.role);
        buf.writeFloat(p.sceneYaw);
        buf.writeFloat(p.sx);
        buf.writeFloat(p.sy);
        buf.writeFloat(p.sz);
        buf.writeFloat(p.startYaw);
        buf.writeVarInt(p.elapsed);
    }

    public static SceneSyncPacket decode(FriendlyByteBuf buf) {
        return new SceneSyncPacket(buf.readByte(), buf.readVarInt(), buf.readUtf(32), buf.readByte(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(SceneSyncPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSceneState.handle(p)));
        ctx.setPacketHandled(true);
    }
}
