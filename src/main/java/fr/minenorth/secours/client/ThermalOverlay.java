package fr.minenorth.secours.client;

import fr.minenorth.secours.MineNorthSecours;
import fr.minenorth.secours.pompier.AriMaskItem;
import fr.minenorth.secours.pompier.ThermalCameraItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran de la caméra thermique et cadre du masque ARI, dessinés en surimpression (HUD).
 * <p>
 * Les points chauds envoyés par le serveur sont projetés à l'écran avec les matrices de la caméra relevées pendant le rendu du
 * monde : rien n'est dessiné dans le monde lui-même, ce qui reste compatible avec Embeddium et les shaders d'Oculus.
 */
@Mod.EventBusSubscriber(modid = MineNorthSecours.MOD_ID, value = Dist.CLIENT)
public final class ThermalOverlay {
    private ThermalOverlay() {}

    private static volatile List<int[]> points = List.of();
    private static volatile long receivedAt;
    private static final Matrix4f VIEW = new Matrix4f(), PROJ = new Matrix4f();
    private static Vec3 cam = Vec3.ZERO;
    private static boolean haveMatrices;

    public static void receive(List<int[]> pts) {
        points = new ArrayList<>(pts);
        receivedAt = System.currentTimeMillis();
    }

    /** Relève les matrices de la caméra (à chaque étape : un shader peut en sauter certaines, la dernière vue suffit). */
    @SubscribeEvent
    public static void level(RenderLevelStageEvent e) {
        VIEW.set(e.getPoseStack().last().pose());
        PROJ.set(e.getProjectionMatrix());
        cam = e.getCamera().getPosition();
        haveMatrices = true;
    }

    /** Température affichée : stable pour un point donné (pas de clignotement d'une seconde à l'autre). */
    private static int temperature(int[] p) {
        int h = Math.abs((p[0] * 73856093) ^ (p[1] * 19349663) ^ (p[2] * 83492791));
        return switch (p[3]) {
            case 0 -> 540 + h % 220;
            case 1 -> 170 + h % 90;
            default -> 36;
        };
    }

    public static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (AriMaskItem.wearing(mc.player)) maskFrame(g, mc.font, w, h);
        if (!ThermalCameraItem.holding(mc.player)) return;
        Font font = mc.font;

        // Teinte « imagerie thermique » et cadre de l'appareil (charte MineNorth).
        g.fill(0, 0, w, h, 0x38200C48);
        MineNorthStyle.rfill(g, 6, 6, w - 6, 24, 8, 0xE0161048);
        g.drawString(font, "CAMÉRA THERMIQUE", 16, 11, MineNorthStyle.CYAN, false);
        List<int[]> pts = System.currentTimeMillis() - receivedAt > 2500 ? List.of() : points;
        int max = 0;
        for (int[] p : pts) max = Math.max(max, temperature(p));
        String right = pts.isEmpty() ? "Aucun point chaud" : "MAX " + max + " °C";
        g.drawString(font, right, w - 16 - font.width(right), 11, pts.isEmpty() ? MineNorthStyle.MUTED : 0xFFFFB347, false);

        int cx = w / 2, cy = h / 2;
        g.fill(cx - 9, cy, cx - 3, cy + 1, 0xC0FFFFFF);
        g.fill(cx + 4, cy, cx + 10, cy + 1, 0xC0FFFFFF);
        g.fill(cx, cy - 9, cx + 1, cy - 3, 0xC0FFFFFF);
        g.fill(cx, cy + 4, cx + 1, cy + 10, 0xC0FFFFFF);
        if (!haveMatrices || pts.isEmpty()) return;

        int[] aimed = null;
        double aimedD = 30 * 30;
        for (int[] p : pts) {
            double dy = p[3] == 2 ? 1.0 : p[3] == 1 ? 0.15 : 0.45;
            double wx = p[0] + 0.5 - cam.x, wy = p[1] + dy - cam.y, wz = p[2] + 0.5 - cam.z;
            Vector4f v = new Vector4f((float) wx, (float) wy, (float) wz, 1f);
            v.mul(VIEW).mul(PROJ);
            double dist = Math.sqrt(wx * wx + wy * wy + wz * wz);
            boolean behind = v.w <= 0.05f;
            float sx, sy;
            if (behind) { sx = -1; sy = -1; } else { sx = (v.x / v.w * 0.5f + 0.5f) * w; sy = (0.5f - v.y / v.w * 0.5f) * h; }
            if (behind || sx < 8 || sx > w - 8 || sy < 30 || sy > h - 8) {
                edge(g, font, p, sx, sy, behind, w, h, dist);
                continue;
            }
            int r = (int) Math.max(4, Math.min(18, 70 / Math.max(1, dist)));
            blob(g, (int) sx, (int) sy, r, p[3]);
            double ad = (sx - cx) * (sx - cx) + (sy - cy) * (sy - cy);
            if (ad < aimedD) { aimedD = ad; aimed = new int[]{p[0], p[1], p[2], p[3], (int) sx, (int) sy, (int) dist}; }
        }
        if (aimed != null) {
            String what = aimed[3] == 0 ? "Foyer" : aimed[3] == 1 ? "Braise cachée" : "Victime";
            String txt = what + " · " + temperature(aimed) + " °C · " + aimed[6] + " m";
            int tw = font.width(txt) + 12;
            MineNorthStyle.rfill(g, aimed[4] - tw / 2, aimed[5] + 20, aimed[4] + tw / 2, aimed[5] + 34, 6, 0xD80E0A34);
            g.drawString(font, txt, aimed[4] - tw / 2 + 6, aimed[5] + 23, aimed[3] == 2 ? MineNorthStyle.CYAN : 0xFFFFC56B, false);
        }
    }

    /** Tache de chaleur : halo, cœur et centre blanc (foyer) ; plus petite et rouge sombre (braise) ; contour cyan (victime). */
    private static void blob(GuiGraphics g, int x, int y, int r, int type) {
        if (type == 2) {
            MineNorthStyle.rfill(g, x - r - 2, y - r - 2, x + r + 2, y + r + 2, r + 2, 0xB020AAEB);
            MineNorthStyle.rfill(g, x - r, y - r, x + r, y + r, r, 0xE0FFD25A);
            MineNorthStyle.rfill(g, x - r / 2, y - r / 2, x + r / 2, y + r / 2, r / 2, 0xFFFFF4C8);
            return;
        }
        if (type == 1) r = Math.max(3, r * 2 / 3);
        int outer = type == 1 ? 0x70B4280A : 0x80FF3C00, mid = type == 1 ? 0xB0E0641E : 0xC0FF9A00, core = type == 1 ? 0xE0FFB040 : 0xFFFFF6C0;
        MineNorthStyle.rfill(g, x - r * 3 / 2, y - r * 3 / 2, x + r * 3 / 2, y + r * 3 / 2, r * 3 / 2, outer);
        MineNorthStyle.rfill(g, x - r, y - r, x + r, y + r, r, mid);
        MineNorthStyle.rfill(g, x - r / 2, y - r / 2, x + r / 2, y + r / 2, Math.max(1, r / 2), core);
    }

    /** Point hors champ : pastille au bord de l'écran, dans sa direction, avec la distance. */
    private static void edge(GuiGraphics g, Font font, int[] p, float sx, float sy, boolean behind, int w, int h, double dist) {
        float cx = w / 2f, cy = h / 2f;
        float dx = behind ? cx - sx : sx - cx, dy = behind ? cy - sy : sy - cy;
        if (behind && dx == 0 && dy == 0) dy = 1;
        float k = Math.min((w / 2f - 12) / Math.max(1e-3f, Math.abs(dx)), (h / 2f - 34) / Math.max(1e-3f, Math.abs(dy)));
        int x = (int) (cx + dx * k), y = (int) (cy + dy * k + 10);
        int col = p[3] == 2 ? MineNorthStyle.CYAN : p[3] == 1 ? 0xFFE0641E : 0xFFFF7A1A;
        MineNorthStyle.rfill(g, x - 5, y - 5, x + 5, y + 5, 5, col);
        String d = (int) dist + " m";
        g.drawString(font, d, Math.max(2, Math.min(w - font.width(d) - 2, x - font.width(d) / 2)), y + 7, 0xFFFFFFFF, true);
    }

    /** Masque ARI porté : bords assombris (champ de vision du masque) et témoin « ARI ». */
    private static void maskFrame(GuiGraphics g, Font font, int w, int h) {
        int b = Math.max(10, Math.min(w, h) / 14);
        for (int i = 0; i < b; i++) {
            int a = (int) (0x70 * (1 - i / (double) b));
            int c = a << 24 | 0x05050A;
            g.fill(0, i, w, i + 1, c);
            g.fill(0, h - i - 1, w, h - i, c);
            g.fill(i, 0, i + 1, h, c);
            g.fill(w - i - 1, 0, w - i, h, c);
        }
        String t = "ARI";
        int x = w - font.width(t) - 26, y = h - 40;
        MineNorthStyle.rfill(g, x - 6, y - 4, w - 8, y + 12, 7, 0xD0161048);
        MineNorthStyle.rfill(g, w - 20, y, w - 12, y + 8, 4, MineNorthStyle.GREEN);
        g.drawString(font, t, x, y, MineNorthStyle.TEXT, false);
    }
}
