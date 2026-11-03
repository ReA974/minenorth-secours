package fr.minenorth.secours.client.phone;

import com.mikasrevs.newphone.client.gui.PhoneModernText;
import fr.minenorth.secours.client.ClientState;
import fr.minenorth.secours.compat.TaczCompat;
import fr.minenorth.secours.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application « Secours » (pompiers et SAMU) dessinée dans l'écran du téléphone. Mêmes fonctions que la tablette
 * ({@link fr.minenorth.secours.client.TabletScreen}) : alertes (blessés, incendies, appels du 18), dossiers médicaux, effectifs (chef),
 * et un onglet Service (prise de service, réponse au 18 sur le téléphone). Le serveur revérifie chaque action. Mise en page portrait.
 */
public class PhoneSecoursScreen extends PhoneAppBase {
    private static final String[] GRADES = {"Chef des secours", "Médecin", "Secouriste"};
    private static final int SERVICE = -1;

    private ModNetwork.TabletPacket v;
    private String message = "";
    private boolean messageOk = true;
    private long receivedAt = System.currentTimeMillis();
    private boolean service;
    private final Field search = new Field("Nom ou prénom du citoyen", "", 32), name = new Field("Prénom Nom ou pseudo", "", 32);

    public PhoneSecoursScreen(ModNetwork.TabletPacket v) {
        super("Secours");
        this.v = v;
        this.message = v.message();
        this.messageOk = v.ok();
    }

    public void update(ModNetwork.TabletPacket n) {
        if (n.view() != v.view()) { service = false; resetScroll(); }
        if (n.view() == ModNetwork.V_FILE) resetScroll();
        this.v = n;
        this.message = n.message();
        this.messageOk = n.ok();
        this.receivedAt = System.currentTimeMillis();
        if (n.ok() && !n.message().isEmpty()) name.value = "";
    }

    private void send(int action, UUID target, String a, int n) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(action, target == null ? ModNetwork.NONE : target, a == null ? "" : a, n));
    }

    // ------------------------------------------------------------------ onglets

    private record Tab(String label, int view) {}

    private List<Tab> tabList() {
        List<Tab> t = new ArrayList<>();
        t.add(new Tab(v.view() == ModNetwork.V_ALERTS && !v.alerts().isEmpty() ? "Alertes (" + v.alerts().size() + ")" : "Alertes", ModNetwork.V_ALERTS));
        t.add(new Tab("Dossiers", ModNetwork.V_FILE));
        if (v.grade() == 0) t.add(new Tab("Effectifs", ModNetwork.V_ROSTER));
        t.add(new Tab("Service", SERVICE));
        return t;
    }

    @Override protected String title() { return "Secours"; }

    @Override
    protected String[] tabs() {
        List<Tab> t = tabList();
        String[] out = new String[t.size()];
        for (int i = 0; i < out.length; i++) out[i] = t.get(i).label();
        return out;
    }

    @Override
    protected void tabChanged() {
        List<Tab> t = tabList();
        if (tab < 0 || tab >= t.size()) return;
        message = "";
        int view = t.get(tab).view();
        service = view == SERVICE;
        switch (view) {
            case ModNetwork.V_ALERTS -> send(ModNetwork.A_ALERTS, null, "", 0);
            case ModNetwork.V_FILE -> send(ModNetwork.A_FILE, null, "", 0);
            case ModNetwork.V_ROSTER -> send(ModNetwork.A_ROSTER, null, "", 0);
            default -> {}
        }
    }

    private int currentTab() {
        List<Tab> t = tabList();
        int want = service ? SERVICE : v.view();
        for (int i = 0; i < t.size(); i++) if (t.get(i).view() == want) return i;
        return 0;
    }

    @Override protected String badge() { return GRADES[Math.max(0, Math.min(2, v.grade()))] + (v.onDuty() ? " · service" : ""); }

    @Override protected int badgeColor() { return v.onDuty() ? GREEN : DARK; }

    @Override protected String message() { return message; }

    @Override protected boolean messageError() { return !messageOk; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        tab = currentTab();
        super.render(g, mx, my, pt);
    }

    @Override
    protected float content(GuiGraphics g, int mx, int my, float y) {
        if (service) return service(g, mx, my, y);
        return switch (v.view()) {
            case ModNetwork.V_FILE -> file(g, mx, my, y);
            case ModNetwork.V_ROSTER -> roster(g, mx, my, y);
            default -> alerts(g, mx, my, y);
        };
    }

    private float section(GuiGraphics g, String title, float y) {
        text(g, title, L, y, 9, R - L, CYAN);
        return y + 16;
    }

    private void card(GuiGraphics g, float t, float b, int accent) {
        box(g, L, t, R, b, 12, CARD);
        box(g, L + 3, t + 6, L + 6, b - 6, 1.5f, accent);
    }

    private int lines(String s, float sz, float w) { return Math.max(1, PhoneModernText.wrap(s, size(sz), size(w)).size()); }

    // ------------------------------------------------------------------ alertes

    private float alerts(GuiGraphics g, int mx, int my, float y) {
        button(g, mx, my, L, y, L + 100, y + 22, "Actualiser", 9, DARK, true, () -> send(ModNetwork.A_ALERTS, null, "", 0));
        if (!v.onDuty()) wrap(g, "Hors service : pas d'alertes ni de soins.", L + 108, y + 1, 8, R - L - 108, WARN);
        y += 30;
        if (v.alerts().isEmpty()) return wrap(g, "Aucune alerte : personne n'est inconscient, aucun incendie, aucun appel d'urgence.", L, y, 9.5f, R - L, OK);
        for (ModNetwork.Alert a : v.alerts()) {
            boolean fire = a.kind() == ModNetwork.Alert.FIRE, call = a.kind() == ModNetwork.Alert.CALL;
            int color = call ? 0xFF4FC3F7 : fire ? 0xFFFF8A33 : a.coma() ? ALERT : WARN;
            String state = call ? "APPEL 18 · il y a " + ClientState.clock(a.secondsLeft()) + (a.info().isBlank() ? "" : " · " + a.info())
                    : fire ? "INCENDIE · à maîtriser avant " + ClientState.clock(a.secondsLeft())
                    : a.coma() ? "INCONSCIENT · hôpital dans " + ClientState.clock(a.secondsLeft()) : "BLESSURE GRAVE";
            state += (a.zones() != 0 ? " · balle : " + TaczCompat.describe(a.zones()) : "") + (a.bleeding() ? " · hémorragie" : "");
            boolean go = call || fire || a.dispatch().isEmpty();
            String enRoute = !call && !a.dispatch().isEmpty() ? "En route : " + a.dispatch() : "";
            float h = 36 + lines(state, 9, R - L - 24) * 12 + (enRoute.isEmpty() ? 0 : 13) + (go ? 28 : 4);
            card(g, y, y + h, color);
            text(g, a.name(), L + 12, y + 7, 10.5f, R - L - 24, WHITE);
            text(g, a.x() + " " + a.y() + " " + a.z() + (a.distance() >= 0 ? "  (" + a.distance() + " m)" : "  (autre dimension)"), L + 12, y + 21, 9, R - L - 24, TEXT);
            float yy = wrap(g, state, L + 12, y + 35, 9, R - L - 24, color);
            if (!enRoute.isEmpty()) { text(g, enRoute, L + 12, yy, 9, R - L - 24, OK); yy += 13; }
            if (go) button(g, mx, my, L + 12, yy + 2, R - 8, yy + 24, call ? "Guider" : "J'y vais", 9.5f, GREEN, true,
                    () -> send(ModNetwork.A_DISPATCH, a.id(), "", 0));
            y += h + 5;
        }
        return y;
    }

    // ------------------------------------------------------------------ dossiers médicaux

    private float file(GuiGraphics g, int mx, int my, float y) {
        field(g, mx, my, search, L, y, R - 92);
        button(g, mx, my, R - 86, y, R, y + 22, "Rechercher", 9, CYAN, true, () -> send(ModNetwork.A_FILE, null, search.value, 0));
        y += 32;
        if (v.fileName().isEmpty()) return wrap(g, "Cherchez un citoyen pour voir son dossier médical.", L, y, 9.5f, R - L, MUTED);
        text(g, v.fileName(), L, y, 12, R - L, WHITE);
        y += 20;
        if (v.fileLines().isEmpty()) return wrap(g, "Dossier vide : aucune blessure enregistrée.", L, y, 9.5f, R - L, OK);
        for (String line : v.fileLines()) y = wrap(g, line, L, y, 9, R - L, line.startsWith("ÉTAT ACTUEL") ? WARN : TEXT) + 3;
        return y;
    }

    // ------------------------------------------------------------------ effectifs (chef)

    private float roster(GuiGraphics g, int mx, int my, float y) {
        var me = Minecraft.getInstance().player;
        UUID self = me == null ? ModNetwork.NONE : me.getUUID();
        if (v.members().isEmpty()) y = wrap(g, "Aucun membre pour le moment.", L, y, 9.5f, R - L, MUTED) + 4;
        for (ModNetwork.Member m : v.members()) {
            card(g, y, y + 36, m.grade() == 0 ? WARN : m.online() ? OK : DARK);
            text(g, m.name(), L + 12, y + 6, 10, R - L - 110, m.online() ? WHITE : MUTED);
            text(g, GRADES[Math.max(0, Math.min(2, m.grade()))] + (m.duty() ? " · en service" : ""), L + 12, y + 20, 8.5f, R - L - 110, m.duty() ? OK : TEXT);
            if (!m.id().equals(self)) {
                button(g, mx, my, R - 86, y + 7, R - 62, y + 29, "+", 10, DARK, m.grade() > 0, () -> send(ModNetwork.A_GRADE, m.id(), "", m.grade() - 1));
                button(g, mx, my, R - 58, y + 7, R - 34, y + 29, "-", 10, DARK, m.grade() < 2, () -> send(ModNetwork.A_GRADE, m.id(), "", m.grade() + 1));
                button(g, mx, my, R - 30, y + 7, R - 6, y + 29, "X", 9, PINK, true, () -> send(ModNetwork.A_GRADE, m.id(), "", -1));
            }
            y += 41;
        }
        y = section(g, "RECRUTER", y + 4);
        field(g, mx, my, name, L, y, R);
        button(g, mx, my, L, y + 28, R, y + 52, "Recruter un secouriste", 9.5f, GREEN, !name.value.isBlank(), () -> send(ModNetwork.A_GRADE, null, name.value, 2));
        return y + 60;
    }

    // ------------------------------------------------------------------ service

    private float service(GuiGraphics g, int mx, int my, float y) {
        y = section(g, "SERVICE", y);
        box(g, L, y, R, y + 46, 14, CARD);
        text(g, "Grade : " + GRADES[Math.max(0, Math.min(2, v.grade()))], L + 12, y + 9, 10, R - L - 24, WHITE);
        text(g, v.onDuty() ? "En service" : "Hors service : pas d'alertes ni de soins", L + 12, y + 26, 8.5f, R - L - 24, v.onDuty() ? OK : WARN);
        y += 54;
        button(g, mx, my, L, y, R, y + 26, v.onDuty() ? "Quitter mon poste" : "Prendre mon service", 10, v.onDuty() ? RED : GREEN, true,
                () -> send(ModNetwork.A_DUTY, null, "", 0));
        y += 32;
        if (v.onDuty() && v.phone() > 0) {
            boolean on = v.phone() == 2;
            button(g, mx, my, L, y, R, y + 26, on ? "Répondre au 18 sur mon téléphone : oui" : "Répondre au 18 sur mon téléphone : non", 9.5f,
                    on ? GREEN : DARK, true, () -> send(ModNetwork.A_PHONE, null, "", 0));
            y += 32;
        }
        return y;
    }

    // ------------------------------------------------------------------ cycle de vie

    /** Les alertes changent vite : la liste se recharge toute seule toutes les 5 secondes. */
    @Override
    public void tick() {
        super.tick();
        if (!service && v.view() == ModNetwork.V_ALERTS && System.currentTimeMillis() - receivedAt > 5000) {
            receivedAt = System.currentTimeMillis();
            send(ModNetwork.A_ALERTS, null, "", 0);
        }
    }

    @Override
    public void removed() {
        send(ModNetwork.A_CLOSE, null, "", 0);
        super.removed();
    }
}
