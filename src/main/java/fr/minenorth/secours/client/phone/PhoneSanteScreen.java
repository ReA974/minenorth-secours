package fr.minenorth.secours.client.phone;

import fr.minenorth.secours.vie.VieNet;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Application « Santé » dessinée dans l'écran du téléphone : forme, alimentation, repos, toxines, immunité, bonus actifs et maladie en cours.
 * Mêmes données que {@link fr.minenorth.secours.vie.client.SanteScreen}, reçues du serveur uniquement (rafraîchies toutes les 3 s).
 */
public class PhoneSanteScreen extends PhoneAppBase {
    private VieNet.State s;
    private int age;

    public PhoneSanteScreen(VieNet.State s) {
        super("Santé");
        this.s = s;
    }

    public void update(VieNet.State n) { this.s = n; }

    @Override protected String title() { return "Santé"; }

    @Override protected String[] tabs() { return new String[0]; }

    @Override protected String badge() { return s.disease().isEmpty() ? "En forme" : "Malade"; }

    @Override protected int badgeColor() { return s.disease().isEmpty() ? GREEN : RED; }

    @Override
    public void tick() {
        super.tick();
        if (++age % 60 == 0) VieNet.CHANNEL.sendToServer(new VieNet.Request(false));
    }

    private float bar(GuiGraphics g, String label, float v, float y, int color) {
        text(g, label, L, y, 9.5f, R - L - 50, TEXT);
        right(g, Math.round(v) + " %", R, y, 9.5f, WHITE);
        box(g, L, y + 15, R, y + 25, 5, CARD);
        float k = Math.max(0f, Math.min(1f, v / 100f));
        if (k > 0.02f) box(g, L, y + 15, L + (R - L) * k, y + 25, 5, color);
        return y + 36;
    }

    @Override
    protected float content(GuiGraphics g, int mx, int my, float y) {
        box(g, L, y, R, y + 46, 14, CARD);
        boolean sick = !s.disease().isEmpty();
        text(g, "État", L + 12, y + 8, 8.5f, 200, MUTED);
        text(g, sick ? s.disease() + " · environ " + s.minutesLeft() + " min" : "Aucune maladie", L + 12, y + 22, 12, R - L - 24, sick ? ALERT : OK);
        y += 56;
        y = bar(g, "Forme physique (sport)", s.forme(), y, CYAN);
        y = bar(g, "Équilibre alimentaire", s.nutrition(), y, GREEN);
        y = bar(g, "Repos (sommeil)", s.repos(), y, PINK);
        y = bar(g, "Toxines (drogues, alcool…)", s.toxines(), y, RED);
        boolean immune = s.immunity() >= s.immuneThreshold();
        y = bar(g, "Immunité" + (immune ? " · immunisé(e)" : ""), s.immunity(), y, immune ? OK : WARN);
        text(g, "BONUS ACTIFS", L, y + 2, 9, R - L, CYAN);
        y += 18;
        y = wrap(g, String.format("Course : +%.0f %%  ·  Vie : +%.1f coeur(s)", s.sprintPct(), s.hpBonus() / 2f), L, y, 9.5f, R - L, TEXT);
        if (s.boostMinutes() > 0) y = wrap(g, "Renfort d'immunité : encore " + s.boostMinutes() + " min", L, y + 2, 9.5f, R - L, OK);
        return wrap(g, "Courez, nagez, mangez équilibré, dormez : l'immunité protège des maladies.", L, y + 10, 8.5f, R - L, MUTED);
    }
}
