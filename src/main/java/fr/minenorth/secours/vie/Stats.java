package fr.minenorth.secours.vie;

import net.minecraft.nbt.CompoundTag;

/** Statistiques de mode de vie d'un joueur (0 à 100). Sauvegardées côté serveur (VieData). */
public final class Stats {
    public double forme = 30, nutrition = 50, repos = 70, toxines = 0;
    /** Maladie en cours (id) et minutes de jeu restantes. */
    public String disease = "";
    public double diseaseLeft;
    /** Bonus d'immunité temporaire (vitamines, vaccin) : valeur et minutes restantes. */
    public double boost, boostLeft;
    /** Compléments de sport : minutes restantes de whey (+40 % de forme gagnée) et de créatine (+25 %). */
    public double wheyLeft, creaLeft;
    /** Derniers aliments (anti-monotonie). */
    public final java.util.ArrayDeque<String> lastFoods = new java.util.ArrayDeque<>();
    /** Minutes de jeu depuis le dernier entraînement ; gains de forme de l'heure en cours. */
    public double idleMinutes, hourGain, hourClock;

    public static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    /** Immunité de 0 à 100 : forme, alimentation et repos la renforcent, les toxines l'affaiblissent. */
    public double immunity() {
        return clamp(0.42 * forme + 0.33 * nutrition + 0.25 * repos - 0.5 * toxines + (boostLeft > 0 ? boost : 0));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("forme", forme); t.putDouble("nutrition", nutrition); t.putDouble("repos", repos); t.putDouble("toxines", toxines);
        t.putString("maladie", disease); t.putDouble("reste", diseaseLeft); t.putDouble("boost", boost); t.putDouble("boostReste", boostLeft);
        t.putDouble("wheyReste", wheyLeft); t.putDouble("creaReste", creaLeft);
        t.putDouble("inactif", idleMinutes); t.putDouble("gainHeure", hourGain); t.putDouble("horloge", hourClock);
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (String f : lastFoods) l.add(net.minecraft.nbt.StringTag.valueOf(f));
        t.put("aliments", l);
        return t;
    }

    public static Stats load(CompoundTag t) {
        Stats s = new Stats();
        s.forme = clamp(t.getDouble("forme")); s.nutrition = clamp(t.getDouble("nutrition")); s.repos = clamp(t.getDouble("repos")); s.toxines = clamp(t.getDouble("toxines"));
        s.disease = t.getString("maladie"); s.diseaseLeft = Math.max(0, t.getDouble("reste")); s.boost = t.getDouble("boost"); s.boostLeft = Math.max(0, t.getDouble("boostReste"));
        s.wheyLeft = Math.max(0, t.getDouble("wheyReste")); s.creaLeft = Math.max(0, t.getDouble("creaReste"));
        s.idleMinutes = t.getDouble("inactif"); s.hourGain = t.getDouble("gainHeure"); s.hourClock = t.getDouble("horloge");
        for (net.minecraft.nbt.Tag x : t.getList("aliments", net.minecraft.nbt.Tag.TAG_STRING)) s.lastFoods.add(x.getAsString());
        if (!t.contains("forme")) { s.forme = 30; s.nutrition = 50; s.repos = 70; }
        return s;
    }
}
