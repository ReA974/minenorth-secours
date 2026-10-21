package fr.minenorth.secours.vie.client;

import fr.minenorth.secours.vie.VieNet;
import net.minecraft.client.Minecraft;

/**
 * Application « Santé » du téléphone MRPhone patché (appelée par réflexion par le jar patché) : visible pour tous les joueurs,
 * ouvre l'écran Santé. Les données viennent du serveur (paquet de demande/réponse VieNet) : le client ne décide de rien.
 */
public final class PhoneSante {
    private PhoneSante() {}

    public static boolean visible() { return true; }

    /** Clic sur l'icône : ferme le téléphone et demande l'écran Santé au serveur. */
    public static void openFromPhone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.setScreen(null);
        VieNet.CHANNEL.sendToServer(new VieNet.Request(true));
    }
}
