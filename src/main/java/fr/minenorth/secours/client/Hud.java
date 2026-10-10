package fr.minenorth.secours.client;

import fr.minenorth.secours.MineNorthSecours;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Affichage à l'écran : blessure en cours, hémorragie, soins au PNJ, et l'écran « inconscient ». */
@Mod.EventBusSubscriber(modid = MineNorthSecours.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class Hud {
    private Hud() {}

    private static final String[] NAMES = {"", "Blessure légère", "Blessure moyenne", "Blessure grave"};
    private static final int[] COLORS = {0, MineNorthStyle.WARN, 0xFFFFA64D, MineNorthStyle.ALERT};

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("blessures", (gui, g, partialTick, w, h) -> render(g, w, h));
    }

    private static void centered(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, true);
    }

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;

        if (ClientState.coma) {
            g.fill(0, 0, w, h, 0xB0000000);
            int y = h / 2 - 34;
            g.pose().pushPose();
            g.pose().scale(2f, 2f, 1f);
            centered(g, font, "VOUS ÊTES INCONSCIENT", w / 4, y / 2, MineNorthStyle.ALERT);
            g.pose().popPose();
            if (ClientState.rescuers) {
                centered(g, font, "Un pompier ou le SAMU doit venir vous réanimer.", w / 2, y + 26, MineNorthStyle.TEXT);
                if (ClientState.dispatch.isEmpty()) centered(g, font, "En attente d'une unité de secours…", w / 2, y + 42, MineNorthStyle.MUTED);
                else centered(g, font, "Unité en route : " + ClientState.dispatch, w / 2, y + 42, MineNorthStyle.OK);
            } else {
                centered(g, font, "Aucun secours en service.", w / 2, y + 26, MineNorthStyle.TEXT);
                centered(g, font, "Appuyez sur [" + ClientKeys.wakeKeyName() + "] pour vous réveiller à l'hôpital.", w / 2, y + 42, MineNorthStyle.OK);
            }
            centered(g, font, "Réveil à l'hôpital dans " + ClientState.clock(ClientState.comaLeft()), w / 2, y + 58, MineNorthStyle.WARN);
            return;
        }

        int x = 6, y = 6;
        if (ClientState.level > 0 && ClientState.level < NAMES.length) {
            int heal = ClientState.healLeft();
            String text = NAMES[ClientState.level] + (ClientState.level == 3 ? " — secours nécessaires" : heal > 0 ? " — guérison dans " + ClientState.clock(heal) : "");
            g.fill(x, y, x + 3, y + 10, COLORS[ClientState.level]);
            g.drawString(font, text, x + 7, y + 1, COLORS[ClientState.level], true);
            y += 12;
        }
        if (ClientState.zones != 0) {
            g.fill(x, y, x + 3, y + 10, MineNorthStyle.ALERT);
            g.drawString(font, "Touché par balle : " + fr.minenorth.secours.compat.TaczCompat.describe(ClientState.zones), x + 7, y + 1, MineNorthStyle.ALERT, true);
            y += 12;
        }
        if (ClientState.bleeding) {
            g.fill(x, y, x + 3, y + 10, MineNorthStyle.ALERT);
            g.drawString(font, "Hémorragie — il faut un bandage", x + 7, y + 1, MineNorthStyle.ALERT, true);
            y += 12;
        }
        int care = ClientState.careLeft();
        if (care > 0) {
            g.fill(x, y, x + 3, y + 10, MineNorthStyle.CYAN);
            g.drawString(font, "Soins en cours " + ClientState.clock(care) + " — ne vous éloignez pas", x + 7, y + 1, MineNorthStyle.CYAN, true);
        }
    }
}
