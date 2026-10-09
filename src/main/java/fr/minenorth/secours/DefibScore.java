package fr.minenorth.secours;

import java.util.Random;

/** Mini-jeu du défibrillateur : battements générés par le serveur, précision recalculée par le serveur. Aucune dépendance Minecraft. */
public final class DefibScore {
    private DefibScore() {}

    public static final long PERFECT_MS = 80, GOOD_MS = 180;

    /** Instants des battements (ms depuis l'ouverture de l'écran), identiques pour le même seed des deux côtés. */
    public static long[] beats(long seed, int count) {
        Random r = new Random(seed);
        long[] out = new long[Math.max(0, count)];
        long t = 1500 + r.nextInt(300);
        for (int i = 0; i < out.length; i++) {
            out[i] = t;
            t += 550 + r.nextInt(351);
        }
        return out;
    }

    /** Précision de 0 à 1. Rejette les rafales de frappes et les parties trop courtes pour être jouées. */
    public static double evaluate(long[] beats, long[] taps, long elapsedMs) {
        if (beats.length == 0 || taps.length > 2 * beats.length) return 0;
        if (elapsedMs < beats[beats.length - 1] - 500) return 0;
        boolean[] used = new boolean[taps.length];
        double sum = 0;
        for (long beat : beats) {
            int best = -1;
            long bestDiff = Long.MAX_VALUE;
            for (int i = 0; i < taps.length; i++) {
                if (used[i]) continue;
                long diff = Math.abs(taps[i] - beat);
                if (diff < bestDiff) { bestDiff = diff; best = i; }
            }
            if (best < 0 || bestDiff > GOOD_MS) continue;
            used[best] = true;
            sum += bestDiff <= PERFECT_MS ? 1.0 : 0.5;
        }
        return sum / beats.length;
    }
}
