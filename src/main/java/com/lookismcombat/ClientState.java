package com.lookismcombat;

import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Plain data shared with the client (safe to load on a server too). */
public class ClientState {
    public record Info(int mode, int selected, float gauge, float gaugeMax) {}

    public static final Map<UUID, Info> INFO = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> PUNCH = new ConcurrentHashMap<>();
    public static volatile double damage = 1.0, maxHealth = 200, gain = 8, fist = 4;

    public static boolean rightFist(Player p) {
        return (PUNCH.getOrDefault(p.getUUID(), 0) & 1) == 0;
    }
}
