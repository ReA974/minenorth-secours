package fr.minenorth.secours.vie;

import fr.minenorth.api.MineNorth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abonnements à une salle de sport : une salle = une entreprise (identifiant de l'API MineNorth). Chaque abonné a une date de fin (temps réel,
 * donc l'abonnement continue de courir hors connexion). Sauvegardé avec le monde ; effacé par le panneau admin avec le reste du joueur.
 */
public class GymPass extends SavedData {
    private static final String NAME = "minenorth_salle_abonnements";

    private final Map<Integer, Map<UUID, Long>> passes = new HashMap<>();

    public static GymPass get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(GymPass::load, GymPass::new, NAME);
    }

    public long expiry(int company, UUID id) {
        Map<UUID, Long> m = passes.get(company);
        Long v = m == null ? null : m.get(id);
        return v == null ? 0 : v;
    }

    public boolean valid(int company, UUID id) { return expiry(company, id) > System.currentTimeMillis(); }

    /** Ajoute des jours à l'abonnement (à partir de maintenant, ou de sa fin s'il court encore). Renvoie la nouvelle date de fin. */
    public long grant(int company, UUID id, int days) {
        long now = System.currentTimeMillis();
        long from = Math.max(now, expiry(company, id));
        long until = from + days * 86_400_000L;
        passes.computeIfAbsent(company, k -> new HashMap<>()).put(id, until);
        setDirty();
        return until;
    }

    /** Abonnés encore valides d'une salle (UUID -> date de fin). */
    public Map<UUID, Long> entries(int company) {
        Map<UUID, Long> out = new HashMap<>();
        Map<UUID, Long> m = passes.get(company);
        if (m != null) { long now = System.currentTimeMillis(); m.forEach((k, v) -> { if (v > now) out.put(k, v); }); }
        return out;
    }

    public boolean remove(int company, UUID id) {
        Map<UUID, Long> m = passes.get(company);
        boolean r = m != null && m.remove(id) != null;
        if (r) setDirty();
        return r;
    }

    public void removePlayer(UUID id) {
        boolean changed = false;
        for (Map<UUID, Long> m : passes.values()) changed |= m.remove(id) != null;
        if (changed) setDirty();
    }

    /** Le joueur peut-il passer le portique de cette entreprise ? Abonné, membre de l'entreprise ou opérateur. */
    public static boolean allowed(ServerPlayer p, int company) {
        if (company <= 0 || p.hasPermissions(2)) return true;
        return MineNorth.companies().isMember(p, company, false) || get(p.server).valid(company, p.getUUID());
    }

    public static GymPass load(CompoundTag tag) {
        GymPass d = new GymPass();
        ListTag l = tag.getList("salles", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            Map<UUID, Long> m = new HashMap<>();
            ListTag a = c.getList("abonnes", Tag.TAG_COMPOUND);
            for (int j = 0; j < a.size(); j++) {
                CompoundTag t = a.getCompound(j);
                if (t.hasUUID("id")) m.put(t.getUUID("id"), t.getLong("fin"));
            }
            d.passes.put(c.getInt("entreprise"), m);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Map.Entry<Integer, Map<UUID, Long>> e : passes.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("entreprise", e.getKey());
            ListTag a = new ListTag();
            for (Map.Entry<UUID, Long> x : e.getValue().entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putUUID("id", x.getKey());
                t.putLong("fin", x.getValue());
                a.add(t);
            }
            c.put("abonnes", a);
            l.add(c);
        }
        tag.put("salles", l);
        return tag;
    }
}
