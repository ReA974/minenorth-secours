package fr.minenorth.secours.api;

import fr.minenorth.secours.FireService;
import fr.minenorth.secours.SecoursService;
import fr.minenorth.secours.config.SecoursConfig;
import fr.minenorth.secours.data.SecoursData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Administration des secours pour le panneau admin (mod Admin, appelé par réflexion : ne pas changer les noms ni les paramètres).
 * Les droits sont vérifiés par le panneau ; ici on borne les valeurs et on enregistre. Thread serveur uniquement.
 * Format des réglages : « clé \t groupe \t libellé \t type (b, i, d) \t valeur \t min \t max \t pas ».
 */
public final class SecoursAdminApi {
    private SecoursAdminApi() {}

    private static final List<Object[]> DEFS = new ArrayList<>();

    private static void d(String group, String key, String label, String kind, double min, double max, double step) {
        DEFS.add(new Object[]{group, key, label, kind, min, max, step});
    }

    static {
        String g = "Blessures";
        d(g, "chute_legere_blocs", "Chute légère à partir de (blocs)", "d", 1, 50, 0.5);
        d(g, "chute_moyenne_blocs", "Chute moyenne à partir de (blocs)", "d", 1, 80, 0.5);
        d(g, "grave_coeurs_restants", "Blessure grave : cœurs restants", "d", 0.5, 10, 0.5);
        d(g, "coma_coeurs_restants", "Inconscient : cœurs restants", "d", 0.5, 10, 0.5);
        d(g, "blessures_coups", "Blessures par coups", "b", 0, 1, 1);
        d(g, "coup_leger_coeurs", "Coup léger (cœurs retirés)", "d", 0.5, 10, 0.5);
        d(g, "coup_moyen_coeurs", "Coup moyen (cœurs retirés)", "d", 0.5, 10, 0.5);
        d(g, "blessures_explosions", "Blessures par explosions", "b", 0, 1, 1);
        d(g, "blessures_feu", "Brûlures (feu et lave)", "b", 0, 1, 1);
        d(g, "feu_coeurs_restants", "Brûlure : cœurs restants", "d", 0.5, 10, 0.5);
        d(g, "blessures_noyade", "Blessures par noyade", "b", 0, 1, 1);
        d(g, "noyade_coeurs_restants", "Noyade : cœurs restants", "d", 0.5, 10, 0.5);
        d(g, "hemorragie_par_balle", "Hémorragie par balle", "b", 0, 1, 1);
        d(g, "hemorragie_intervalle_secondes", "Hémorragie : intervalle (s)", "i", 1, 120, 1);
        d(g, "hemorragie_torse_multiplicateur", "Hémorragie au torse (multiplicateur)", "d", 1, 10, 0.5);
        d(g, "tete_inconscient_direct", "Tête touchée : inconscient direct", "b", 0, 1, 1);
        d(g, "ralentissement_legere", "Ralentissement : blessure légère", "d", 0, 0.9, 0.05);
        d(g, "ralentissement_moyenne", "Ralentissement : blessure moyenne", "d", 0, 0.9, 0.05);
        d(g, "ralentissement_grave", "Ralentissement : blessure grave", "d", 0, 0.9, 0.05);
        d(g, "ralentissement_jambe", "Ralentissement : jambe touchée", "d", 0, 0.9, 0.05);
        d(g, "guerison_legere_minutes", "Guérison légère (min)", "d", 0, 120, 1);
        d(g, "guerison_moyenne_minutes", "Guérison moyenne (min)", "d", 0, 240, 1);
        d(g, "protection_active", "Protection par l'armure", "b", 0, 1, 1);
        d(g, "protection_points_min", "Armure : points minimum", "i", 1, 20, 1);
        g = "Coma et hôpital";
        d(g, "coma_duree_minutes", "Durée du coma (min)", "d", 1, 120, 1);
        d(g, "coma_mortel", "Un inconscient peut mourir", "b", 0, 1, 1);
        d(g, "coma_mortel_sans_secours_seulement", "Mortel seulement sans secours", "b", 0, 1, 1);
        d(g, "defib_battements", "Défibrillateur : battements", "i", 1, 40, 1);
        d(g, "defib_precision_min", "Défibrillateur : précision minimale", "d", 0, 1, 0.05);
        d(g, "facture_hopital_euros", "Facture d'hôpital (€)", "d", 0, 100000, 50);
        d(g, "hopital_duree_minutes", "Séjour à l'hôpital (min)", "d", 0, 120, 1);
        d(g, "hopital_rayon_blocs", "Rayon autour de l'hôpital (blocs)", "d", 2, 200, 1);
        d(g, "urgences_duree_minutes", "Séjour aux urgences (min)", "d", 0, 120, 1);
        g = "Soins et PNJ";
        d(g, "facture_soins_euros", "Facture d'un soin (€)", "d", 0, 100000, 25);
        d(g, "facture_reanimation_euros", "Facture d'une réanimation (€)", "d", 0, 100000, 25);
        d(g, "facture_part_secouriste", "Part versée au secouriste", "d", 0, 1, 0.05);
        d(g, "pnj_prix_legere_euros", "PNJ : prix blessure légère (€)", "d", 0, 100000, 25);
        d(g, "pnj_prix_moyenne_euros", "PNJ : prix blessure moyenne (€)", "d", 0, 100000, 25);
        d(g, "pnj_prix_grave_euros", "PNJ : prix blessure grave (€)", "d", 0, 100000, 50);
        d(g, "pnj_duree_legere_secondes", "PNJ : durée légère (s)", "i", 1, 600, 5);
        d(g, "pnj_duree_moyenne_secondes", "PNJ : durée moyenne (s)", "i", 1, 600, 5);
        d(g, "pnj_duree_grave_secondes", "PNJ : durée grave (s)", "i", 1, 600, 5);
        d(g, "pnj_grave_seulement_sans_secours", "PNJ : grave seulement sans secours", "b", 0, 1, 1);
        d(g, "pnj_distance_max", "PNJ : distance maximale", "i", 1, 30, 1);
        d(g, "distance_soin", "Distance de soin (blocs)", "i", 1, 20, 1);
        g = "Incendies";
        d(g, "incendies_actifs", "Incendies de service actifs", "b", 0, 1, 1);
        d(g, "incendie_delai_secondes", "Délai avant le premier incendie (s)", "i", 0, 3600, 10);
        d(g, "incendie_intervalle_minutes", "Un incendie toutes les (min)", "d", 1, 240, 1);
        d(g, "incendie_max_simultanes", "Incendies simultanés (max)", "i", 1, 10, 1);
        d(g, "incendie_rayon", "Rayon des foyers (blocs)", "i", 1, 20, 1);
        d(g, "incendie_duree_max_minutes", "Durée maximale (min)", "d", 1, 240, 1);
        d(g, "incendie_duree_min_minutes", "Durée minimale (min)", "d", 0, 240, 1);
        d(g, "incendie_foyers_min", "Foyers au départ (min)", "i", 1, 60, 1);
        d(g, "incendie_foyers_max", "Foyers au départ (max)", "i", 1, 60, 1);
        d(g, "incendie_propagation_secondes", "Propagation : toutes les (s, 0 = aucune)", "i", 0, 600, 5);
        d(g, "incendie_propagation_rayon_bonus", "Propagation : rayon en plus (blocs)", "i", 0, 20, 1);
        d(g, "incendie_reprises_max", "Reprises de feu (max)", "i", 0, 20, 1);
        d(g, "incendie_prime_euros", "Prime d'incendie maîtrisé (€)", "d", 0, 100000, 25);
        d(g, "incendie_prime_distance", "Prime : distance de présence (blocs)", "i", 5, 200, 5);
        d(g, "incendie_prime_presence_secondes", "Prime : présence minimale (s)", "i", 0, 3600, 10);
        d(g, "incendie_calme_actif", "Mode calme (trop de blessés)", "b", 0, 1, 1);
        d(g, "incendie_calme_niveau_min", "Mode calme : niveau de blessure", "i", 1, 3, 1);
        d(g, "incendie_blesses_par_pompier", "Mode calme : blessés par pompier", "i", 1, 20, 1);
    }

    private static Field field(String key) {
        try {
            return SecoursConfig.class.getField(key);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** Tous les réglages (secours puis santé). */
    public static List<String> settings() {
        List<String> out = new ArrayList<>();
        SecoursConfig c = SecoursConfig.get();
        for (Object[] r : DEFS) {
            Field f = field((String) r[1]);
            if (f == null) continue;
            try {
                Object v = f.get(c);
                double val = v instanceof Boolean b ? (b ? 1 : 0) : ((Number) v).doubleValue();
                out.add("sec." + r[1] + "\t" + r[0] + "\t" + r[2] + "\t" + r[3] + "\t" + val + "\t" + r[4] + "\t" + r[5] + "\t" + r[6]);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        for (String l : fr.minenorth.secours.vie.VieConfig.adminLines()) {
            String[] p = l.split("\t");
            out.add("vie." + p[0] + "\tSanté\t" + p[1] + "\t" + p[2] + "\t" + p[3] + "\t" + p[4] + "\t" + p[5] + "\t" + p[6]);
        }
        return out;
    }

    /** Applique un réglage borné et l'enregistre ; renvoie un message (jamais null). */
    public static String set(String key, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "Valeur invalide.";
        if (key.startsWith("vie.")) {
            String r = fr.minenorth.secours.vie.VieConfig.adminSet(key.substring(4), value);
            return r == null ? "Impossible d'enregistrer ce réglage (config non chargée)." : r;
        }
        if (!key.startsWith("sec.")) return "Réglage inconnu.";
        String k = key.substring(4);
        for (Object[] r : DEFS) {
            if (!r[1].equals(k)) continue;
            Field f = field(k);
            if (f == null) return "Réglage inconnu.";
            double v = Math.max((Double) r[4], Math.min((Double) r[5], value));
            try {
                SecoursConfig c = SecoursConfig.get();
                Object o;
                if (f.getType() == boolean.class) o = v >= 0.5;
                else if (f.getType() == int.class) o = (int) Math.round(v);
                else o = Math.round(v * 1000) / 1000.0;
                f.set(c, o);
                // cohérences que le chargement impose aussi
                c.incendie_duree_min_minutes = Math.max(0, Math.min(c.incendie_duree_min_minutes, c.incendie_duree_max_minutes));
                c.incendie_foyers_max = Math.max(c.incendie_foyers_min, c.incendie_foyers_max);
                if (!SecoursConfig.save()) return "Réglage appliqué mais fichier non enregistré.";
                return r[2] + " : " + o;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "Réglage invalide.";
            }
        }
        return "Réglage inconnu.";
    }

    /** Sites d'incendie : « nom \t x \t y \t z ». */
    public static List<String> sites(MinecraftServer s) {
        List<String> out = new ArrayList<>();
        for (SecoursData.Site st : SecoursData.get(s).sites.values()) out.add(st.name + "\t" + st.x + "\t" + st.y + "\t" + st.z);
        return out;
    }

    public static int incidents(MinecraftServer s) {
        return SecoursData.get(s).incidents.size();
    }

    public static String siteAdd(ServerPlayer p, String name) {
        SecoursData d = SecoursData.get(p.server);
        SecoursData.Site s = new SecoursData.Site();
        s.name = name;
        s.dim = p.level().dimension().location().toString();
        s.x = p.blockPosition().getX();
        s.y = p.blockPosition().getY();
        s.z = p.blockPosition().getZ();
        d.sites.put(name.toLowerCase(Locale.ROOT), s);
        d.setDirty();
        return "Site d'incendie « " + name + " » défini à ta position. Choisis un sol non inflammable : le feu peut se propager.";
    }

    public static String siteRemove(MinecraftServer server, String name) {
        SecoursData d = SecoursData.get(server);
        if (d.sites.remove(name.toLowerCase(Locale.ROOT)) == null) return "Site inconnu : " + name;
        d.setDirty();
        return "Site « " + name + " » supprimé.";
    }

    /** Déclenche un incendie (site nommé, ou au hasard si le nom est vide). */
    public static String fireStart(MinecraftServer server, String name) {
        return FireService.start(server, name == null || name.isBlank() ? null : name);
    }

    public static String fireStopAll(MinecraftServer server) {
        return FireService.closeAll(server, SecoursData.get(server), "§e[Secours] Incendie annulé par un administrateur.") + " incendie(s) éteint(s).";
    }

    /** Points de réveil : « nom \t x \t y \t z ». */
    public static List<String> spawns(MinecraftServer s, boolean urgences) {
        List<String> out = new ArrayList<>();
        SecoursData d = SecoursData.get(s);
        for (SecoursData.Spawn sp : (urgences ? d.urgences : d.hospitals).values())
            out.add(sp.name + "\t" + (int) sp.x + "\t" + (int) sp.y + "\t" + (int) sp.z);
        return out;
    }

    public static String spawnAdd(ServerPlayer p, boolean urgences, String name) {
        return SecoursService.addSpawn(p, urgences, name == null || name.isBlank() ? null : name);
    }

    public static String spawnRemove(MinecraftServer s, boolean urgences, String name) {
        return SecoursService.removeSpawn(s, urgences, name);
    }
}
