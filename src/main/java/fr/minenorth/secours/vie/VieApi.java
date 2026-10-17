package fr.minenorth.secours.vie;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Points d'entrée pour les autres mods (appel par réflexion : noms et paramètres à ne pas changer). */
public final class VieApi {
    private VieApi() {}

    /** Immunité du joueur de 0 à 100. */
    public static double immunity(MinecraftServer s, UUID id) { return Vie.immunity(s, id); }

    /** Donne une maladie (rhume, grippe, gastro, intoxication). Faux si le joueur est déjà malade ou l'identifiant inconnu. */
    public static boolean infect(ServerPlayer p, String diseaseId) {
        Disease d = Disease.byId(diseaseId);
        return d != null && Vie.infect(p, d);
    }

    /** Guérit le joueur (soins, hôpital…). Vrai s'il était malade. */
    public static boolean cure(ServerPlayer p) { return Vie.cure(p); }

    /** Bonus temporaire d'immunité (vaccin, soins). */
    public static void boost(ServerPlayer p, double points, double minutes) { Vie.boost(p, points, minutes); }
}
