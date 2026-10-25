package com.example.mcaactions.net;

import com.example.mcaactions.ServerScenes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "make this villager do this action" (actionId "stop" ends the current one). */
public record PerformActionPacket(int entityId, String actionId) {
    public static void encode(PerformActionPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeUtf(p.actionId, 32);
    }

    public static PerformActionPacket decode(FriendlyByteBuf buf) {
        return new PerformActionPacket(buf.readVarInt(), buf.readUtf(32));
    }

    public static void handle(PerformActionPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) ServerScenes.request(player, p.entityId, p.actionId);
        });
        ctx.setPacketHandled(true);
    }
}
