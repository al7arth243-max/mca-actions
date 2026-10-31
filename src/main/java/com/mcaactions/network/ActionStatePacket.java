package com.mcaactions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: an action started; tells every nearby client which animation each participant plays. */
public class ActionStatePacket {
    public final int playerId;
    public final int villagerId;
    public final String playerAnim;
    public final String villagerAnim;
    public final float playerYaw;
    public final float villagerYaw;
    public final double anchorX, anchorY, anchorZ;
    public final boolean showStop;

    public ActionStatePacket(int playerId, int villagerId, String playerAnim, String villagerAnim,
                             float playerYaw, float villagerYaw, double anchorX, double anchorY, double anchorZ, boolean showStop) {
        this.playerId = playerId;
        this.villagerId = villagerId;
        this.playerAnim = playerAnim;
        this.villagerAnim = villagerAnim;
        this.playerYaw = playerYaw;
        this.villagerYaw = villagerYaw;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.showStop = showStop;
    }

    public ActionStatePacket(FriendlyByteBuf buf) {
        this.playerId = buf.readVarInt();
        this.villagerId = buf.readVarInt();
        this.playerAnim = buf.readUtf(64);
        this.villagerAnim = buf.readUtf(64);
        this.playerYaw = buf.readFloat();
        this.villagerYaw = buf.readFloat();
        this.anchorX = buf.readDouble();
        this.anchorY = buf.readDouble();
        this.anchorZ = buf.readDouble();
        this.showStop = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeVarInt(villagerId);
        buf.writeUtf(playerAnim, 64);
        buf.writeUtf(villagerAnim, 64);
        buf.writeFloat(playerYaw);
        buf.writeFloat(villagerYaw);
        buf.writeDouble(anchorX);
        buf.writeDouble(anchorY);
        buf.writeDouble(anchorZ);
        buf.writeBoolean(showStop);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mcaactions.client.ClientActions.onStart(this));
        ctx.get().setPacketHandled(true);
    }
}
