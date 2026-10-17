package fr.minenorth.secours.vie;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Statistiques de tous les joueurs (par UUID), sauvegardées avec le monde. */
public class VieData extends SavedData {
    private static final String NAME = "minenorth_vie";

    private final Map<UUID, Stats> stats = new HashMap<>();

    public static VieData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(VieData::load, VieData::new, NAME);
    }

    public Stats of(UUID id) { return stats.computeIfAbsent(id, k -> { setDirty(); return new Stats(); }); }

    public boolean has(UUID id) { return stats.containsKey(id); }

    public void remove(UUID id) { if (stats.remove(id) != null) setDirty(); }

    public static VieData load(CompoundTag tag) {
        VieData d = new VieData();
        ListTag l = tag.getList("joueurs", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            if (t.hasUUID("id")) d.stats.put(t.getUUID("id"), Stats.load(t.getCompound("stats")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, Stats> e : stats.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.getKey());
            t.put("stats", e.getValue().save());
            l.add(t);
        }
        tag.put("joueurs", l);
        return tag;
    }
}
