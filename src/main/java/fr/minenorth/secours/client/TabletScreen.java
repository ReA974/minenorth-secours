package fr.minenorth.secours.client;

import fr.minenorth.secours.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tablette des secours : alertes (joueurs inconscients) et effectifs. */
public class TabletScreen extends Screen {
    private static final int W = 420, H = 240, ROWS = 6;
    private static final String[] GRADES = {"Chef des secours", "Médecin", "Secouriste"};

    private ModNetwork.TabletPacket v;
    private int left, top, page;
    private String message = "";
    private boolean messageOk = true;
    private long receivedAt = System.currentTimeMillis();
    private EditBox bName, bSearch;
    private String kName = "", kSearch = "";

    private record Label(String text, int x, int y, int color) {}
    private record Card(int x, int y, int w, int h, int accent) {}
    private final List<Label> labels = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();

    public TabletScreen(ModNetwork.TabletPacket v) {
        super(Component.literal("Secours"));
        this.v = v;
    }

    public void update(ModNetwork.TabletPacket n) {
        if (bName != null) kName = bName.getValue();
        if (bSearch != null) kSearch = bSearch.getValue();
        if (n.view() != v.view() || n.view() == ModNetwork.V_FILE) page = 0;
        this.v = n;
        this.message = n.message();
        this.messageOk = n.ok();
        this.receivedAt = System.currentTimeMillis();
        if (n.ok() && !n.message().isEmpty()) kName = "";
        clearWidgets();
        init();
    }

    private void send(int action, UUID target, String a, int n) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(action, target == null ? ModNetwork.NONE : target, a == null ? "" : a, n));
    }
    private MineNorthButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new MineNorthButton(x, y, w, h, Component.literal(label), color, r));
    }
    private void label(String text, int x, int y, int color) { labels.add(new Label(text, x, y, color)); }
    private void label(String text, int x, int y, int color, int maxWidth) { label(font.plainSubstrByWidth(text, maxWidth), x, y, color); }
    private void card(int x, int y, int w, int h, int accent) { cards.add(new Card(x, y, w, h, accent)); }
    private void flip(int delta) {
        if (bName != null) kName = bName.getValue();
        if (bSearch != null) kSearch = bSearch.getValue();
        page += delta; message = ""; clearWidgets(); init();
    }

    private void pager(int pages, int right, int y) {
        if (pages <= 1) return;
        btn(right - 78, y, 20, 18, "<", MineNorthStyle.DARK, () -> flip(-1)).enabled(page > 0);
        String p = (page + 1) + " / " + pages;
        label(p, right - 39 - font.width(p) / 2, y + 5, MineNorthStyle.TEXT);
        btn(right - 20, y, 20, 18, ">", MineNorthStyle.DARK, () -> flip(1)).enabled(page < pages - 1);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        labels.clear();
        cards.clear();
        bName = null;
        bSearch = null;
        int x = left + 14, w = W - 28;
        btn(left + W - 100, top + 10, 86, 16, "Fermer", MineNorthButton.GHOST, this::onClose);
        int view = v.view();
        btn(x, top + 46, 76, 18, "ALERTES", view == ModNetwork.V_ALERTS ? MineNorthStyle.CYAN : MineNorthStyle.DARK, () -> send(ModNetwork.A_ALERTS, null, "", 0));
        btn(x + 80, top + 46, 76, 18, "DOSSIERS", view == ModNetwork.V_FILE ? MineNorthStyle.CYAN : MineNorthStyle.DARK, () -> send(ModNetwork.A_FILE, null, "", 0));
        if (v.grade() == 0) btn(x + 160, top + 46, 76, 18, "EFFECTIFS", view == ModNetwork.V_ROSTER ? MineNorthStyle.CYAN : MineNorthStyle.DARK, () -> send(ModNetwork.A_ROSTER, null, "", 0));
        // Prise de service : sans elle, pas d'alertes et pas de soins.
        btn(x + w - 150, top + 46, 150, 18, v.onDuty() ? "EN SERVICE • QUITTER" : "PRENDRE MON SERVICE",
                v.onDuty() ? MineNorthStyle.GREEN : MineNorthStyle.PINK, () -> send(ModNetwork.A_DUTY, null, "", 0));
        if (view == ModNetwork.V_FILE) buildFile(x, w);
        else if (view == ModNetwork.V_ROSTER) buildRoster(x, w);
        else buildAlerts(x, w);
    }

    private void buildAlerts(int x, int w) {
        List<ModNetwork.Alert> list = v.alerts();
        int y0 = top + 72, rows = ROWS - 1;
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(pages - 1, page));
        if (!v.onDuty()) label("Vous êtes hors service : vous ne recevez pas les alertes et ne pouvez pas soigner.", x, top + H - 50, MineNorthStyle.WARN, w);
        if (list.isEmpty()) label("Aucune alerte : personne n'est inconscient.", x, y0 + 6, MineNorthStyle.OK, w);
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= list.size()) break;
            ModNetwork.Alert a = list.get(idx);
            int y = y0 + i * 26;
            int color = a.coma() ? MineNorthStyle.ALERT : MineNorthStyle.WARN;
            card(x, y, w, 24, color);
            label(a.name(), x + 8, y + 3, MineNorthStyle.WHITE, 150);
            String state = a.coma() ? "INCONSCIENT • hôpital dans " + ClientState.clock(a.secondsLeft()) : "BLESSURE GRAVE";
            String shot = a.zones() != 0 ? " • balle : " + fr.minenorth.secours.compat.TaczCompat.describe(a.zones()) : "";
            label(state + shot + (a.bleeding() ? " • hémorragie" : ""), x + 8, y + 14, color, 290);
            String where = a.x() + " " + a.y() + " " + a.z() + (a.distance() >= 0 ? "  (" + a.distance() + " m)" : "  (autre dimension)");
            label(where, x + 164, y + 3, MineNorthStyle.TEXT, 130);
            if (a.dispatch().isEmpty()) {
                btn(x + w - 82, y + 4, 78, 16, "J'Y VAIS", MineNorthStyle.GREEN, () -> send(ModNetwork.A_DISPATCH, a.id(), "", 0));
            } else {
                label("En route :", x + w - 110, y + 3, MineNorthStyle.MUTED);
                label(a.dispatch(), x + w - 110, y + 14, MineNorthStyle.OK, 104);
            }
        }
        int yb = top + H - 36;
        btn(x, yb, 110, 18, "ACTUALISER", MineNorthStyle.DARK, () -> send(ModNetwork.A_ALERTS, null, "", 0));
        pager(pages, x + w, yb);
    }

    /** Dossier médical d'un citoyen : recherche par nom, puis historique. */
    private void buildFile(int x, int w) {
        int y0 = top + 72;
        bSearch = new EditBox(font, x, y0, 200, 18, Component.literal("Recherche"));
        bSearch.setMaxLength(32);
        bSearch.setHint(Component.literal("Nom ou prénom du citoyen"));
        bSearch.setValue(kSearch);
        addRenderableWidget(bSearch);
        btn(x + 206, y0 - 1, 96, 20, "RECHERCHER", MineNorthStyle.CYAN, () -> send(ModNetwork.A_FILE, null, bSearch.getValue(), 0));
        if (v.fileName().isEmpty()) { label("Cherchez un citoyen pour voir son dossier médical.", x, y0 + 30, MineNorthStyle.MUTED); return; }
        label(v.fileName(), x, y0 + 26, MineNorthStyle.WHITE);
        List<String> lines = v.fileLines();
        int rows = 8, pages = Math.max(1, (lines.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(pages - 1, page));
        if (lines.isEmpty()) label("Dossier vide : aucune blessure enregistrée.", x, y0 + 42, MineNorthStyle.OK);
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= lines.size()) break;
            String line = lines.get(idx);
            label(line, x, y0 + 42 + i * 12, line.startsWith("ÉTAT ACTUEL") ? MineNorthStyle.WARN : MineNorthStyle.TEXT, w);
        }
        pager(pages, x + w, top + H - 36);
    }

    private void buildRoster(int x, int w) {
        List<ModNetwork.Member> list = v.members();
        int y0 = top + 72, rows = ROWS;
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(pages - 1, page));
        var me = Minecraft.getInstance().player;
        UUID self = me == null ? ModNetwork.NONE : me.getUUID();
        if (list.isEmpty()) label("Aucun membre pour le moment.", x, y0 + 6, MineNorthStyle.MUTED);
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= list.size()) break;
            ModNetwork.Member m = list.get(idx);
            int y = y0 + i * 20;
            card(x, y, w, 18, m.grade() == 0 ? MineNorthStyle.WARN : m.online() ? MineNorthStyle.OK : MineNorthStyle.DARK);
            label(m.name(), x + 8, y + 5, m.online() ? MineNorthStyle.WHITE : MineNorthStyle.MUTED, 150);
            label(GRADES[Math.max(0, Math.min(2, m.grade()))], x + 170, y + 5, MineNorthStyle.TEXT);
            if (m.duty()) label("en service", x + 270, y + 5, MineNorthStyle.OK);
            if (!m.id().equals(self)) {
                btn(x + w - 62, y + 2, 16, 14, "+", MineNorthStyle.DARK, () -> send(ModNetwork.A_GRADE, m.id(), "", m.grade() - 1)).enabled(m.grade() > 0);
                btn(x + w - 44, y + 2, 16, 14, "-", MineNorthStyle.DARK, () -> send(ModNetwork.A_GRADE, m.id(), "", m.grade() + 1)).enabled(m.grade() < 2);
                btn(x + w - 22, y + 2, 20, 14, "X", MineNorthStyle.PINK, () -> send(ModNetwork.A_GRADE, m.id(), "", -1));
            }
        }
        int yb = top + H - 36;
        bName = new EditBox(font, x, yb, 150, 18, Component.literal("Nom"));
        bName.setMaxLength(32);
        bName.setHint(Component.literal("Prénom Nom ou pseudo"));
        bName.setValue(kName);
        addRenderableWidget(bName);
        btn(x + 156, yb - 1, 140, 20, "RECRUTER (SECOURISTE)", MineNorthStyle.GREEN, () -> send(ModNetwork.A_GRADE, null, bName.getValue(), 2));
        pager(pages, x + w, yb);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, "SECOURS", "MINENORTH RP • POMPIERS ET SAMU");
        for (Card c : cards) MineNorthStyle.card(g, c.x(), c.y(), c.w(), c.h(), false, c.accent());
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), l.color(), false);
        if (!message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, W - 28), left + 14, top + H - 13, messageOk ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
        }
        super.render(g, mx, my, pt);
    }

    /** Les alertes changent vite : la liste se recharge toute seule toutes les 5 secondes. */
    @Override
    public void tick() {
        super.tick();
        if (v.view() == ModNetwork.V_ALERTS && System.currentTimeMillis() - receivedAt > 5000) {
            receivedAt = System.currentTimeMillis();
            send(ModNetwork.A_ALERTS, null, "", 0);
        }
    }

    @Override
    public void removed() {
        send(ModNetwork.A_CLOSE, null, "", 0);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
