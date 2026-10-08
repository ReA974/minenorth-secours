package fr.minenorth.secours.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Blessures des joueurs, effectifs des secours et position de l'hôpital. Sauvegardé avec le monde. */
public class SecoursData extends SavedData {
    private static final String NAME = "minenorth_secours";

    public static final int AUCUNE = 0, LEGERE = 1, MOYENNE = 2, GRAVE = 3;
    public static final String[] NIVEAUX = {"Aucune", "Blessure légère", "Blessure moyenne", "Blessure grave"};
    public static final int CHEF = 0, MEDECIN = 1, SECOURISTE = 2;
    public static final String[] GRADES = {"Chef des secours", "Médecin", "Secouriste"};

    public static final class Injury {
        public int level;
        /** Heure (ms) de guérison naturelle ; 0 = ne guérit pas seule. */
        public long healAt;
        public boolean bleeding, coma;
        /** Zones touchées par balle (masque de bits, voir TaczCompat). Effacé quand la blessure est soignée. */
        public int zones;
        /** Heure (ms) du réveil à l'hôpital si personne n'intervient. */
        public long comaDeadline;
        /** Nom de l'unité qui a annoncé être en route. */
        public String dispatch = "";

        public boolean empty() { return level == AUCUNE && !bleeding && !coma; }
    }

    public final Map<UUID, Injury> injuries = new LinkedHashMap<>();
    /** Site où un incendie peut se déclencher. */
    public static final class Site {
        public String name = "", dim = "";
        public int x, y, z;
    }

    /** Incendie en cours : positions des foyers (BlockPos.asLong) encore à éteindre. */
    public static final class Incident {
        public UUID id = UUID.randomUUID();
        public String site = "", dim = "", dispatch = "";
        public int x, y, z;
        public long startMs;
        public final java.util.List<Long> fires = new java.util.ArrayList<>();
    }

    public final Map<String, Site> sites = new LinkedHashMap<>();
    public final Map<UUID, Incident> incidents = new LinkedHashMap<>();
    public final Map<UUID, Integer> staff = new LinkedHashMap<>();
    public final Map<UUID, String> names = new LinkedHashMap<>();
    /** Secouristes actuellement en service (non sauvegardé : tout le monde est hors service au redémarrage). */
    public final java.util.Set<UUID> onDuty = new java.util.HashSet<>();
    /** Dossier médical : uuid -> lignes "horodatage|texte", les plus anciennes d'abord. */
    public final Map<UUID, java.util.List<String>> files = new LinkedHashMap<>();

    public void note(UUID citizen, String text) {
        java.util.List<String> list = files.computeIfAbsent(citizen, k -> new java.util.ArrayList<>());
        list.add(System.currentTimeMillis() + "|" + text);
        while (list.size() > 60) list.remove(0);
        setDirty();
    }
    public boolean hasHospital;
    public String hospitalDim = "";
    public double hx, hy, hz;

    public static SecoursData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SecoursData::load, SecoursData::new, NAME);
    }

    public Injury injury(UUID id) { return injuries.computeIfAbsent(id, k -> new Injury()); }
    public Injury peek(UUID id) { return injuries.get(id); }
    public String name(UUID id) { String n = names.get(id); return n != null ? n : id.toString().substring(0, 8); }
    public UUID byName(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) if (e.getValue().equalsIgnoreCase(name.trim())) return e.getKey();
        return null;
    }

    public static SecoursData load(CompoundTag tag) {
        SecoursData d = new SecoursData();
        d.hasHospital = tag.getBoolean("hasHospital"); d.hospitalDim = tag.getString("hospitalDim");
        d.hx = tag.getDouble("hx"); d.hy = tag.getDouble("hy"); d.hz = tag.getDouble("hz");
        ListTag il = tag.getList("injuries", Tag.TAG_COMPOUND);
        for (int i = 0; i < il.size(); i++) {
            CompoundTag t = il.getCompound(i);
            Injury j = new Injury();
            j.level = t.getInt("level"); j.healAt = t.getLong("healAt"); j.bleeding = t.getBoolean("bleeding");
            j.coma = t.getBoolean("coma"); j.zones = t.getInt("zones"); j.comaDeadline = t.getLong("comaDeadline"); j.dispatch = t.getString("dispatch");
            d.injuries.put(t.getUUID("id"), j);
        }
        ListTag stl = tag.getList("sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < stl.size(); i++) {
            CompoundTag t = stl.getCompound(i);
            Site s = new Site();
            s.name = t.getString("name"); s.dim = t.getString("dim"); s.x = t.getInt("x"); s.y = t.getInt("y"); s.z = t.getInt("z");
            d.sites.put(s.name.toLowerCase(java.util.Locale.ROOT), s);
        }
        ListTag inl = tag.getList("incidents", Tag.TAG_COMPOUND);
        for (int i = 0; i < inl.size(); i++) {
            CompoundTag t = inl.getCompound(i);
            Incident n = new Incident();
            n.id = t.getUUID("id"); n.site = t.getString("site"); n.dim = t.getString("dim"); n.dispatch = t.getString("dispatch");
            n.x = t.getInt("x"); n.y = t.getInt("y"); n.z = t.getInt("z"); n.startMs = t.getLong("start");
            for (long l : t.getLongArray("fires")) n.fires.add(l);
            d.incidents.put(n.id, n);
        }
        ListTag sl = tag.getList("staff", Tag.TAG_COMPOUND);
        for (int i = 0; i < sl.size(); i++) { CompoundTag t = sl.getCompound(i); d.staff.put(t.getUUID("id"), t.getInt("grade")); }
        ListTag nl = tag.getList("names", Tag.TAG_COMPOUND);
        for (int i = 0; i < nl.size(); i++) { CompoundTag t = nl.getCompound(i); d.names.put(t.getUUID("id"), t.getString("name")); }
        ListTag fl = tag.getList("files", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            CompoundTag t = fl.getCompound(i);
            java.util.List<String> lines = new java.util.ArrayList<>();
            ListTag ll = t.getList("lines", Tag.TAG_STRING);
            for (int k = 0; k < ll.size(); k++) lines.add(ll.getString(k));
            d.files.put(t.getUUID("id"), lines);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("hasHospital", hasHospital); tag.putString("hospitalDim", hospitalDim);
        tag.putDouble("hx", hx); tag.putDouble("hy", hy); tag.putDouble("hz", hz);
        ListTag il = new ListTag();
        injuries.forEach((id, j) -> {
            if (j.empty()) return;
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id); t.putInt("level", j.level); t.putLong("healAt", j.healAt); t.putBoolean("bleeding", j.bleeding);
            t.putBoolean("coma", j.coma); t.putInt("zones", j.zones); t.putLong("comaDeadline", j.comaDeadline); t.putString("dispatch", j.dispatch);
            il.add(t);
        });
        tag.put("injuries", il);
        ListTag stl = new ListTag();
        for (Site s : sites.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("name", s.name); t.putString("dim", s.dim); t.putInt("x", s.x); t.putInt("y", s.y); t.putInt("z", s.z);
            stl.add(t);
        }
        tag.put("sites", stl);
        ListTag inl = new ListTag();
        for (Incident n : incidents.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", n.id); t.putString("site", n.site); t.putString("dim", n.dim); t.putString("dispatch", n.dispatch);
            t.putInt("x", n.x); t.putInt("y", n.y); t.putInt("z", n.z); t.putLong("start", n.startMs);
            t.putLongArray("fires", n.fires.stream().mapToLong(Long::longValue).toArray());
            inl.add(t);
        }
        tag.put("incidents", inl);
        ListTag sl = new ListTag();
        staff.forEach((id, g) -> { CompoundTag t = new CompoundTag(); t.putUUID("id", id); t.putInt("grade", g); sl.add(t); });
        tag.put("staff", sl);
        ListTag nl = new ListTag();
        names.forEach((id, n) -> { CompoundTag t = new CompoundTag(); t.putUUID("id", id); t.putString("name", n); nl.add(t); });
        tag.put("names", nl);
        ListTag fl = new ListTag();
        files.forEach((id, lines) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            ListTag ll = new ListTag();
            for (String line : lines) ll.add(net.minecraft.nbt.StringTag.valueOf(line));
            t.put("lines", ll);
            fl.add(t);
        });
        tag.put("files", fl);
        return tag;
    }
}
