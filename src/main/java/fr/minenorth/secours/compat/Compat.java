package fr.minenorth.secours.compat;

import fr.minenorth.api.Identity;
import fr.minenorth.api.MineNorth;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Noms RP via MineNorth API (plus de réflexion). */
public final class Compat {
    private Compat() {}

    /** État du bouton « répondre sur mon téléphone » (pont MineNorth Téléphone, par réflexion) : 0 indisponible, 1 non, 2 oui. */
    public static int phoneState(net.minecraft.server.level.ServerPlayer p) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("minenorthphone")) return 0;
        try {
            return (Integer) Class.forName("fr.minenorth.phone.CallRouter").getMethod("state", net.minecraft.server.level.ServerPlayer.class, boolean.class).invoke(null, p, false);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Active / coupe la réponse aux appels du 18 sur le téléphone du secouriste. */
    public static void setPhone(net.minecraft.server.level.ServerPlayer p, boolean on) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("minenorthphone")) return;
        try {
            Class.forName("fr.minenorth.phone.CallRouter").getMethod("setOpted", net.minecraft.server.level.ServerPlayer.class, boolean.class, boolean.class).invoke(null, p, on, false);
        } catch (Throwable ignored) {
        }
    }

    /** « NOM Prénom » de la carte d'identité (format des fichiers), ou "" si le citoyen n'en a pas. */
    public static String rpName(MinecraftServer s, UUID id) {
        if (s == null || id == null) return "";
        return MineNorth.identity().get(s, id).map(Identity::officialName).orElse("");
    }
}
