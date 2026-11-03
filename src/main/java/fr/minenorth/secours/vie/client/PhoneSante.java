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

    /** Clic sur l'icône : demande l'état au serveur ; la réponse ouvre l'application DANS le téléphone (phone.PhoneSanteScreen). Sans le téléphone : écran Santé. */
    public static void openFromPhone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (fr.minenorth.secours.client.PhoneAppClient.phone()) { fr.minenorth.secours.client.phone.PhoneSecours.requestSante(); return; }
        mc.setScreen(null);
        VieNet.CHANNEL.sendToServer(new VieNet.Request(true));
    }
}
