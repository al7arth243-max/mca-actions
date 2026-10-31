package com.mcaactions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: an action ended; go back to normal rendering. */
public class ActionEndPacket {
    public final int playerId;
    public final int villagerId;

    public ActionEndPacket(int playerId, int villagerId) {
        this.playerId = playerId;
        this.villagerId = villagerId;
    }

    public ActionEndPacket(FriendlyByteBuf buf) {
        this.playerId = buf.readVarInt();
        this.villagerId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeVarInt(villagerId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mcaactions.client.ClientActions.onEnd(this));
        ctx.get().setPacketHandled(true);
    }
}
