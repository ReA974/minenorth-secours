package fr.minenorth.secours.client;

/**
 * État de santé du joueur local, reçu du serveur.
 * Classe volontairement sans aucun import client : le code commun peut la lire sans risque sur un serveur dédié.
 */
public final class ClientState {
    private ClientState() {}

    public static int level, zones;
    /** Joueurs inconscients connus de ce client, et s'il faut les coucher nous-mêmes à l'affichage. */
    public static final java.util.Set<java.util.UUID> comaPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static boolean customRender = true;
    public static boolean bleeding, coma;
    public static String dispatch = "";
    private static int healSeconds, comaSeconds, careSeconds;
    private static long receivedAt;

    public static void set(int lvl, int heal, boolean bleed, boolean isComa, int comaLeft, String unit, int care, int hitZones) {
        zones = hitZones;
        level = lvl; healSeconds = heal; bleeding = bleed; coma = isComa; comaSeconds = comaLeft; dispatch = unit == null ? "" : unit;
        careSeconds = care; receivedAt = System.currentTimeMillis();
    }

    private static int left(int seconds) { return (int) Math.max(0, seconds - (System.currentTimeMillis() - receivedAt) / 1000); }
    public static int healLeft() { return left(healSeconds); }
    public static int comaLeft() { return left(comaSeconds); }
    public static int careLeft() { return left(careSeconds); }

    public static String clock(int seconds) { return (seconds / 60) + ":" + String.format("%02d", seconds % 60); }
}
