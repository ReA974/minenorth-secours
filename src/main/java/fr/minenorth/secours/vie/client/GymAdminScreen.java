package fr.minenorth.secours.vie.client;

import fr.minenorth.secours.client.MineNorthButton;
import fr.minenorth.secours.client.MineNorthStyle;
import fr.minenorth.secours.vie.GymAdmin;
import fr.minenorth.secours.vie.VieNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Écran de gestion d'une salle de sport (portique ou borne) pour le patron, les gérants et les opérateurs : entreprise liée, prix, durée, abonnés
 * (offrir / retirer). Charte arrondie MineNorth ; le serveur revérifie chaque action.
 */
public class GymAdminScreen extends Screen {
    private static final int W = 330, H = 284, ROWS = 5, ROW_H = 16;
    private VieNet.GymView v;
    private int left, top, scroll, nearIdx;
    private boolean dropOpen;
    private String priceTxt, daysTxt;
    private EditBox price, days;

    private GymAdminScreen(VieNet.GymView v) {
        super(Component.literal("Salle de sport"));
        this.v = v;
        this.priceTxt = euros(v.priceCents());
        this.daysTxt = String.valueOf(v.days());
    }

    /** Appelé par le paquet du serveur (thread client). */
    public static void receive(VieNet.GymView v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GymAdminScreen s && s.v.pos().equals(v.pos())) s.update(v);
        else mc.setScreen(new GymAdminScreen(v));
    }

    private static String euros(int cents) { return cents % 100 == 0 ? String.valueOf(cents / 100) : String.format("%d,%02d", cents / 100, cents % 100); }

    private void update(VieNet.GymView n) {
        boolean sameSettings = n.priceCents() == v.priceCents() && n.days() == v.days();
        v = n;
        if (!sameSettings) { priceTxt = euros(n.priceCents()); daysTxt = String.valueOf(n.days()); }
        scroll = Math.min(scroll, Math.max(0, v.subs().size() - ROWS));
        nearIdx = v.near().isEmpty() ? 0 : Math.min(nearIdx, v.near().size() - 1);
        rebuildWidgets();
    }

    private void send(int action, int a, int b, UUID u) {
        VieNet.CHANNEL.sendToServer(new VieNet.GymAction(v.pos(), action, a, b, u));
    }

    private int parsePriceCents() {
        try { return (int) Math.max(0, Math.min(10_000_000, Math.round(Double.parseDouble(price.getValue().trim().replace(',', '.')) * 100))); }
        catch (NumberFormatException e) { return v.priceCents(); }
    }

    private int parseDays() {
        try { return Math.max(1, Math.min(365, Integer.parseInt(days.getValue().trim()))); }
        catch (NumberFormatException e) { return v.days(); }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int x = left + 14;
        addRenderableWidget(new MineNorthButton(left + W - 100, top + 10, 86, 16, Component.literal("Fermer"), MineNorthButton.GHOST, this::onClose));
        addRenderableWidget(new MineNorthButton(left + W - 14 - 76, top + 66, 76, 18, Component.literal("Délier"), 0xFFB3263E, () -> send(GymAdmin.UNLINK, 0, 0, new UUID(0, 0)))
                .enabled(v.company() > 0));
        price = new EditBox(font, x, top + 112, 80, 16, Component.literal("Prix"));
        price.setMaxLength(10);
        price.setValue(priceTxt);
        price.setResponder(t -> priceTxt = t);
        addRenderableWidget(price);
        days = new EditBox(font, x + 100, top + 112, 56, 16, Component.literal("Jours"));
        days.setMaxLength(3);
        days.setValue(daysTxt);
        days.setResponder(t -> daysTxt = t);
        addRenderableWidget(days);
        addRenderableWidget(new MineNorthButton(left + W - 14 - 120, top + 108, 120, 20, Component.literal("Enregistrer"), MineNorthStyle.GREEN,
                () -> send(GymAdmin.SETTINGS, parsePriceCents(), parseDays(), new UUID(0, 0))));
        // liste des abonnés : un bouton « Retirer » par ligne visible
        List<VieNet.GymView.Sub> subs = v.subs();
        for (int i = 0; i < ROWS && scroll + i < subs.size(); i++) {
            UUID id = subs.get(scroll + i).id();
            addRenderableWidget(new MineNorthButton(left + W - 14 - 6 - 54, top + 156 + i * ROW_H, 54, 14, Component.literal("Retirer"), 0xFFB3263E,
                    () -> send(GymAdmin.REMOVE, 0, 0, id)));
        }
        // offrir un abonnement à un joueur proche
        boolean any = !v.near().isEmpty();
        String who = any ? v.near().get(nearIdx).name() : "aucun joueur proche";
        addRenderableWidget(new MineNorthButton(x, top + 256, 140, 18, Component.literal("Joueur : " + font.plainSubstrByWidth(who, 84)), MineNorthStyle.DARK, () -> {
            if (v.near().size() > 1) { nearIdx = (nearIdx + 1) % v.near().size(); rebuildWidgets(); }
        }).enabled(any));
        addRenderableWidget(new MineNorthButton(x + 148, top + 256, W - 28 - 148, 18, Component.literal("Offrir un abonnement"), MineNorthStyle.GREEN,
                () -> send(GymAdmin.OFFER, 0, parseDays(), v.near().get(nearIdx).id())).enabled(any && v.company() > 0));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = left + 14, y = top + 66, w = W - 28 - 84;
        boolean inButton = mx >= x && mx < x + w && my >= y && my < y + 18;
        if (dropOpen) {
            List<VieNet.GymView.Company> cs = v.companies();
            for (int i = 0; i < cs.size(); i++) {
                int ry = y + 20 + i * 16;
                if (mx >= x && mx < x + w && my >= ry && my < ry + 16) {
                    dropOpen = false;
                    send(GymAdmin.LINK, cs.get(i).id(), 0, new UUID(0, 0));
                    return true;
                }
            }
            dropOpen = false;
            if (inButton) return true;
        } else if (inButton && !v.companies().isEmpty()) {
            dropOpen = true;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, v.subs().size() - ROWS);
        int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (ns != scroll) { scroll = ns; rebuildWidgets(); }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, "SALLE DE SPORT", "MINENORTH RP • GESTION DE LA SALLE");
        int x = left + 14;
        g.drawString(font, "Entreprise liée", x, top + 52, MineNorthStyle.BLUE, false);
        int dw = W - 28 - 84;
        boolean hov = mx >= x && mx < x + dw && my >= top + 66 && my < top + 84;
        MineNorthStyle.rfill(g, x, top + 66, x + dw, top + 84, 5, hov ? MineNorthStyle.HOVER : MineNorthStyle.LIST);
        String cur = v.company() > 0 ? v.companyName() : "Aucune (le portique est ouvert à tous)";
        g.drawString(font, font.plainSubstrByWidth(cur, dw - 22), x + 6, top + 71, v.company() > 0 ? MineNorthStyle.WHITE : MineNorthStyle.WARN, false);
        g.drawString(font, dropOpen ? "▲" : "▼", x + dw - 12, top + 71, MineNorthStyle.TEXT, false);
        g.drawString(font, "Prix de l'abonnement (€)", x, top + 98, MineNorthStyle.BLUE, false);
        g.drawString(font, "Durée (jours)", x + 100, top + 98, MineNorthStyle.BLUE, false);
        // liste des abonnés
        g.drawString(font, "Abonnés (" + v.subs().size() + ")", x, top + 140, MineNorthStyle.BLUE, false);
        MineNorthStyle.card(g, x, top + 152, W - 28, ROWS * ROW_H + 4, false, MineNorthStyle.CYAN);
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        List<VieNet.GymView.Sub> subs = v.subs();
        if (subs.isEmpty()) g.drawString(font, v.company() > 0 ? "Aucun abonné pour le moment." : "Lie d'abord la salle à une entreprise.", x + 10, top + 158, MineNorthStyle.MUTED, false);
        for (int i = 0; i < ROWS && scroll + i < subs.size(); i++) {
            VieNet.GymView.Sub s = subs.get(scroll + i);
            int ry = top + 156 + i * ROW_H;
            g.drawString(font, font.plainSubstrByWidth(s.name(), 120), x + 10, ry + 3, MineNorthStyle.WHITE, false);
            g.drawString(font, "jusqu'au " + df.format(new Date(s.end())), x + 140, ry + 3, MineNorthStyle.TEXT, false);
        }
        if (subs.size() > ROWS) g.drawString(font, "molette : défiler", x + W - 28 - 82, top + 140, MineNorthStyle.MUTED, false);
        if (!v.msg().isEmpty()) g.drawString(font, font.plainSubstrByWidth(v.msg().replaceAll("§.", ""), W - 28), x, top + 241, v.msg().startsWith("§c") ? MineNorthStyle.ALERT : MineNorthStyle.OK, false);
        super.render(g, mx, my, pt);
        if (dropOpen) {   // liste déroulante par-dessus le reste
            List<VieNet.GymView.Company> cs = v.companies();
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            MineNorthStyle.rfill(g, x - 1, top + 85, x + dw + 1, top + 87 + cs.size() * 16, 5, MineNorthStyle.FRAME);
            MineNorthStyle.rfill(g, x, top + 86, x + dw, top + 86 + cs.size() * 16, 4, MineNorthStyle.PANEL);
            for (int i = 0; i < cs.size(); i++) {
                int ry = top + 86 + i * 16;
                boolean h = mx >= x && mx < x + dw && my >= ry && my < ry + 16;
                if (h) MineNorthStyle.rfill(g, x + 1, ry, x + dw - 1, ry + 16, 3, MineNorthStyle.HOVER);
                g.drawString(font, font.plainSubstrByWidth(cs.get(i).name(), dw - 12), x + 6, ry + 4, cs.get(i).id() == v.company() ? MineNorthStyle.OK : MineNorthStyle.WHITE, false);
            }
            g.pose().popPose();
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
