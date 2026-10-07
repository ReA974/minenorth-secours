package fr.minenorth.secours.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configuration : config/minenorth_secours.json — rechargée avec /secours reload. */
public final class SecoursConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static SecoursConfig current = new SecoursConfig();

    /** Un objet de soin. item = identifiant Minecraft (F3+H pour le voir en jeu). */
    public static final class Objet {
        public String item; public int secondes; public boolean consomme; public boolean reserve_secours;
        Objet(String item, int secondes, boolean consomme, boolean reserveSecours) {
            this.item = item; this.secondes = secondes; this.consomme = consomme; this.reserve_secours = reserveSecours;
        }
    }

    public String _aide = "Durées de guérison en minutes, prix en euros, cœurs = cœurs entiers restants. "
            + "bandage : arrête l'hémorragie et soigne une blessure légère. trousse : soigne toutes les blessures. "
            + "defibrillateur : réanime un joueur inconscient. "
            + "Protection : une balle arrêtée par l'armure de la zone touchée ne cause ni hémorragie ni blessure par balle. "
            + "Rechargez avec /secours reload.";

    // --- chutes
    public double chute_legere_blocs = 4;
    public double chute_moyenne_blocs = 8;
    /** Après une chute, s'il reste ce nombre de cœurs ou moins : blessure grave. */
    public double grave_coeurs_restants = 4;
    /** S'il reste ce nombre de cœurs ou moins après n'importe quel dégât : le joueur tombe inconscient. */
    public double coma_coeurs_restants = 2;

    // --- autres causes de blessure (un seul coup qui retire au moins ce nombre de cœurs)
    public boolean blessures_coups = true;
    public double coup_leger_coeurs = 1.5;
    public double coup_moyen_coeurs = 3;
    /** Explosions et accidents de véhicule : blessure grave s'il reste grave_coeurs_restants ou moins. */
    public boolean blessures_explosions = true;
    /** Feu et lave : brûlure moyenne s'il reste ce nombre de cœurs ou moins. */
    public boolean blessures_feu = true;
    public double feu_coeurs_restants = 6;
    /** Noyade : blessure légère s'il reste ce nombre de cœurs ou moins. */
    public boolean blessures_noyade = true;
    public double noyade_coeurs_restants = 5;

    // --- facture envoyée au patient quand un secouriste le soigne (0 = gratuit)
    public double facture_soins_euros = 150;
    public double facture_reanimation_euros = 300;
    /** Part de la facture versée au secouriste (0.5 = la moitié) ; le reste va à la banque. */
    public double facture_part_secouriste = 0.5;

    // --- guérison naturelle
    public double guerison_legere_minutes = 3;
    public double guerison_moyenne_minutes = 12;

    // --- ralentissement (0.05 = 5 % plus lent)
    public double ralentissement_legere = 0.05;
    public double ralentissement_moyenne = 0.20;
    public double ralentissement_grave = 0.45;

    // --- hémorragie (blessure par balle)
    public boolean hemorragie_par_balle = true;
    public int hemorragie_intervalle_secondes = 8;

    // --- zone touchée par une balle (arme TACZ)
    /** Ralentissement quand une jambe est touchée (remplace celui de la blessure s'il est plus fort). Plus de saut non plus. */
    public double ralentissement_jambe = 0.50;
    /** Torse touché : l'hémorragie va ce nombre de fois plus vite. */
    public double hemorragie_torse_multiplicateur = 2;
    /** Tête touchée sans protection : inconscient immédiatement. */
    public boolean tete_inconscient_direct = true;

    // --- protection : casque = tête, plastron = torse et bras, jambières = jambes
    public boolean protection_active = true;
    /** Points d'armure minimum de la pièce pour qu'elle arrête une balle (1 = n'importe quelle armure). */
    public int protection_points_min = 1;
    /** Objets comptés comme protection même s'ils ne sont pas une armure classique (ex. gilet pare-balles d'un mod). */
    public java.util.List<String> protection_objets = new java.util.ArrayList<>();
    /** true : seuls les objets de protection_objets protègent. */
    public boolean protection_seulement_liste = false;

    // --- coma
    public double coma_duree_minutes = 10;
    /**
     * true (conseillé) : le mod couche lui-même le personnage à l'affichage, quels que soient les autres mods.
     * false : on utilise la pose Minecraft de coma_pose (SLEEPING ou SWIMMING), qui ne fonctionne pas sur toutes les installations.
     */
    public boolean coma_rendu_allonge = true;
    /** Utilisé seulement si coma_rendu_allonge = false. SLEEPING = allongé sur le dos ; SWIMMING = à plat ventre. */
    public String coma_pose = "SLEEPING";
    public double coeurs_apres_reanimation = 3;
    public double facture_hopital_euros = 500;

    // --- PNJ de soins
    public double pnj_prix_legere_euros = 100;
    public double pnj_prix_moyenne_euros = 300;
    public double pnj_prix_grave_euros = 1500;
    public int pnj_duree_legere_secondes = 20;
    public int pnj_duree_moyenne_secondes = 45;
    public int pnj_duree_grave_secondes = 90;
    /** true : le PNJ ne soigne une blessure grave que si aucun pompier / SAMU n'est connecté. */
    public boolean pnj_grave_seulement_sans_secours = true;
    public int pnj_distance_max = 5;

    // --- objets de soin
    public Objet bandage = new Objet("minecraft:paper", 4, true, false);
    public Objet trousse = new Objet("minecraft:glistering_melon_slice", 8, true, true);
    public Objet defibrillateur = new Objet("minecraft:totem_of_undying", 10, false, true);
    public int distance_soin = 4;

    // --- coma
    /** Commandes encore utilisables inconscient (sans le /). Les OP ne sont pas concernés. Le reste est bloqué (/home, /spawn, /tpa…). */
    public java.util.List<String> commandes_autorisees_coma = new java.util.ArrayList<>(java.util.List.of("msg", "tell", "w", "r", "me", "help"));

    public static SecoursConfig get() { return current; }

    public static boolean load() {
        // Config côté serveur uniquement : le client ne crée ni ne lit aucun fichier.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER) return true;
        Path f = FMLPaths.CONFIGDIR.get().resolve("minenorth_secours.json");
        boolean ok = true;
        try {
            if (Files.exists(f)) {
                SecoursConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), SecoursConfig.class);
                if (c != null) current = c;
            }
        } catch (Exception e) {
            ok = false;
        }
        SecoursConfig c = current, d = new SecoursConfig();
        if (c.bandage == null || c.bandage.item == null) c.bandage = d.bandage;
        if (c.trousse == null || c.trousse.item == null) c.trousse = d.trousse;
        if (c.defibrillateur == null || c.defibrillateur.item == null) c.defibrillateur = d.defibrillateur;
        if (c.coma_pose == null) c.coma_pose = "SLEEPING";
        if (c.protection_objets == null) c.protection_objets = new java.util.ArrayList<>();
        if (c.commandes_autorisees_coma == null) c.commandes_autorisees_coma = d.commandes_autorisees_coma;
        c.hemorragie_torse_multiplicateur = Math.max(1, c.hemorragie_torse_multiplicateur);
        c.hemorragie_intervalle_secondes = Math.max(1, c.hemorragie_intervalle_secondes);
        c.coma_coeurs_restants = Math.max(0.5, c.coma_coeurs_restants);
        if (ok) {
            try {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return ok;
    }

    public static long cents(double euros) { return Math.max(0, Math.round(euros * 100.0)); }
    public static long ms(double minutes) { return Math.max(0, Math.round(minutes * 60_000.0)); }
}
