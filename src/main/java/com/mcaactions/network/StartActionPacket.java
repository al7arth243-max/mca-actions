package com.mcaactions.network;

import com.mcaactions.server.ActionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player clicked an action button for a villager. */
public class StartActionPacket {
    private final int villagerId;
    private final String action;

    public StartActionPacket(int villagerId, String action) {
        this.villagerId = villagerId;
        this.action = action;
    }

    public StartActionPacket(FriendlyByteBuf buf) {
        this.villagerId = buf.readVarInt();
        this.action = buf.readUtf(32);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(villagerId);
        buf.writeUtf(action, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ActionManager.start(player, villagerId, action);
        }
        ctx.get().setPacketHandled(true);
    }
}
