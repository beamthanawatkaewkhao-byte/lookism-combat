package com.lookismcombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = LookismCombat.MODID)
public class ServerEvents {
    public static class PState {
        public float gauge = 0;
        public int selected = 0;
        public Mode active = Mode.NONE;
    }

    static final Map<UUID, PState> STATES = new HashMap<>();
    static final UUID SPEED_ID = UUID.fromString("5d1c1c0e-7a31-4d7c-9a62-2a1a3b9d0f11");

    static PState st(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), k -> new PState()); }
    static float max() { return Config.GAUGE_MAX.get().floatValue(); }

    // ---------- actions from keys ----------
    public static void action(ServerPlayer p, int id) {
        PState s = st(p);
        if (id == 0) {
            if (s.active != Mode.NONE) deactivate(p, s); else activate(p, s);
        } else {
            int n = Mode.SELECTABLE.length;
            s.selected = Math.floorMod(s.selected + (id == 1 ? 1 : -1), n);
            p.displayClientMessage(Component.literal("Mode: " + Mode.SELECTABLE[s.selected].label), true);
            sync(p, s);
        }
    }

    static void activate(ServerPlayer p, PState s) {
        Mode m = Mode.SELECTABLE[s.selected];
        if (s.gauge < m.minFrac * max()) {
            p.displayClientMessage(Component.literal("Not enough gauge for " + m.label), true);
            return;
        }
        s.active = m;
        applySpeed(p, m);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5F, 1.4F);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
        p.displayClientMessage(Component.literal(m.label + " ACTIVATED"), true);
        sync(p, s);
    }

    static void deactivate(ServerPlayer p, PState s) {
        s.active = Mode.NONE;
        applySpeed(p, Mode.NONE);
        p.displayClientMessage(Component.literal("Mode ended"), true);
        sync(p, s);
    }

    static void applySpeed(ServerPlayer p, Mode m) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        a.removeModifier(SPEED_ID);
        if (m != Mode.NONE && m.speed != 0)
            a.addTransientModifier(new AttributeModifier(SPEED_ID, "lookism_mode", m.speed, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    static void applyMaxHealth(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return;
        double want = Config.MAX_HEALTH.get(), old = a.getBaseValue();
        if (old == want) return;
        a.setBaseValue(want);
        if (want > old) p.setHealth((float) (p.getHealth() + (want - old)));
        else if (p.getHealth() > want) p.setHealth((float) want);
    }

    public static void sync(ServerPlayer p, PState s) {
        Network.CH.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new Network.Sync(p.getUUID(), s.active.ordinal(), s.selected, s.gauge, max()));
    }

    // ---------- ticking ----------
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        PState s = st(p);
        float max = max();
        if (s.active != Mode.NONE) {
            s.gauge -= (float) (s.active.drainFrac * max / 20.0);
            if (s.gauge <= 0) { s.gauge = 0; deactivate(p, s); }
        } else {
            s.gauge += Config.PASSIVE.get().floatValue() / 20f;
        }
        s.gauge = Mth.clamp(s.gauge, 0, max);

        if (p.tickCount % 20 == 0) {
            applyMaxHealth(p);
            if (p.isAlive() && p.getHealth() < p.getMaxHealth() && p.getFoodData().getFoodLevel() >= 18)
                p.heal(Config.HP_REGEN.get().floatValue());
        }
        if (p.tickCount % 5 == 0) sync(p, s);
    }

    // ---------- combat ----------
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer v)) return;
        PState s = STATES.get(v.getUUID());
        if (s == null || s.active == Mode.NONE || s.active.dodge <= 0) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (v.getRandom().nextFloat() < s.active.dodge) {
            e.setCanceled(true);
            ((ServerLevel) v.level()).sendParticles(ParticleTypes.CLOUD, v.getX(), v.getY() + 1, v.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        float amt = e.getAmount();
        if (e.getSource().getEntity() instanceof ServerPlayer a && e.getSource().getDirectEntity() == a) {
            PState s = st(a);
            amt *= Config.DAMAGE_MULT.get().floatValue();
            if (a.getMainHandItem().isEmpty()) amt += Config.FIST_BONUS.get().floatValue();
            if (s.active != Mode.NONE) {
                amt *= Config.MODE_DAMAGE.get(s.active).get().floatValue();
                float yaw = a.getYRot() * Mth.DEG_TO_RAD;
                e.getEntity().knockback(0.4 * s.active.knock, Mth.sin(yaw), -Mth.cos(yaw));
                ((ServerLevel) a.level()).sendParticles(ParticleTypes.CRIT, e.getEntity().getX(), e.getEntity().getY() + 1,
                        e.getEntity().getZ(), 15, 0.3, 0.4, 0.3, 0.3);
            }
            s.gauge = Math.min(max(), s.gauge + Config.GAIN_HIT.get().floatValue());
        }
        if (e.getEntity() instanceof ServerPlayer v) {
            PState s = st(v);
            if (s.active != Mode.NONE) amt *= (float) (1.0 - s.active.resist);
            s.gauge = Math.min(max(), s.gauge + Config.GAIN_HURT.get().floatValue());
        }
        e.setAmount(amt);
    }

    // ---------- lifecycle ----------
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Network.CH.send(PacketDistributor.PLAYER.with(() -> p), Network.SettingsSync.current());
        applyMaxHealth(p);
        sync(p, st(p));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { STATES.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        STATES.remove(e.getEntity().getUUID());
        if (e.getEntity() instanceof ServerPlayer p) { applyMaxHealth(p); sync(p, st(p)); }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer viewer) {
            PState s = st(t);
            Network.CH.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new Network.Sync(t.getUUID(), s.active.ordinal(), s.selected, s.gauge, max()));
        }
    }
}
