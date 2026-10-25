package com.example.mcaactions;

import javax.annotation.Nullable;

/**
 * The actions offered in the menu. enterTicks / outroTicks must match "enter.length" / "outro" (seconds * 20)
 * in assets/mca_actions/scenes/<id>.json; the server uses them to know when Switch/Leave are allowed and when
 * a scene is finished.
 */
public enum ActionType {
    HUG("hug", 0, 24),
    CUDDLE("cuddle", 56, 56);

    public final String id;
    public final int enterTicks, outroTicks;

    ActionType(String id, int enterTicks, int outroTicks) {
        this.id = id;
        this.enterTicks = enterTicks;
        this.outroTicks = outroTicks;
    }

    @Nullable
    public static ActionType byId(String id) {
        for (ActionType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
