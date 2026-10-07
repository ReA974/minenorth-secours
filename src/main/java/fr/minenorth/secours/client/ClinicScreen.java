package fr.minenorth.secours.client;

import fr.minenorth.secours.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Menu du PNJ de soins : diagnostic, prix, durée. Le soin n'est jamais instantané. */
public class ClinicScreen extends Screen {
    private static final int W = 320, H = 176;
    private static final String[] NAMES = {"Aucune blessure", "Blessure légère", "Blessure moyenne", "Blessure grave"};
    private final ModNetwork.ClinicPacket p;
    private int left, top;

    public ClinicScreen(ModNetwork.ClinicPacket p) {
        super(Component.literal("Soins"));
        this.p = p;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        addRenderableWidget(new MineNorthButton(left + W - 100, top + 10, 86, 16, Component.literal("Fermer"), MineNorthButton.GHOST, this::onClose));
        String label = p.price() > 0 ? "SE FAIRE SOIGNER • " + MineNorthStyle.euros(p.price()) : "SE FAIRE SOIGNER";
        addRenderableWidget(new MineNorthButton(left + 14, top + H - 44, W - 28, 22, Component.literal(label), MineNorthStyle.GREEN, () -> {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(ModNetwork.A_CLINIC_PAY, ModNetwork.NONE, "", 0));
            paid = true;
            onClose();
        })).enabled(p.allowed());
    }

    private boolean paid;

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, "CENTRE DE SOINS", "MINENORTH RP • HÔPITAL");
        int x = left + 14, y = top + 52;
        MineNorthStyle.card(g, x, y, W - 28, 62, false, p.level() >= 3 ? MineNorthStyle.ALERT : p.level() == 2 ? MineNorthStyle.WARN : MineNorthStyle.CYAN);
        int level = Math.max(0, Math.min(3, p.level()));
        g.drawString(font, "Diagnostic :", x + 10, y + 8, MineNorthStyle.BLUE, false);
        g.drawString(font, NAMES[level] + (p.bleeding() ? " + hémorragie" : ""), x + 80, y + 8, MineNorthStyle.WHITE, false);
        if (p.seconds() > 0) {
            g.drawString(font, "Durée des soins :", x + 10, y + 24, MineNorthStyle.BLUE, false);
            g.drawString(font, p.seconds() + " secondes, en restant près du médecin", x + 104, y + 24, MineNorthStyle.TEXT, false);
            g.drawString(font, "Prix :", x + 10, y + 40, MineNorthStyle.BLUE, false);
            g.drawString(font, p.price() > 0 ? MineNorthStyle.euros(p.price()) + " par carte bancaire" : "gratuit", x + 44, y + 40, MineNorthStyle.TEXT, false);
        }
        if (!p.reason().isEmpty()) g.drawString(font, font.plainSubstrByWidth(p.reason(), W - 28), x, top + H - 14, MineNorthStyle.WARN, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public void removed() {
        // Fermé sans payer : le serveur referme la session du PNJ.
        if (!paid) ModNetwork.CHANNEL.sendToServer(new ModNetwork.ActionPacket(ModNetwork.A_CLOSE, ModNetwork.NONE, "", 0));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
