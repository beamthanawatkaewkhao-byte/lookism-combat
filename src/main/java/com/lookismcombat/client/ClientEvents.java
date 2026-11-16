package com.lookismcombat.client;

import com.lookismcombat.ClientState;
import com.lookismcombat.LookismCombat;
import com.lookismcombat.Mode;
import com.lookismcombat.Network;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

public class ClientEvents {

    static final KeyMapping TOGGLE = key("toggle", GLFW.GLFW_KEY_R);
    static final KeyMapping NEXT = key("next", GLFW.GLFW_KEY_G);
    static final KeyMapping SETTINGS = key("settings", GLFW.GLFW_KEY_O);

    static KeyMapping key(String n, int code) {
        return new KeyMapping("key." + LookismCombat.MODID + "." + n, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, code, "key.categories." + LookismCombat.MODID);
    }

    // ======================= MOD BUS =======================
    @Mod.EventBusSubscriber(modid = LookismCombat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(TOGGLE); e.register(NEXT); e.register(SETTINGS);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("lookism_hud", Hud::render);
        }

        /** Swap the player model for our animated one. */
        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                PlayerRenderer r = e.getSkin(skin);
                if (r == null) continue;
                boolean slim = skin.equals("slim");
                ModelPart root = e.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER);
                // LivingEntityRenderer.model (SRG f_115290_)
                ObfuscationReflectionHelper.setPrivateValue(LivingEntityRenderer.class, r,
                        new LookismPlayerModel(root, slim), "f_115290_");
            }
        }
    }

    // ======================= FORGE BUS =======================
    @Mod.EventBusSubscriber(modid = LookismCombat.MODID, value = Dist.CLIENT)
    public static class ForgeBus {

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (TOGGLE.consumeClick()) Network.CH.sendToServer(new Network.Action(0));
            while (NEXT.consumeClick()) Network.CH.sendToServer(new Network.Action(Screen.hasShiftDown() ? 2 : 1));
            while (SETTINGS.consumeClick()) if (mc.screen == null) mc.setScreen(new SettingsScreen());
        }

        /** count swings so punches alternate left / right */
        @SubscribeEvent
        public static void playerTick(TickEvent.PlayerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && e.side.isClient()) {
                Player p = e.player;
                if (p.swinging && p.swingTime == 0) ClientState.PUNCH.merge(p.getUUID(), 1, Integer::sum);
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientState.INFO.clear(); ClientState.PUNCH.clear();
        }

        /** hide vanilla hearts, we draw a blood bar instead */
        @SubscribeEvent
        public static void hideHearts(RenderGuiOverlayEvent.Pre e) {
            if (e.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameMode != null && mc.gameMode.canHurtPlayer()) {
                    e.setCanceled(true);
                    ((ForgeGui) mc.gui).leftHeight += 10; // keep armor bar above our blood bar
                }
            }
        }

        // ---- whole-body lean / twist / lunge ----
        static boolean poseActive(Player p) {
            ClientState.Info i = ClientState.INFO.get(p.getUUID());
            if (i == null || i.mode() == 0) return false;
            if (p.isPassenger() || p.isFallFlying() || p.isSwimming() || p.isSleeping()) return false;
            return true;
        }

        @SubscribeEvent
        public static void renderPre(RenderPlayerEvent.Pre e) {
            Player p = e.getEntity();
            if (!poseActive(p)) return;
            Mode m = Mode.byId(ClientState.INFO.get(p.getUUID()).mode());
            float pt = e.getPartialTick();
            float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            float t = p.getAttackAnim(pt);
            boolean punch = t > 0 && p.getMainHandItem().isEmpty();
            float s = Mth.sin(t * Mth.PI);
            float lean = 0, twist = 0, lunge = 0;
            if (p.isSprinting()) lean = (m == Mode.EXISTENCE || m == Mode.ULTRA_INSTINCT) ? 0.5F : 0.38F;
            if (punch) {
                if (m.kick) lean -= 0.3F * s;
                else {
                    twist = (ClientState.rightFist(p) ? 1 : -1) * 0.5F * s;
                    lean += 0.25F * s; lunge = 0.25F * s;
                }
            }
            var ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(0, 0.75, 0);                                    // pivot at the hips
            ps.mulPose(Axis.YP.rotation(-yaw * Mth.DEG_TO_RAD + twist));
            ps.translate(0, 0, lunge);
            ps.mulPose(Axis.XP.rotation(lean));
            ps.translate(0, -0.75, 0);
            ps.mulPose(Axis.YP.rotation(yaw * Mth.DEG_TO_RAD));          // restore facing for the renderer
        }

        @SubscribeEvent
        public static void renderPost(RenderPlayerEvent.Post e) {
            if (poseActive(e.getEntity())) e.getPoseStack().popPose();
        }
    }

    // ======================= HUD =======================
    static class Hud {
        static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
            Minecraft mc = Minecraft.getInstance();
            Player p = mc.player;
            if (p == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
            Font f = mc.font;

            // ---------- blood bar (replaces hearts) ----------
            float hp = p.getHealth(), max = p.getMaxHealth();
            float r = Mth.clamp(hp / max, 0F, 1F);
            int bw = 81, bh = 9, x = w / 2 - 91, y = h - 39;
            g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000);
            g.fill(x, y, x + bw, y + bh, 0xFF2A0808);
            int fw = Math.round(bw * r);
            boolean low = r < 0.25F && (p.tickCount / 5) % 2 == 0;
            g.fill(x, y, x + fw, y + 4, low ? 0xFFFF6A6A : 0xFFE83A3A);
            g.fill(x, y + 4, x + fw, y + bh, low ? 0xFFD02020 : 0xFFA81414);
            g.drawCenteredString(f, (int) Math.ceil(hp) + " / " + (int) max, x + bw / 2, y + 1, 0xFFFFFF);

            // ---------- mode gauge (top-left) ----------
            ClientState.Info i = ClientState.INFO.get(p.getUUID());
            if (i == null) return;
            Mode act = Mode.byId(i.mode());
            Mode sel = Mode.SELECTABLE[Mth.clamp(i.selected(), 0, Mode.SELECTABLE.length - 1)];
            Mode shown = act != Mode.NONE ? act : sel;
            int gx = 8, gy = 22, gw = 110, gh = 10;
            String title = act != Mode.NONE ? act.label + "  [ACTIVE]" : "Mode: " + sel.label;
            g.drawString(f, title, gx, gy - 12, act != Mode.NONE ? act.color : 0xFFFFFF, true);
            g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, 0xFF000000);
            g.fill(gx, gy, gx + gw, gy + gh, 0xFF202020);
            int gf = Math.round(gw * Mth.clamp(i.gauge() / i.gaugeMax(), 0F, 1F));
            g.fill(gx, gy, gx + gf, gy + gh, shown.color);
            int mark = gx + (int) (gw * shown.minFrac);
            g.fill(mark, gy, mark + 1, gy + gh, 0xFFFFFFFF); // minimum gauge needed to activate
            g.drawCenteredString(f, (int) i.gauge() + " / " + (int) i.gaugeMax(), gx + gw / 2, gy + 1, 0xFFFFFF);
            g.drawString(f, "R: toggle   G: switch   O: settings", gx, gy + gh + 4, 0xAAAAAA, true);
        }
    }
}
