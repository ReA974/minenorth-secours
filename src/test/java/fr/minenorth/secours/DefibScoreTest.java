package fr.minenorth.secours;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefibScoreTest {
    private static long[] shifted(long[] beats, long by) {
        long[] t = Arrays.copyOf(beats, beats.length);
        for (int i = 0; i < t.length; i++) t[i] += by;
        return t;
    }

    private static long end(long[] beats) { return beats[beats.length - 1] + 1000; }

    @Test
    void beatsAreDeterministic() {
        assertArrayEquals(DefibScore.beats(42, 12), DefibScore.beats(42, 12));
        assertEquals(12, DefibScore.beats(42, 12).length);
    }

    @Test
    void beatsAreIncreasingWithBoundedGaps() {
        long[] b = DefibScore.beats(7, 12);
        assertTrue(b[0] >= 1500);
        for (int i = 1; i < b.length; i++) {
            long gap = b[i] - b[i - 1];
            assertTrue(gap >= 550 && gap <= 900, "gap " + gap);
        }
    }

    @Test
    void perfectTapsScoreOne() {
        long[] b = DefibScore.beats(1, 12);
        assertEquals(1.0, DefibScore.evaluate(b, b.clone(), end(b)), 1e-9);
    }

    @Test
    void noTapsScoreZero() {
        long[] b = DefibScore.beats(1, 12);
        assertEquals(0.0, DefibScore.evaluate(b, new long[0], end(b)), 1e-9);
    }

    @Test
    void goodTapsScoreHalf() {
        long[] b = DefibScore.beats(1, 12);
        assertEquals(0.5, DefibScore.evaluate(b, shifted(b, 150), end(b)), 1e-9);
    }

    @Test
    void lateTapsScoreZero() {
        long[] b = DefibScore.beats(1, 12);
        assertEquals(0.0, DefibScore.evaluate(b, shifted(b, 300), end(b)), 1e-9);
    }

    @Test
    void spamIsRejected() {
        long[] b = DefibScore.beats(1, 12);
        long[] taps = new long[25];
        for (int i = 0; i < taps.length; i++) taps[i] = i < 12 ? b[i] : b[11] + 10L * (i - 11);
        assertEquals(0.0, DefibScore.evaluate(b, taps, end(b)), 1e-9);
    }

    @Test
    void tooFastIsRejected() {
        long[] b = DefibScore.beats(1, 12);
        assertEquals(0.0, DefibScore.evaluate(b, b.clone(), b[11] - 600), 1e-9);
    }

    @Test
    void oneTapCannotMatchTwoBeats() {
        long[] b = DefibScore.beats(1, 12);
        assertEquals(1.0 / 12, DefibScore.evaluate(b, new long[]{b[0]}, end(b)), 1e-9);
    }
}
