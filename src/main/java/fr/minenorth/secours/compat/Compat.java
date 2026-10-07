package fr.minenorth.secours.compat;

import fr.minenorth.api.Identity;
import fr.minenorth.api.MineNorth;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Noms RP via MineNorth API (plus de réflexion). */
public final class Compat {
    private Compat() {}

    /** « NOM Prénom » de la carte d'identité (format des fichiers), ou "" si le citoyen n'en a pas. */
    public static String rpName(MinecraftServer s, UUID id) {
        if (s == null || id == null) return "";
        return MineNorth.identity().get(s, id).map(Identity::officialName).orElse("");
    }
}
