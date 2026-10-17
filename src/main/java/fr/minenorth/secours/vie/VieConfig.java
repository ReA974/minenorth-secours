package fr.minenorth.secours.vie;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/** config/minenorth_vie.toml (serveur dédié). Hors serveur dédié la config n'est pas chargée : les valeurs par défaut s'appliquent. */
public final class VieConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED, DISEASES, CONTAGION, CURE_ON_DEATH, HARSH;
    private static final ForgeConfigSpec.DoubleValue SPRINT_BONUS, SPRINT_STAMINA, HEALTH_BONUS, TRAIN_GAIN, TRAIN_MAX_HOUR, DECAY_FORME, DISEASE_BASE, DEATH_LOSS;
    private static final ForgeConfigSpec.IntValue IMMUNE_THRESHOLD, DISEASE_CHECK_MIN, SICK_PENALTY_HP;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HEALTHY, JUNK, TOXIC, RISKY;

    private static final List<String> DEF_HEALTHY = List.of("minecraft:apple", "minecraft:carrot", "minecraft:golden_carrot", "minecraft:melon_slice",
            "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:baked_potato", "minecraft:beetroot", "minecraft:cooked_cod", "minecraft:cooked_salmon",
            "minecraft:bread", "minecraft:dried_kelp", "minecraft:beetroot_soup", "minecraft:mushroom_stew", "minecraft:rabbit_stew",
            "minenorth_harvest:orange", "minenorth_harvest:lemon", "minenorth_harvest:cherries", "minenorth_harvest:pear", "minenorth_harvest:baguette");
    private static final List<String> DEF_JUNK = List.of("minecraft:cake", "minecraft:cookie", "minecraft:pumpkin_pie", "minecraft:honey_bottle",
            "minecraft:cooked_porkchop", "minecraft:cooked_beef", "minecraft:suspicious_stew",
            "minenorth_harvest:apple_pie", "minenorth_harvest:orange_pie", "minenorth_harvest:lemon_pie", "minenorth_harvest:cherry_pie", "minenorth_harvest:pear_pie");
    private static final List<String> DEF_TOXIC = List.of("minenorthdrogue:joint", "minenorthdrogue:cocaine", "minenorthdrogue:meth");
    private static final List<String> DEF_RISKY = List.of("minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:poisonous_potato", "minecraft:chicken",
            "minecraft:porkchop", "minecraft:beef", "minecraft:mutton", "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:pufferfish");

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Général").push("general");
        ENABLED = b.comment("false = le mod ne fait rien").define("actif", true);
        DISEASES = b.comment("Maladies activées (sinon seuls les bonus de forme et d'alimentation existent)").define("maladies", true);
        CONTAGION = b.comment("Un joueur malade contagieux peut contaminer ceux qui restent près de lui").define("contagion", false);
        HARSH = b.comment("false (défaut, mode FUN) : être malade ne fait que suspendre vos bonus (course, vie en plus) et vous fait éternuer ; aucun malus de vitesse, de faim ni de vie.",
                        "true : maladies pénalisantes (ralentissement, faiblesse, faim, nausées, vie en moins).").define("maladiesRudes", false);
        CURE_ON_DEATH = b.comment("La mort guérit la maladie en cours").define("guerisonALaMort", true);
        DEATH_LOSS = b.comment("Part de la forme perdue à la mort (0.1 = 10 %)").defineInRange("perteFormeMort", 0.1, 0.0, 1.0);
        b.pop();
        b.comment("Bonus liés à la forme et à l'alimentation").push("bonus");
        SPRINT_BONUS = b.comment("Bonus de vitesse de course à la forme maximale (0.12 = +12 %)").defineInRange("vitesseCourse", 0.12, 0.0, 0.5);
        SPRINT_STAMINA = b.comment("Réduction de la dépense de faim en courant, à la forme maximale (0.4 = 40 % de faim en moins)").defineInRange("enduranceCourse", 0.4, 0.0, 0.9);
        HEALTH_BONUS = b.comment("Points de vie en plus à la forme ET l'alimentation maximales (2 = un coeur)").defineInRange("vieSupplementaire", 4.0, 0.0, 20.0);
        SICK_PENALTY_HP = b.comment("Points de vie en moins pendant une maladie sérieuse (grippe)").defineInRange("vieEnMoinsMalade", 2, 0, 10);
        b.pop();
        b.comment("Entraînement").push("sport");
        TRAIN_GAIN = b.comment("Points de forme gagnés par 100 blocs courus (50 blocs à la nage = autant)").defineInRange("gainPar100Blocs", 1.5, 0.0, 20.0);
        TRAIN_MAX_HOUR = b.comment("Points de forme gagnables au maximum par heure de jeu (anti-triche)").defineInRange("gainMaxParHeure", 20.0, 1.0, 200.0);
        DECAY_FORME = b.comment("Forme perdue par minute sans entraînement").defineInRange("perteParMinute", 0.03, 0.0, 5.0);
        b.pop();
        b.comment("Maladies").push("maladies");
        IMMUNE_THRESHOLD = b.comment("Immunité à partir de laquelle on ne tombe plus malade (rhume, grippe, gastro)").defineInRange("seuilImmunite", 75, 1, 100);
        DISEASE_CHECK_MIN = b.comment("Minutes de jeu entre deux risques de contamination").defineInRange("minutesEntreRisques", 10, 1, 120);
        DISEASE_BASE = b.comment("Chance de base de tomber malade à chaque risque (avant l'effet de l'immunité)").defineInRange("chanceDeBase", 0.06, 0.0, 1.0);
        b.pop();
        b.comment("Listes d'objets (identifiant complet)").push("aliments");
        HEALTHY = b.comment("Aliments sains : améliorent l'équilibre alimentaire").defineList("sains", DEF_HEALTHY, o -> o instanceof String);
        JUNK = b.comment("Aliments gras / sucrés : dégradent l'équilibre alimentaire (avec modération, aucun effet)").defineList("gras", DEF_JUNK, o -> o instanceof String);
        TOXIC = b.comment("Substances : augmentent les toxines (drogues, alcool d'autres mods…)").defineList("toxiques", DEF_TOXIC, o -> o instanceof String);
        RISKY = b.comment("Aliments à risque : peuvent donner une intoxication alimentaire").defineList("risques", DEF_RISKY, o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private VieConfig() {}

    private static <T> T val(Supplier<T> s, T def) {
        try { return s.get(); } catch (IllegalStateException e) { return def; }
    }

    public static boolean enabled() { return val(ENABLED::get, true); }
    public static boolean diseases() { return val(DISEASES::get, true); }
    public static boolean contagion() { return val(CONTAGION::get, false); }
    public static boolean harsh() { return val(HARSH::get, false); }
    public static boolean cureOnDeath() { return val(CURE_ON_DEATH::get, true); }
    public static double deathLoss() { return val(DEATH_LOSS::get, 0.1); }
    public static double sprintBonus() { return val(SPRINT_BONUS::get, 0.12); }
    public static double sprintStamina() { return val(SPRINT_STAMINA::get, 0.4); }
    public static double healthBonus() { return val(HEALTH_BONUS::get, 4.0); }
    public static int sickPenaltyHp() { return val(SICK_PENALTY_HP::get, 2); }
    public static double trainGain() { return val(TRAIN_GAIN::get, 1.5); }
    public static double trainMaxHour() { return val(TRAIN_MAX_HOUR::get, 20.0); }
    public static double decayForme() { return val(DECAY_FORME::get, 0.03); }
    public static int immuneThreshold() { return val(IMMUNE_THRESHOLD::get, 75); }
    public static int diseaseCheckMinutes() { return val(DISEASE_CHECK_MIN::get, 10); }
    public static double diseaseBase() { return val(DISEASE_BASE::get, 0.06); }
    @SuppressWarnings("unchecked") public static List<String> healthy() { return val(() -> (List<String>) HEALTHY.get(), DEF_HEALTHY); }
    @SuppressWarnings("unchecked") public static List<String> junk() { return val(() -> (List<String>) JUNK.get(), DEF_JUNK); }
    @SuppressWarnings("unchecked") public static List<String> toxic() { return val(() -> (List<String>) TOXIC.get(), DEF_TOXIC); }
    @SuppressWarnings("unchecked") public static List<String> risky() { return val(() -> (List<String>) RISKY.get(), DEF_RISKY); }
}
