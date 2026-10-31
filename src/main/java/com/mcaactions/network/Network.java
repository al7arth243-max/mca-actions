package com.mcaactions.network;

import com.mcaactions.McaActions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(McaActions.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Network() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(StartActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StartActionPacket::encode).decoder(StartActionPacket::new)
                .consumerMainThread(StartActionPacket::handle).add();
        CHANNEL.messageBuilder(StopActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StopActionPacket::encode).decoder(StopActionPacket::new)
                .consumerMainThread(StopActionPacket::handle).add();
        CHANNEL.messageBuilder(ActionStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ActionStatePacket::encode).decoder(ActionStatePacket::new)
                .consumerMainThread(ActionStatePacket::handle).add();
        CHANNEL.messageBuilder(ActionEndPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ActionEndPacket::encode).decoder(ActionEndPacket::new)
                .consumerMainThread(ActionEndPacket::handle).add();
    }

    /** Sends to the player and everyone who can see them, so other players see the animation too. */
    public static void sendAround(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }
}
