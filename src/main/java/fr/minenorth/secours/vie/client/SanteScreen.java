package fr.minenorth.secours.vie.client;

import fr.minenorth.secours.vie.VieNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Écran « Santé » : forme, alimentation, repos, toxines, immunité, bonus actifs et maladie en cours. Données reçues du serveur uniquement. */
public class SanteScreen extends Screen {
    private static final int W = 260, H = 214;
    private VieNet.State s;
    private int left, top, age;

    private SanteScreen(VieNet.State s) {
        super(Component.literal("Santé"));
        this.s = s;
    }

    /** Appelé par le paquet d'état (thread client). */
    public static void receive(VieNet.State s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SanteScreen sc) sc.s = s;
        else if (s.open()) mc.setScreen(new SanteScreen(s));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public void tick() {
        if (++age % 60 == 0) VieNet.CHANNEL.sendToServer(new VieNet.Request(false));
    }

    private void bar(GuiGraphics g, String label, float v, int x, int y, int color, boolean invert) {
        g.drawString(font, label, x, y, 0xFFB8C7E8, false);
        String txt = Math.round(v) + " %";
        g.drawString(font, txt, x + W - 28 - font.width(txt), y, 0xFFFFFFFF, false);
        g.fill(x, y + 11, x + W - 28, y + 17, 0xFF0E0A34);
        float k = Math.max(0f, Math.min(1f, v / 100f));
        g.fill(x, y + 11, x + Math.round((W - 28) * k), y + 17, color);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, 0xFF161048);
        g.drawString(font, Component.literal("Santé et mode de vie").withStyle(net.minecraft.ChatFormatting.BOLD), left + 14, top + 10, 0xFFFFFFFF, false);
        g.fill(left + 10, top + 24, left + W - 10, top + 25, 0xFF2E2480);
        int x = left + 14, y = top + 32;
        bar(g, "Forme physique (sport)", s.forme(), x, y, 0xFF20AAEB, false);
        bar(g, "Équilibre alimentaire", s.nutrition(), x, y + 24, 0xFF1E9E52, false);
        bar(g, "Repos (sommeil)", s.repos(), x, y + 48, 0xFFC83CF0, false);
        bar(g, "Toxines (drogues, alcool…)", s.toxines(), x, y + 72, 0xFFB3263E, true);
        boolean immune = s.immunity() >= s.immuneThreshold();
        bar(g, "Immunité" + (immune ? " — immunisé(e)" : ""), s.immunity(), x, y + 98, immune ? 0xFF6CFF9A : 0xFFFFE066, false);
        g.fill(left + 10, y + 124, left + W - 10, y + 125, 0xFF2E2480);
        String sick = s.disease().isEmpty() ? "Aucune maladie" : s.disease() + " — environ " + s.minutesLeft() + " min";
        g.drawString(font, "État : ", x, y + 132, 0xFFB8C7E8, false);
        g.drawString(font, sick, x + font.width("État : "), y + 132, s.disease().isEmpty() ? 0xFF6CFF9A : 0xFFFF6A9A, false);
        String bonus = String.format("Course : +%.0f %%   ·   Vie : +%.1f coeur(s)", s.sprintPct(), s.hpBonus() / 2f);
        g.drawString(font, bonus, x, y + 146, 0xFFCFE3FF, false);
        if (s.boostMinutes() > 0) g.drawString(font, "Renfort d'immunité : encore " + s.boostMinutes() + " min", x, y + 158, 0xFF6CFF9A, false);
        g.drawString(font, "Courez, nagez, mangez équilibré, dormez : l'immunité protège des maladies.", x, top + H - 12, 0xFF8FA8E0, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
