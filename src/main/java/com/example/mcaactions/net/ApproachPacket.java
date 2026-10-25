package com.example.mcaactions.net;

import com.example.mcaactions.client.AutoWalk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: walk to (x, z) on your own (start = true) or stop walking (start = false). */
public record ApproachPacket(boolean start, double x, double z) {
    public static void encode(ApproachPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.start);
        buf.writeDouble(p.x);
        buf.writeDouble(p.z);
    }

    public static ApproachPacket decode(FriendlyByteBuf buf) {
        return new ApproachPacket(buf.readBoolean(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(ApproachPacket p, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (p.start) AutoWalk.start(p.x, p.z);
            else AutoWalk.stop();
        }));
        ctx.setPacketHandled(true);
    }
}
