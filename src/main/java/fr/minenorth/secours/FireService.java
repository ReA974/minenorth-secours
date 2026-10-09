package fr.minenorth.secours;

import fr.minenorth.api.MineNorth;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

    /**
     * Âge (0-15) de chaque foyer au dernier passage. Une flamme qui disparaît « jeune » a été éteinte (extincteur ou camion MTS,
     * eau, main) ; une flamme usée ou sous la pluie s'est simplement éteinte seule et repart.
     */
    private static final Map<Long, Integer> LAST_AGE = new HashMap<>();
    private static final int NATURAL_AGE = 13;

    // État de suivi des incendies (en mémoire : repart de zéro au redémarrage du serveur).
    private static final Map<UUID, Long> NEXT_SPREAD = new HashMap<>();
    private static final Map<UUID, Long> REKINDLE_AT = new HashMap<>();
    private static final Map<UUID, Integer> REKINDLES = new HashMap<>();
    /** incendie -> (pompier -> secondes passées à moins de incendie_prime_distance du site). */
    private static final Map<UUID, Map<UUID, Integer>> PRESENCE = new HashMap<>();
    /** Mode calme actif (trop de blessés à soigner). */
    private static boolean calm;

    private static int ageOf(BlockState st) {
        return st.hasProperty(BlockStateProperties.AGE_15) ? st.getValue(BlockStateProperties.AGE_15) : 0;
    }

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

        SecoursConfig cf = SecoursConfig.get();
        int wanted = cf.incendie_foyers_min + RNG.nextInt(Math.max(1, cf.incendie_foyers_max - cf.incendie_foyers_min + 1));
        List<Long> fires = ignite(level, st, Math.max(1, cf.incendie_rayon), wanted, 60);
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

    private static List<Long> ignite(ServerLevel level, Site st, int radius, int wanted, int maxTries) {
        List<Long> out = new ArrayList<>();
        level.getChunkAt(new BlockPos(st.x, st.y, st.z)); // charge la zone si besoin
        for (int tries = 0; tries < maxTries && out.size() < wanted; tries++) {
            int x = st.x + (tries == 0 ? 0 : RNG.nextInt(radius * 2 + 1) - radius);
            int z = st.z + (tries == 0 ? 0 : RNG.nextInt(radius * 2 + 1) - radius);
            for (int y = st.y + 2; y >= st.y - 3; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!canBurnAt(level, pos)) continue;
                if (!out.contains(pos.asLong()) && !(level.getBlockState(pos).getBlock() instanceof BaseFireBlock)) {
                    level.setBlock(pos, BaseFireBlock.getState(level, pos), 3);
                    out.add(pos.asLong());
                    LAST_AGE.put(pos.asLong(), 0);
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

        // Trop de blessés à prendre en charge : on calme le jeu (pas de nouvel incendie, ni propagation, ni reprise).
        boolean over = overloaded(s, d, cfg, duty);
        if (over != calm) {
            calm = over;
            for (ServerPlayer p : duty) tell(p, over
                    ? "§e[Secours] Beaucoup de blessés à prendre en charge : plus de nouveaux incendies tant que la situation n'est pas calmée."
                    : "§a[Secours] La situation est calmée : les incendies peuvent reprendre.");
        }

        if (!over && cfg.incendies_actifs && !d.sites.isEmpty() && d.incidents.size() < Math.max(1, cfg.incendie_max_simultanes)) {
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

        for (Incident inc : new ArrayList<>(d.incidents.values())) tickIncident(s, d, inc, now, cfg, duty, over);
    }

    /** Charge de soins : blessés à prendre en charge (un pompier blessé compte double) comparée au nombre de pompiers en service. */
    private static boolean overloaded(MinecraftServer s, SecoursData d, SecoursConfig cfg, List<ServerPlayer> duty) {
        if (!cfg.incendie_calme_actif) return false;
        int load = 0;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Injury j = d.peek(p.getUUID());
            if (j == null) continue;
            if (!(j.coma || j.bleeding || j.level >= cfg.incendie_calme_niveau_min)) continue;
            load += duty.contains(p) ? 2 : 1;
        }
        return load >= Math.max(2, duty.size() * Math.max(1, cfg.incendie_blesses_par_pompier));
    }

    /** Foyers supplémentaires autour du site (propagation ou reprise). Renvoie ceux qui ont réellement pris. */
    private static List<Long> igniteMore(ServerLevel level, Incident inc, SecoursConfig cfg, int wanted) {
        Site st = new Site();
        st.name = inc.site; st.dim = inc.dim; st.x = inc.x; st.y = inc.y; st.z = inc.z;
        return ignite(level, st, Math.max(1, cfg.incendie_rayon) + cfg.incendie_propagation_rayon_bonus, wanted, 30);
    }

    /** Prime aux pompiers en service qui sont restés assez longtemps sur place. Source banque : « secours:prime-incendie ». */
    private static void reward(MinecraftServer s, Incident inc, SecoursConfig cfg) {
        long cents = SecoursConfig.cents(cfg.incendie_prime_euros);
        Map<UUID, Integer> seen = PRESENCE.get(inc.id);
        if (cents <= 0 || seen == null) return;
        for (Map.Entry<UUID, Integer> en : seen.entrySet()) {
            if (en.getValue() < cfg.incendie_prime_presence_secondes) continue;
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            if (p == null || !SecoursService.onDuty(p)) continue;
            if (MineNorth.bank().refund(s, p.getUUID(), cents, "secours:prime-incendie"))
                tell(p, "§a[Secours] Prime d'intervention : " + SecoursService.money(cents) + " versés sur votre compte.");
            else tell(p, "§e[Secours] Prime d'intervention non versée (pas de compte bancaire ou trésor insuffisant).");
        }
    }

    private static void tickIncident(MinecraftServer s, SecoursData d, Incident inc, long now, SecoursConfig cfg, List<ServerPlayer> duty, boolean over) {
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
        // Présence des pompiers en service sur les lieux (pour la prime).
        double maxD2 = (double) cfg.incendie_prime_distance * cfg.incendie_prime_distance;
        for (ServerPlayer p : duty) {
            if (!dimOf(p).equals(inc.dim)) continue;
            double dx = p.getX() - inc.x, dz = p.getZ() - inc.z;
            if (dx * dx + dz * dz <= maxD2) PRESENCE.computeIfAbsent(inc.id, k -> new HashMap<>()).merge(p.getUUID(), 1, Integer::sum);
        }
        boolean changed = false;
        long activeUntil = inc.startMs + SecoursConfig.ms(cfg.incendie_duree_min_minutes);
        // Propagation : tant que la durée minimale n'est pas écoulée et que tout va bien, de nouveaux foyers apparaissent.
        if (!over && cfg.incendie_propagation_secondes > 0 && now < activeUntil && inc.fires.size() < cfg.incendie_foyers_max
                && now >= NEXT_SPREAD.getOrDefault(inc.id, inc.startMs + cfg.incendie_propagation_secondes * 1000L)) {
            List<Long> more = igniteMore(level, inc, cfg, Math.min(1 + RNG.nextInt(2), cfg.incendie_foyers_max - inc.fires.size()));
            inc.fires.addAll(more);
            NEXT_SPREAD.put(inc.id, now + cfg.incendie_propagation_secondes * 1000L);
            if (!more.isEmpty()) changed = true;
        }
        for (Long l : new ArrayList<>(inc.fires)) {
            BlockPos pos = BlockPos.of(l);
            if (!level.isLoaded(pos)) continue;
            BlockState st = level.getBlockState(pos);
            if (st.getBlock() instanceof BaseFireBlock) { LAST_AGE.put(l, ageOf(st)); continue; }
            if (!st.isAir()) { inc.fires.remove(l); LAST_AGE.remove(l); changed = true; continue; }   // eau ou bloc posé : éteint
            // Flamme disparue : usure naturelle (âge avancé ou pluie) -> le feu repart ; sinon extincteur / camion MTS -> éteint pour de bon.
            boolean natural = LAST_AGE.getOrDefault(l, 15) >= NATURAL_AGE || level.isRainingAt(pos);
            if (natural && canBurnAt(level, pos)) {
                level.setBlock(pos, BaseFireBlock.getState(level, pos), 3);
                LAST_AGE.put(l, 0);
            } else { inc.fires.remove(l); LAST_AGE.remove(l); changed = true; }
        }
        if (changed) d.setDirty();
        if (inc.fires.isEmpty()) {
            // Éteint trop tôt : le feu reprend (sauf mode calme ou reprises épuisées).
            if (!over && now < activeUntil && REKINDLES.getOrDefault(inc.id, 0) < cfg.incendie_reprises_max) {
                Long at = REKINDLE_AT.get(inc.id);
                if (at == null) { REKINDLE_AT.put(inc.id, now + 15_000L); return; }
                if (now < at) return;
                REKINDLE_AT.remove(inc.id);
                List<Long> again = igniteMore(level, inc, cfg, 2 + RNG.nextInt(2));
                if (!again.isEmpty()) {
                    inc.fires.addAll(again);
                    REKINDLES.merge(inc.id, 1, Integer::sum);
                    d.setDirty();
                    for (ServerPlayer p : duty) tell(p, "§c[Secours] Le feu reprend à " + inc.site + " ! Il reste des braises.");
                    return;
                }
            }
            long minutes = Math.max(1, (now - inc.startMs) / 60_000L);
            reward(s, inc, cfg);
            close(s, d, inc, "§a[Secours] Incendie maîtrisé à " + inc.site + " en " + minutes + " min. Bien joué !");
        }
    }

    private static void close(MinecraftServer s, SecoursData d, Incident inc, String message) {
        for (long f : inc.fires) LAST_AGE.remove(f);
        NEXT_SPREAD.remove(inc.id); REKINDLE_AT.remove(inc.id); REKINDLES.remove(inc.id); PRESENCE.remove(inc.id);
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
