package com.mcaactions;

import com.mcaactions.network.Network;
import com.mcaactions.server.ActionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(McaActions.MODID)
public class McaActions {
    public static final String MODID = "mcaactions";

    public McaActions() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(ActionManager.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
    }
}
