package fr.minenorth.secours.client.phone;

import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import fr.minenorth.secours.network.ModNetwork;
import fr.minenorth.secours.network.PhoneNet;
import fr.minenorth.secours.vie.VieNet;
import net.minecraft.client.Minecraft;

/**
 * Seul point d'entrée vers {@link PhoneSecoursScreen} et {@link PhoneSanteScreen} (qui héritent d'une classe du téléphone MikasRevs Phone 2).
 * Ne JAMAIS toucher cette classe sans avoir vérifié {@code PhoneAppClient.phone()} : sa vérification par la JVM charge ces écrans, donc la classe
 * du téléphone. Côté client uniquement.
 */
public final class PhoneSecours {
    /** Ouverture demandée depuis le téléphone : la prochaine réponse du serveur ouvre l'application dans le téléphone (pendant 5 s). */
    private static long pendingSecours, pendingSante;

    private PhoneSecours() {}

    /** Icône « Secours » : on garde le téléphone ouvert et on demande la tablette au serveur (qui revérifie le métier). */
    public static void request() {
        pendingSecours = System.currentTimeMillis() + 5000;
        ModNetwork.CHANNEL.sendToServer(new PhoneNet.Open());
    }

    /** true si la vue de la tablette a été prise par l'application du téléphone (ouverte ou mise à jour). */
    public static boolean handle(ModNetwork.TabletPacket view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneSecoursScreen ps) {
            ps.update(view);
            return true;
        }
        if (System.currentTimeMillis() < pendingSecours && mc.player != null) {
            pendingSecours = 0;
            PhoneNavigationState.open(mc, mc.screen, new PhoneSecoursScreen(view));
            return true;
        }
        return false;
    }

    /** Icône « Santé » : on garde le téléphone ouvert et on demande l'état au serveur. */
    public static void requestSante() {
        pendingSante = System.currentTimeMillis() + 5000;
        VieNet.CHANNEL.sendToServer(new VieNet.Request(true));
    }

    /** true si l'état Santé a été pris par l'application du téléphone (ouverte ou mise à jour). */
    public static boolean handleSante(VieNet.State s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneSanteScreen ps) {
            ps.update(s);
            return true;
        }
        if (s.open() && System.currentTimeMillis() < pendingSante && mc.player != null) {
            pendingSante = 0;
            PhoneNavigationState.open(mc, mc.screen, new PhoneSanteScreen(s));
            return true;
        }
        return false;
    }
}
