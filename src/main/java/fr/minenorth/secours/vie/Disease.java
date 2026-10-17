package fr.minenorth.secours.vie;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import javax.annotation.Nullable;
import java.util.List;

/** Maladies : jamais mortelles (les effets ne tuent pas). Durées en minutes de jeu. */
public enum Disease {
    RHUME("rhume", "Rhume", 8, true, -0.04, 0, List.of(), "Vous avez attrapé un rhume : vous reniflez, vos bonus de forme sont suspendus le temps de guérir."),
    GRIPPE("grippe", "Grippe", 15, true, -0.08, 1, List.of(new Fx(MobEffects.WEAKNESS, 0)), "Vous avez la grippe : vos bonus de forme sont suspendus le temps de guérir."),
    GASTRO("gastro", "Gastro-entérite", 6, true, 0.0, 0, List.of(new Fx(MobEffects.HUNGER, 0)), "Vous avez une gastro : vos bonus de forme sont suspendus le temps de guérir."),
    INTOXICATION("intoxication", "Intoxication alimentaire", 5, false, 0.0, 0, List.of(new Fx(MobEffects.HUNGER, 0)), "Vous avez mangé quelque chose de pas net : vos bonus de forme sont suspendus le temps de guérir.");

    /** Effet appliqué en continu (niveau 0 = I). */
    public record Fx(MobEffect effect, int level) {}

    public final String id, label, onset;
    public final int minutes;
    /** Peut se transmettre à un joueur proche. */
    public final boolean contagious;
    /** Malus de vitesse (multiplicateur de base, négatif). */
    public final double speed;
    /** Points de vie en moins : 0 = aucun, 1 = la pénalité de la config. */
    public final int healthPenalty;
    public final List<Fx> effects;

    Disease(String id, String label, int minutes, boolean contagious, double speed, int healthPenalty, List<Fx> effects, String onset) {
        this.id = id; this.label = label; this.minutes = minutes; this.contagious = contagious; this.speed = speed;
        this.healthPenalty = healthPenalty; this.effects = effects; this.onset = onset;
    }

    @Nullable
    public static Disease byId(String id) {
        for (Disease d : values()) if (d.id.equals(id)) return d;
        return null;
    }
}
