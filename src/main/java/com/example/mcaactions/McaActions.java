package com.example.mcaactions;

import com.example.mcaactions.net.ActionNetwork;
import net.minecraftforge.fml.common.Mod;

@Mod(McaActions.MOD_ID)
public class McaActions {
    public static final String MOD_ID = "mca_actions";

    public McaActions() {
        ActionNetwork.register();
    }
}
