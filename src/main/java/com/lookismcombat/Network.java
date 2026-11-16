package com.lookismcombat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

public class Network {
    private static final String PV = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LookismCombat.MODID, "main"), () -> PV, PV::equals, PV::equals);

    public static void init() {
        int i = 0;
        CH.registerMessage(i++, Action.class, Action::enc, Action::dec, Action::handle);
        CH.registerMessage(i++, Settings.class, Settings::enc, Settings::dec, Settings::handle);
        CH.registerMessage(i++, Sync.class, Sync::enc, Sync::dec, Sync::handle);
        CH.registerMessage(i++, SettingsSync.class, SettingsSync::enc, SettingsSync::dec, SettingsSync::handle);
    }

    /** C2S: 0 = toggle, 1 = next mode, 2 = previous mode */
    public record Action(int id) {
        void enc(FriendlyByteBuf b) { b.writeByte(id); }
        static Action dec(FriendlyByteBuf b) { return new Action(b.readByte()); }
        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null) ServerEvents.action(p, id);
            });
            c.get().setPacketHandled(true);
        }
    }

    /** C2S: change settings (OP / singleplayer owner only) */
    public record Settings(double damage, double maxHealth, double gain, double fist) {
        void enc(FriendlyByteBuf b) { b.writeDouble(damage); b.writeDouble(maxHealth); b.writeDouble(gain); b.writeDouble(fist); }
        static Settings dec(FriendlyByteBuf b) { return new Settings(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()); }
        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p == null) return;
                if (!(p.hasPermissions(2) || p.server.isSingleplayerOwner(p.getGameProfile()))) {
                    p.sendSystemMessage(Component.literal("Lookism Combat: you need OP to change settings."));
                    return;
                }
                Config.DAMAGE_MULT.set(Mth.clamp(damage, 0.0, 100.0));
                Config.MAX_HEALTH.set(Mth.clamp(maxHealth, 1.0, 10000.0));
                Config.GAIN_HIT.set(Mth.clamp(gain, 0.0, 1000.0));
                Config.FIST_BONUS.set(Mth.clamp(fist, 0.0, 1000.0));
                Config.SPEC.save();
                CH.send(PacketDistributor.ALL.noArg(), SettingsSync.current());
            });
            c.get().setPacketHandled(true);
        }
    }

    /** S2C: one player's mode state */
    public record Sync(UUID id, int mode, int selected, float gauge, float gaugeMax) {
        void enc(FriendlyByteBuf b) { b.writeUUID(id); b.writeVarInt(mode); b.writeVarInt(selected); b.writeFloat(gauge); b.writeFloat(gaugeMax); }
        static Sync dec(FriendlyByteBuf b) { return new Sync(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat()); }
        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> ClientState.INFO.put(id, new ClientState.Info(mode, selected, gauge, gaugeMax)));
            c.get().setPacketHandled(true);
        }
    }

    /** S2C: current settings values */
    public record SettingsSync(double damage, double maxHealth, double gain, double fist) {
        static SettingsSync current() {
            return new SettingsSync(Config.DAMAGE_MULT.get(), Config.MAX_HEALTH.get(), Config.GAIN_HIT.get(), Config.FIST_BONUS.get());
        }
        void enc(FriendlyByteBuf b) { b.writeDouble(damage); b.writeDouble(maxHealth); b.writeDouble(gain); b.writeDouble(fist); }
        static SettingsSync dec(FriendlyByteBuf b) { return new SettingsSync(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()); }
        void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ClientState.damage = damage; ClientState.maxHealth = maxHealth;
                ClientState.gain = gain; ClientState.fist = fist;
            });
            c.get().setPacketHandled(true);
        }
    }
}
