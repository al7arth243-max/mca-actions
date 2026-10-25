package com.example.mcaactions.net;

import com.example.mcaactions.McaActions;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ActionNetwork {
    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(McaActions.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ActionNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, PerformActionPacket.class, PerformActionPacket::encode,
                PerformActionPacket::decode, PerformActionPacket::handle);
        CHANNEL.registerMessage(id++, SceneSyncPacket.class, SceneSyncPacket::encode,
                SceneSyncPacket::decode, SceneSyncPacket::handle);
        CHANNEL.registerMessage(id++, SceneControlPacket.class, SceneControlPacket::encode,
                SceneControlPacket::decode, SceneControlPacket::handle);
        CHANNEL.registerMessage(id++, ApproachPacket.class, ApproachPacket::encode,
                ApproachPacket::decode, ApproachPacket::handle);
    }
}
