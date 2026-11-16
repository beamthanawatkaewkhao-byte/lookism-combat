package com.lookismcombat.client;

import com.lookismcombat.ClientState;
import com.lookismcombat.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

public class SettingsScreen extends Screen {
    private double dmg = ClientState.damage, hp = ClientState.maxHealth, gain = ClientState.gain, fist = ClientState.fist;

    public SettingsScreen() { super(Component.literal("Lookism Combat Settings")); }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 4;
        addRenderableWidget(new Slider(cx - 100, y, "Damage multiplier", "%.1f", 0.1, 20, dmg, v -> dmg = v));
        addRenderableWidget(new Slider(cx - 100, y + 24, "Max blood (HP)", "%.0f", 20, 2000, hp, v -> hp = v));
        addRenderableWidget(new Slider(cx - 100, y + 48, "Gauge gain / hit", "%.1f", 0, 50, gain, v -> gain = v));
        addRenderableWidget(new Slider(cx - 100, y + 72, "Fist bonus damage", "%.1f", 0, 50, fist, v -> fist = v));
        addRenderableWidget(Button.builder(Component.literal("Apply (OP only)"), b ->
                Network.CH.sendToServer(new Network.Settings(dmg, Math.round(hp), gain, fist)))
                .bounds(cx - 100, y + 104, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx + 2, y + 104, 98, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 4 - 18, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    private static class Slider extends AbstractSliderButton {
        final String label, fmt; final double min, max; final DoubleConsumer cb;

        Slider(int x, int y, String label, String fmt, double min, double max, double cur, DoubleConsumer cb) {
            super(x, y, 200, 20, Component.empty(), Math.max(0, Math.min(1, (cur - min) / (max - min))));
            this.label = label; this.fmt = fmt; this.min = min; this.max = max; this.cb = cb;
            updateMessage();
        }

        double val() { return min + value * (max - min); }

        @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + String.format(fmt, val()))); }
        @Override protected void applyValue() { cb.accept(val()); }
    }
}
