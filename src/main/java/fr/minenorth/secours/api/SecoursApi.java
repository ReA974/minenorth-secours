package fr.minenorth.secours.api;

import fr.minenorth.secours.data.SecoursData;
import fr.minenorth.secours.SecoursService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

    // ------------------------------------------------------------------ utilisé par le panneau admin
    // Le mod Admin appelle ces méthodes par réflexion : ne pas changer leurs noms ni leurs paramètres.

    /** Noms des grades, du plus haut (index 0) au plus bas. */
    public static String[] grades() { return SecoursData.GRADES.clone(); }

    /** Grade du joueur (0 = Chef … 2 = Secouriste), -1 s'il n'est pas dans les secours. Fonctionne hors ligne. */
    public static int grade(MinecraftServer s, UUID id) {
        Integer g = SecoursData.get(s).staff.get(id);
        return g == null ? -1 : Math.max(0, Math.min(2, g));
    }

    /** Effectifs : uuid -> grade (copie). */
    public static Map<UUID, Integer> staff(MinecraftServer s) {
        return new LinkedHashMap<>(SecoursData.get(s).staff);
    }

    /** Vrai si le pompier a pris son service sur la tablette (salaire de l'État). */
    public static boolean onDuty(MinecraftServer s, UUID id) {
        return SecoursData.get(s).onDuty.contains(id);
    }

    /** Nom affiché (carte d'identité, sinon pseudo). */
    public static String name(MinecraftServer s, UUID id) { return SecoursService.display(s, id); }

    /** Nomme (grade 0..2) ou retire (-1) un pompier, connecté ou non. Renvoie le message de résultat. */
    public static String setGrade(MinecraftServer s, UUID id, String name, int grade) {
        return SecoursService.setGrade(s, id, name, grade);
    }

    /** Donne une tablette (joueur connecté). Faux s'il est hors ligne. */
    public static boolean giveTablet(MinecraftServer s, UUID id) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p == null) return false;
        SecoursService.giveTablet(p);
        return true;
    }
}
