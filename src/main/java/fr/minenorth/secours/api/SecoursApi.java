package fr.minenorth.secours.api;

import fr.minenorth.secours.data.SecoursData;
import net.minecraft.server.level.ServerPlayer;

/**
 * API pour les autres mods MineNorth. Thread serveur uniquement.
 * Le mod Portes appelle isSecours par réflexion : ne pas changer son nom ni ses paramètres.
 */
public final class SecoursApi {
    private SecoursApi() {}

    /** Vrai si le joueur fait partie des pompiers / du SAMU. */
    public static boolean isSecours(ServerPlayer p) {
        return SecoursData.get(p.server).staff.containsKey(p.getUUID());
    }

    /** Vrai si le joueur est inconscient au sol. */
    public static boolean isUnconscious(ServerPlayer p) {
        SecoursData.Injury j = SecoursData.get(p.server).peek(p.getUUID());
        return j != null && j.coma;
    }
}
