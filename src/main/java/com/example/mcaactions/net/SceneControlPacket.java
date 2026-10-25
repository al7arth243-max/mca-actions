package com.example.mcaactions.net;

import com.example.mcaactions.ServerScenes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the on-screen buttons. control 0 = Leave, 1 = Switch places. */
public record SceneControlPacket(int control) {
    public static void encode(SceneControlPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.control);
    }

    public static SceneControlPacket decode(FriendlyByteBuf buf) {
        return new SceneControlPacket(buf.readByte());
    }

    public static void handle(SceneControlPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) ServerScenes.control(player, p.control);
        });
        ctx.setPacketHandled(true);
    }
}
