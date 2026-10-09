package fr.minenorth.secours.client;

import fr.minenorth.secours.DefibScore;
import fr.minenorth.secours.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Mini-jeu du défibrillateur : les battements arrivent de la droite vers la ligne de frappe, il faut cliquer (ou Espace) au bon moment.
 * Les frappes sont renvoyées au serveur, qui recalcule la précision lui-même.
 */
public class DefibScreen extends Screen {
    private static final int W = 320, H = 170;
    private static final float PX_PER_MS = 0.3f;
    private static final int NONE = 0, MISS = 1, GOOD = 2, PERFECT = 3;

    private final long[] beats;
    private final int[] result;
    private final List<Long> taps = new ArrayList<>();
    private final long startMs = System.currentTimeMillis();
    private boolean finished;
    private String lastJudge = "";
    private int lastJudgeColor = MineNorthStyle.MUTED;
    private long lastJudgeAt;

    public DefibScreen(long seed, int beatCount) {
        super(Component.literal("Défibrillateur"));
        this.beats = DefibScore.beats(seed, beatCount);
        this.result = new int[beats.length];
    }

    @Override public boolean isPauseScreen() { return false; }

    private long elapsed() { return System.currentTimeMillis() - startMs; }

    private void tap() {
        if (finished || taps.size() >= Math.min(ModNetwork.DefibResultPacket.MAX_TAPS, 2 * beats.length)) return;
        long t = elapsed();
        taps.add(t);
        int best = -1;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < beats.length; i++) {
            if (result[i] != NONE) continue;
            long diff = Math.abs(beats[i] - t);
            if (diff < bestDiff) { bestDiff = diff; best = i; }
        }
        if (best >= 0 && bestDiff <= DefibScore.GOOD_MS) {
            result[best] = bestDiff <= DefibScore.PERFECT_MS ? PERFECT : GOOD;
            judge(result[best] == PERFECT ? "PARFAIT" : "BON", result[best] == PERFECT ? MineNorthStyle.OK : MineNorthStyle.WARN);
        } else {
            judge("RATÉ", MineNorthStyle.ALERT);
        }
    }

    private void judge(String text, int color) { lastJudge = text; lastJudgeColor = color; lastJudgeAt = System.currentTimeMillis(); }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) { tap(); return true; }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE) { tap(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private void finish() {
        finished = true;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.DefibResultPacket(false, taps.stream().mapToLong(Long::longValue).toArray()));
        if (minecraft != null) minecraft.setScreen(null);
    }

    /** Fermeture par Échap : on prévient le serveur que le secouriste abandonne. */
    @Override
    public void onClose() {
        if (!finished) {
            finished = true;
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.DefibResultPacket(true, new long[0]));
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        long now = elapsed();
        if (!finished && now > beats[beats.length - 1] + 800) { finish(); return; }

        int left = (width - W) / 2, top = (height - H) / 2;
        MineNorthStyle.panel(g, left, top, W, H, "Défibrillateur", "Cliquez ou Espace sur chaque battement");

        int trackTop = top + 52, trackH = 70, mid = trackTop + trackH / 2;
        int strikeX = left + 60;
        g.fill(left + 10, trackTop, left + W - 10, trackTop + trackH, MineNorthStyle.LIST);
        g.fill(left + 10, mid, left + W - 10, mid + 1, MineNorthStyle.DARK);
        g.fill(strikeX - 1, trackTop, strikeX + 1, trackTop + trackH, MineNorthStyle.CYAN);

        int judged = 0;
        for (int i = 0; i < beats.length; i++) {
            if (result[i] == NONE && now > beats[i] + DefibScore.GOOD_MS) result[i] = MISS;
            if (result[i] != NONE) judged++;
            int x = strikeX + Math.round((beats[i] - now) * PX_PER_MS);
            if (x < left + 12 || x > left + W - 12) continue;
            int color = switch (result[i]) {
                case PERFECT -> MineNorthStyle.OK;
                case GOOD -> MineNorthStyle.WARN;
                case MISS -> MineNorthStyle.ALERT;
                default -> MineNorthStyle.WHITE;
            };
            // Pic d'électrocardiogramme + cœur au sommet.
            g.fill(x - 1, mid - 24, x + 1, mid + 14, color);
            g.fill(x - 4, mid - 30, x + 4, mid - 24, color);
        }

        double score = 0;
        for (int r : result) score += r == PERFECT ? 1.0 : r == GOOD ? 0.5 : 0;
        score /= beats.length;
        int barX = left + 10, barY = top + H - 30, barW = W - 20;
        g.fill(barX, barY, barX + barW, barY + 8, MineNorthStyle.LIST);
        g.fill(barX, barY, barX + (int) (barW * score), barY + 8, score >= 0.7 ? MineNorthStyle.GREEN : MineNorthStyle.PINK);
        g.fill(barX + (int) (barW * 0.7) - 1, barY - 2, barX + (int) (barW * 0.7) + 1, barY + 10, MineNorthStyle.WHITE);
        g.drawString(font, "Précision " + Math.round(score * 100) + " %  (70 % requis)   Battements : " + judged + "/" + beats.length,
                barX, barY + 13, MineNorthStyle.MUTED, false);

        if (System.currentTimeMillis() - lastJudgeAt < 500 && !lastJudge.isEmpty()) {
            g.drawCenteredString(font, lastJudge, strikeX, trackTop - 11, lastJudgeColor);
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
