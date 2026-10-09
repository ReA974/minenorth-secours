package fr.minenorth.secours.client;

import fr.minenorth.secours.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    public static void state(ModNetwork.StatePacket p) {
        boolean wasComa = ClientState.coma;
        ClientState.set(p.level(), p.healSeconds(), p.bleeding(), p.coma(), p.comaSeconds(), p.dispatch(), p.careSeconds(), p.zones());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Pose pose = null;
        if (p.coma()) {
            // Pose vide : le serveur demande le rendu couché du mod (ComaRender), pas une pose Minecraft.
            if (!p.pose().isEmpty()) { try { pose = Pose.valueOf(p.pose()); } catch (RuntimeException e) { pose = null; } }
            // Au moment où il tombe, on ferme l'écran ouvert (inventaire, tablette…). Le chat reste utilisable ensuite.
            if (!wasComa && mc.screen != null) mc.setScreen(null);
        }
        mc.player.setForcedPose(pose);
    }

    public static void comaList(ModNetwork.ComaListPacket p) {
        ClientState.comaPlayers.clear();
        ClientState.comaPlayers.addAll(p.players());
        ClientState.customRender = p.customRender();
    }

    public static void tablet(ModNetwork.TabletPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TabletScreen s) s.update(p);
        else mc.setScreen(new TabletScreen(p));
    }

    public static void defib(ModNetwork.DefibStartPacket p) {
        Minecraft.getInstance().setScreen(new DefibScreen(p.seed(), p.beats()));
    }

    public static void clinic(ModNetwork.ClinicPacket p) {
        Minecraft.getInstance().setScreen(new ClinicScreen(p));
    }
}
