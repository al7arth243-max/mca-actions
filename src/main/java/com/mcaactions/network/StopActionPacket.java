package com.mcaactions.network;

import com.mcaactions.server.ActionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player clicked Stop (or pressed Shift). */
public class StopActionPacket {
    public StopActionPacket() {}

    public StopActionPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ActionManager.stop(player);
        }
        ctx.get().setPacketHandled(true);
    }
}
