package com.lookismcombat;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.EnumMap;
import java.util.Map;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT, MAX_HEALTH, GAUGE_MAX, GAIN_HIT,
            GAIN_HURT, PASSIVE, FIST_BONUS, HP_REGEN;
    public static final Map<Mode, ForgeConfigSpec.DoubleValue> MODE_DAMAGE = new EnumMap<>(Mode.class);

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        DAMAGE_MULT = b.comment("Global damage multiplier for player attacks")
                .defineInRange("damage_multiplier", 1.0, 0.0, 100.0);
        MAX_HEALTH = b.comment("Max blood (HP). Vanilla = 20")
                .defineInRange("max_health", 200.0, 1.0, 10000.0);
        HP_REGEN = b.comment("HP regenerated per second when food >= 18")
                .defineInRange("hp_regen_per_second", 1.0, 0.0, 1000.0);
        FIST_BONUS = b.comment("Extra damage when punching with an empty hand")
                .defineInRange("fist_bonus_damage", 4.0, 0.0, 1000.0);
        b.pop();

        b.push("gauge");
        GAUGE_MAX = b.defineInRange("gauge_max", 100.0, 10.0, 10000.0);
        GAIN_HIT = b.comment("Gauge gained per hit you land").defineInRange("gain_per_hit", 8.0, 0.0, 1000.0);
        GAIN_HURT = b.comment("Gauge gained when you get hit").defineInRange("gain_per_hurt", 4.0, 0.0, 1000.0);
        PASSIVE = b.comment("Passive gauge gain per second while no mode is active")
                .defineInRange("passive_per_second", 1.0, 0.0, 1000.0);
        b.pop();

        b.push("mode_damage_multiplier");
        for (Mode m : Mode.values())
            if (m != Mode.NONE)
                MODE_DAMAGE.put(m, b.defineInRange(m.name().toLowerCase(), m.dmg, 0.0, 100.0));
        b.pop();
        SPEC = b.build();
    }
}
