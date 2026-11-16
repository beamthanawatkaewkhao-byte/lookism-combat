package com.lookismcombat;

import java.util.Arrays;

/** Lookism-inspired modes. minFrac/drainFrac are fractions of the max gauge (drain is per second). */
public enum Mode {
    NONE("Normal", 0, 0, 1.0, 0, 0, 0, 0, 0xFFAAAAAA, false),
    BIG_DADDY("Big Daddy", 0.25, 0.06, 2.0, -0.05, 0.35, 0.0, 1.2, 0xFFE05050, false),
    ULTRA_INSTINCT("Ultra Instinct", 0.30, 0.07, 1.4, 0.25, 0.0, 0.35, 0.3, 0xFF66CCFF, false),
    GAPRYONG_FIST("Gapryong's Fist", 0.40, 0.09, 2.6, 0.05, 0.20, 0.0, 1.0, 0xFFFF8800, false),
    ZACK_KICK("Zack's Kick", 0.30, 0.06, 1.9, 0.15, 0.10, 0.10, 0.7, 0xFFFFDD33, true),
    EXISTENCE("Existence", 1.00, 0.12, 3.6, 0.30, 0.40, 0.20, 1.5, 0xFFAA44FF, false);

    public static final Mode[] SELECTABLE = Arrays.stream(values()).filter(m -> m != NONE).toArray(Mode[]::new);

    public final String label;
    public final double minFrac, drainFrac, dmg, speed, resist, dodge, knock;
    public final int color;
    public final boolean kick;

    Mode(String label, double minFrac, double drainFrac, double dmg, double speed, double resist,
         double dodge, double knock, int color, boolean kick) {
        this.label = label; this.minFrac = minFrac; this.drainFrac = drainFrac; this.dmg = dmg;
        this.speed = speed; this.resist = resist; this.dodge = dodge; this.knock = knock;
        this.color = color; this.kick = kick;
    }

    public static Mode byId(int id) {
        Mode[] v = values();
        return id < 0 || id >= v.length ? NONE : v[id];
    }
}
