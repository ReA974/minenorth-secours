package fr.minenorth.secours;

import fr.minenorth.secours.compat.MapBridge;
import fr.minenorth.secours.config.SecoursConfig;
import fr.minenorth.secours.data.SecoursData;
import fr.minenorth.secours.data.SecoursData.Incident;
import fr.minenorth.secours.data.SecoursData.Injury;
import fr.minenorth.secours.data.SecoursData.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Incendies de service : quand des pompiers sont en service, un incendie se déclenche sur un des sites définis par les OP
 * (/secours incendie ajouter). Il apparaît sur la carte (mod minenorth_map) des pompiers en service ; « J'y vais » sur la
 * tablette lance le guidage. Même guidage vers un blessé inconscient.
 */
@Mod.EventBusSubscriber
public final class FireService {
    public static final String MARK_FIRE = "Incendie : ", MARK_HURT = "Blessé : ";
    private static final int FIRE_COLOR = 0xFF5500, HURT_COLOR = 0xFF3355;
    private static final Random RNG = new Random();

    private record Guide(UUID victim, int x, int y, int z) {}

    /** Unité -> blessé vers lequel la carte la guide. */
    private static final Map<UUID, Guide> GUIDES = new HashMap<>();
    private static long pendingStart, nextAuto, noDutySince;

    private FireService() {}

    // ------------------------------------------------------------------ utilitaires

    private static ServerLevel level(MinecraftServer s, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    private static List<ServerPlayer> onDuty(MinecraftServer s) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (SecoursService.onDuty(p)) out.add(p);
        return out;
    }

    private static String markName(Incident i) { return MARK_FIRE + i.site; }

    private static void tell(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal(text)); }

    private static String dimOf(ServerPlayer p) { return p.level().dimension().location().toString(); }

    // ------------------------------------------------------------------ prise / fin de service

    public static void onDutyChanged(ServerPlayer p, boolean on) {
        MinecraftServer s = p.server;
        SecoursData d = SecoursData.get(s);
        if (!on) {
            MapBridge.clear(p, MARK_FIRE);
            MapBridge.clear(p, MARK_HURT);
            GUIDES.remove(p.getUUID());
            return;
        }
        for (Incident i : d.incidents.values()) showOn(p, i, false);
        SecoursConfig cfg = SecoursConfig.get();
        if (cfg.incendies_actifs && d.incidents.isEmpty() && !d.sites.isEmpty() && pendingStart == 0) {
            pendingStart = System.currentTimeMillis() + Math.max(0, cfg.incendie_delai_secondes) * 1000L;
        }
    }

    public static void onLogout(UUID id) { GUIDES.remove(id); }

    private static void showOn(ServerPlayer p, Incident i, boolean guide) {
        MapBridge.set(p, markName(i), i.dim, i.x, i.y, i.z, FIRE_COLOR, guide);
    }

    // ------------------------------------------------------------------ déclenchement

    /** Déclenche un incendie (site donné, sinon au hasard parmi ceux qui ne brûlent pas déjà). Renvoie le message de résultat. */
    public static String start(MinecraftServer s, String siteName) {
        SecoursData d = SecoursData.get(s);
        List<Site> free = new ArrayList<>();
        for (Site st : d.sites.values()) {
            boolean busy = false;
            for (Incident i : d.incidents.values()) if (i.site.equalsIgnoreCase(st.name)) busy = true;
            if (busy) continue;
            if (siteName == null || st.name.equalsIgnoreCase(siteName)) free.add(st);
        }
        if (free.isEmpty()) return siteName == null ? "Aucun site d'incendie libre (ajoutez-en avec /secours incendie ajouter)." : "Site inconnu ou déjà en feu : " + siteName;
        Site st = free.get(RNG.nextInt(free.size()));
        ServerLevel level = level(s, st.dim);
        if (level == null) return "Dimension introuvable pour le site " + st.name + ".";

        List<Long> fires = ignite(level, st, Math.max(1, SecoursConfig.get().incendie_rayon));
        if (fires.isEmpty()) return "Impossible d'allumer un feu sur le site " + st.name + " (pas de sol plein ?).";

        Incident inc = new Incident();
        inc.id = UUID.randomUUID();
        inc.site = st.name; inc.dim = st.dim; inc.x = st.x; inc.y = st.y; inc.z = st.z;
        inc.startMs = System.currentTimeMillis();
        inc.fires.addAll(fires);
        d.incidents.put(inc.id, inc);
        d.setDirty();
        for (ServerPlayer p : onDuty(s)) {
            showOn(p, inc, false);
            tell(p, "§c§l[Secours] INCENDIE à " + st.name + " §r§c(" + st.x + " " + st.y + " " + st.z + "). Tablette : « J'y vais » pour être guidé sur la carte.");
        }
        return "Incendie déclenché : " + st.name + " (" + fires.size() + " foyers).";
    }

    private static List<Long> ignite(ServerLevel level, Site st, int radius) {
        List<Long> out = new ArrayList<>();
        int wanted = 4 + RNG.nextInt(4);
        level.getChunkAt(new BlockPos(st.x, st.y, st.z)); // charge la zone si besoin
        for (int tries = 0; tries < 60 && out.size() < wanted; tries++) {
            int x = st.x + (tries == 0 ? 0 : RNG.nextInt(radius * 2 + 1) - radius);
            int z = st.z + (tries == 0 ? 0 : RNG.nextInt(radius * 2 + 1) - radius);
            for (int y = st.y + 2; y >= st.y - 3; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!canBurnAt(level, pos)) continue;
                if (!out.contains(pos.asLong())) {
                    level.setBlock(pos, BaseFireBlock.getState(level, pos), 3);
                    out.add(pos.asLong());
                }
                break;
            }
        }
        return out;
    }

    private static boolean canBurnAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    // ------------------------------------------------------------------ « J'y vais »

    /** Un pompier annonce qu'il va à l'incendie : ajouté aux unités en route + guidage sur sa carte. */
    public static void dispatch(ServerPlayer p, Incident inc) {
        MinecraftServer s = p.server;
        SecoursData d = SecoursData.get(s);
        String unit = SecoursService.display(s, p.getUUID());
        boolean already = false;
        for (String u : inc.dispatch.split(", ")) if (u.equals(unit)) already = true;
        if (!already) {
            inc.dispatch = inc.dispatch.isEmpty() ? unit : inc.dispatch + ", " + unit;
            d.setDirty();
            for (ServerPlayer q : onDuty(s)) if (q != p) tell(q, "§b[Secours] " + unit + " est en route vers l'incendie de " + inc.site + ".");
        }
        showOn(p, inc, true);
    }

    /** Guidage de la carte vers un joueur inconscient (« J'y vais » sur son alerte). */
    public static void guideTo(ServerPlayer unit, ServerPlayer victim) {
        if (!MapBridge.available()) return;
        MapBridge.clear(unit, MARK_HURT);
        BlockPos b = victim.blockPosition();
        MapBridge.set(unit, MARK_HURT + SecoursService.display(unit.server, victim.getUUID()), dimOf(victim), b.getX(), b.getY(), b.getZ(), HURT_COLOR, true);
        GUIDES.put(unit.getUUID(), new Guide(victim.getUUID(), b.getX(), b.getY(), b.getZ()));
    }

    // ------------------------------------------------------------------ boucle (une fois par seconde)

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = e.getServer();
        SecoursConfig cfg = SecoursConfig.get();
        SecoursData d = SecoursData.get(s);
        long now = System.currentTimeMillis();
        List<ServerPlayer> duty = onDuty(s);

        tickGuides(s, d, duty);

        if (duty.isEmpty()) {
            pendingStart = 0; nextAuto = 0;
            if (!d.incidents.isEmpty()) {
                if (noDutySince == 0) noDutySince = now;
                else if (now - noDutySince > 90_000L) closeAll(s, d, null);   // plus personne : on éteint, sinon ça brûle sans fin
            } else noDutySince = 0;
            return;
        }
        noDutySince = 0;

        if (cfg.incendies_actifs && !d.sites.isEmpty() && d.incidents.size() < Math.max(1, cfg.incendie_max_simultanes)) {
            if (pendingStart != 0 && now >= pendingStart) {
                pendingStart = 0;
                start(s, null);
                nextAuto = now + SecoursConfig.ms(Math.max(1, cfg.incendie_intervalle_minutes));
            } else if (pendingStart == 0) {
                if (nextAuto == 0) nextAuto = now + SecoursConfig.ms(Math.max(1, cfg.incendie_intervalle_minutes));
                else if (now >= nextAuto) {
                    start(s, null);
                    nextAuto = now + SecoursConfig.ms(Math.max(1, cfg.incendie_intervalle_minutes));
                }
            }
        }

        for (Incident inc : new ArrayList<>(d.incidents.values())) tickIncident(s, d, inc, now, cfg, duty);
    }

    private static void tickIncident(MinecraftServer s, SecoursData d, Incident inc, long now, SecoursConfig cfg, List<ServerPlayer> duty) {
        ServerLevel level = level(s, inc.dim);
        long limit = inc.startMs + SecoursConfig.ms(Math.max(1, cfg.incendie_duree_max_minutes));
        if (level == null) { close(s, d, inc, null); return; }
        if (now >= limit) {
            for (long l : inc.fires) {
                BlockPos pos = BlockPos.of(l);
                if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            close(s, d, inc, "§c[Secours] L'incendie de " + inc.site + " n'a pas été maîtrisé à temps (le feu s'est éteint).");
            return;
        }
        boolean changed = false;
        for (Long l : new ArrayList<>(inc.fires)) {
            BlockPos pos = BlockPos.of(l);
            if (!level.isLoaded(pos)) continue;
            BlockState st = level.getBlockState(pos);
            if (st.getBlock() instanceof BaseFireBlock) continue;
            if (!st.isAir()) { inc.fires.remove(l); changed = true; continue; }   // eau ou bloc posé : éteint
            if (canBurnAt(level, pos)) level.setBlock(pos, BaseFireBlock.getState(level, pos), 3); // simple extinction naturelle : le feu repart
            else { inc.fires.remove(l); changed = true; }
        }
        if (changed) d.setDirty();
        if (inc.fires.isEmpty()) {
            long minutes = Math.max(1, (now - inc.startMs) / 60_000L);
            close(s, d, inc, "§a[Secours] Incendie maîtrisé à " + inc.site + " en " + minutes + " min. Bien joué !");
        }
    }

    private static void close(MinecraftServer s, SecoursData d, Incident inc, String message) {
        d.incidents.remove(inc.id);
        d.setDirty();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            MapBridge.remove(p, markName(inc));
            if (message != null && SecoursService.onDuty(p)) tell(p, message);
        }
    }

    /** Éteint tous les incendies (commande, ou plus aucun pompier en service). */
    public static int closeAll(MinecraftServer s, SecoursData d, String message) {
        int n = 0;
        for (Incident inc : new ArrayList<>(d.incidents.values())) {
            ServerLevel level = level(s, inc.dim);
            if (level != null) for (long l : inc.fires) {
                BlockPos pos = BlockPos.of(l);
                if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BaseFireBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            close(s, d, inc, message);
            n++;
        }
        return n;
    }

    private static void tickGuides(MinecraftServer s, SecoursData d, List<ServerPlayer> duty) {
        for (Map.Entry<UUID, Guide> en : new HashMap<>(GUIDES).entrySet()) {
            ServerPlayer unit = s.getPlayerList().getPlayer(en.getKey());
            ServerPlayer victim = s.getPlayerList().getPlayer(en.getValue().victim());
            Injury j = victim == null ? null : d.peek(victim.getUUID());
            if (unit == null) { GUIDES.remove(en.getKey()); continue; }
            if (victim == null || j == null || !j.coma || !duty.contains(unit)) {
                MapBridge.clear(unit, MARK_HURT);
                GUIDES.remove(en.getKey());
                continue;
            }
            if (unit.level() == victim.level() && unit.distanceToSqr(victim) < 36) {   // arrivé
                MapBridge.clear(unit, MARK_HURT);
                GUIDES.remove(en.getKey());
                continue;
            }
            BlockPos b = victim.blockPosition();
            Guide g = en.getValue();
            if (Math.abs(b.getX() - g.x()) + Math.abs(b.getZ() - g.z()) >= 3) {      // le blessé a été déplacé
                MapBridge.clear(unit, MARK_HURT);
                MapBridge.set(unit, MARK_HURT + SecoursService.display(s, victim.getUUID()), dimOf(victim), b.getX(), b.getY(), b.getZ(), HURT_COLOR, true);
                GUIDES.put(en.getKey(), new Guide(g.victim(), b.getX(), b.getY(), b.getZ()));
            }
        }
    }

    // ------------------------------------------------------------------ feu éteint à la main

    private static void extinguished(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl)) return;
        SecoursData d = SecoursData.get(sl.getServer());
        for (Incident inc : d.incidents.values()) {
            if (inc.fires.remove((Long) pos.asLong())) { d.setDirty(); return; }
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getState().getBlock() instanceof BaseFireBlock) extinguished(e.getPlayer().level(), e.getPos());
    }

    @SubscribeEvent
    public static void onPunch(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getLevel().getBlockState(e.getPos()).getBlock() instanceof BaseFireBlock) extinguished(e.getLevel(), e.getPos());
    }
}
