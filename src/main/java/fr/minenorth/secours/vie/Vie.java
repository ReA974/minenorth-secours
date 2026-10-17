package fr.minenorth.secours.vie;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/** Opérations sur les statistiques de vie (utilisées par les évènements, les objets et les commandes). Tout se passe côté serveur. */
public final class Vie {
    private Vie() {}

    public static Stats stats(ServerPlayer p) { return VieData.get(p.server).of(p.getUUID()); }

    public static void dirty(MinecraftServer s) { VieData.get(s).setDirty(); }

    @Nullable
    public static Disease disease(Stats s) { return s.disease.isEmpty() ? null : Disease.byId(s.disease); }

    /** Contamine le joueur (sans effet s'il est déjà malade). Renvoie vrai si la maladie démarre. */
    public static boolean infect(ServerPlayer p, Disease d) {
        Stats s = stats(p);
        if (!s.disease.isEmpty()) return false;
        s.disease = d.id;
        s.diseaseLeft = d.minutes;
        dirty(p.server);
        p.sendSystemMessage(Component.literal("§c[Santé] §f" + d.onset));
        p.displayClientMessage(Component.literal("§c" + d.label), true);
        return true;
    }

    /** Guérit le joueur. Renvoie vrai s'il était malade. */
    public static boolean cure(ServerPlayer p) {
        Stats s = stats(p);
        if (s.disease.isEmpty()) return false;
        s.disease = "";
        s.diseaseLeft = 0;
        dirty(p.server);
        p.sendSystemMessage(Component.literal("§a[Santé] Vous êtes guéri(e)."));
        return true;
    }

    /** Ajoute un bonus d'immunité temporaire (le plus fort et le plus long des deux est gardé). */
    public static void boost(ServerPlayer p, double points, double minutes) {
        Stats s = stats(p);
        s.boost = Math.max(s.boost, points);
        s.boostLeft = Math.max(s.boostLeft, minutes);
        dirty(p.server);
    }

    /** Immunité (0 à 100) d'un joueur, pour les autres mods (par réflexion). */
    public static double immunity(MinecraftServer s, UUID id) {
        return VieData.get(s).has(id) ? VieData.get(s).of(id).immunity() : 50;
    }
}
