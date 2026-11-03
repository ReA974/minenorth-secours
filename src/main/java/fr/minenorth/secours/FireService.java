package fr.minenorth.secours;

import fr.minenorth.api.MineNorth;
import fr.minenorth.secours.compat.MapBridge;
import fr.minenorth.secours.config.SecoursConfig;
import fr.minenorth.secours.data.SecoursData;
import fr.minenorth.secours.data.SecoursData.Incident;
import fr.minenorth.secours.data.SecoursData.Injury;
import fr.minenorth.secours.data.SecoursData.Site;
import fr.minenorth.secours.pompier.AriMaskItem;
import fr.minenorth.secours.pompier.Pompier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Incendies de service : quand des pompiers sont en service, un incendie se déclenche sur un des sites définis par les OP
 * (/secours incendie ajouter). Il apparaît sur la carte (mod minenorth_map) des pompiers en service ; « J'y vais » sur la
 * tablette lance le guidage. Même guidage vers un blessé inconscient. L'accident d'un labo clandestin (mod drogue) devient
 * aussi une intervention.
 * <p>
 * Les flammes sont des {@link fr.minenorth.secours.pompier.FoyerBlock} : elles ne s'éteignent jamais seules, ne brûlent rien et
 * ne se propagent pas d'elles-mêmes. Un foyer qui n'est plus là a donc forcément été éteint (lance, extincteur MTS, eau,
 * couverture, main). Une fois tout éteint avant la durée minimale, 1 ou 2 braises cachées restent : à noyer, sinon le feu repart.
 */
@Mod.EventBusSubscriber
public final class FireService {
    public static final String MARK_FIRE = "Incendie : ", MARK_HURT = "Blessé : ";
    public static final String LAB_SITE = "Labo clandestin";
    private static final int FIRE_COLOR = 0xFF5500, HURT_COLOR = 0xFF3355;
    private static final Random RNG = new Random();
    private static final ResourceLocation DRUG_LAB = new ResourceLocation("minenorthdrogue", "lab");

    private record Guide(UUID victim, int x, int y, int z) {}

    /** Unité -> blessé vers lequel la carte la guide. */
    private static final Map<UUID, Guide> GUIDES = new HashMap<>();
    private static long pendingStart, nextAuto, noDutySince;

    // État de suivi en mémoire (repart de zéro au redémarrage, sans conséquence : seulement des délais).
    private static final Map<UUID, Long> NEXT_SPREAD = new HashMap<>();
    /** incendie -> (pompier -> secondes passées à moins de incendie_prime_distance du site). */
    private static final Map<UUID, Map<UUID, Integer>> PRESENCE = new HashMap<>();
    /** Explosions de labo à examiner au tick suivant (le mod drogue pose ses flammes juste après l'explosion). */
    private static final List<Object[]> LAB_BLASTS = new ArrayList<>();
    /** Mode calme actif (trop de blessés à soigner). */
    private static boolean calm;

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

    private static String dimOf(ServerLevel l) { return l.dimension().location().toString(); }

    private static boolean isFoyer(BlockState st) { return st.is(Pompier.FOYER.get()); }

    private static boolean isBraise(BlockState st) { return st.is(Pompier.BRAISE.get()); }

    /** Flamme vanilla (feu, feu des âmes), à remplacer par un foyer quand elle appartient à un incendie. */
    private static boolean isVanillaFire(BlockState st) { return st.getBlock() instanceof BaseFireBlock && !isFoyer(st); }

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

    /** Alerte sonore et écrite aux pompiers en service, repère sur leur carte. */
    private static void alert(MinecraftServer s, Incident inc, String text) {
        for (ServerPlayer p : onDuty(s)) {
            showOn(p, inc, false);
            tell(p, text);
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
        }
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
        List<Long> fires = ignite(level, new BlockPos(st.x, st.y, st.z), Math.max(1, cf.incendie_rayon), wanted, Math.max(60, wanted * 5), Pompier.FOYER.get().defaultBlockState());
        if (fires.isEmpty()) return "Impossible d'allumer un feu sur le site " + st.name + " (pas de sol plein ou pas d'air au-dessus ?).";

        Incident inc = new Incident();
        inc.id = UUID.randomUUID();
        inc.site = st.name; inc.dim = st.dim; inc.x = st.x; inc.y = st.y; inc.z = st.z;
        inc.startMs = System.currentTimeMillis();
        inc.fires.addAll(fires);
        d.incidents.put(inc.id, inc);
        d.setDirty();
        alert(s, inc, "§c§l[Secours] INCENDIE à " + st.name + " §r§c(" + st.x + " " + st.y + " " + st.z + "). Tablette : « J'y vais » pour être guidé sur la carte.");
        return "Incendie déclenché : " + st.name + " (" + fires.size() + " foyers).";
    }

    /** Pose jusqu'à wanted blocs (foyer ou braise) sur du sol plein autour du centre. Renvoie les positions réellement prises. */
    private static List<Long> ignite(ServerLevel level, BlockPos c, int radius, int wanted, int maxTries, BlockState what) {
        List<Long> out = new ArrayList<>();
        level.getChunkAt(c); // charge la zone si besoin
        for (int tries = 0; tries < maxTries && out.size() < wanted; tries++) {
            int x = c.getX() + (tries == 0 ? 0 : RNG.nextInt(radius * 2 + 1) - radius);
            int z = c.getZ() + (tries == 0 ? 0 : RNG.nextInt(radius * 2 + 1) - radius);
            for (int y = c.getY() + 2; y >= c.getY() - 3; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!canBurnAt(level, pos)) continue;
                if (!out.contains(pos.asLong())) {
                    level.setBlock(pos, what, 3);
                    out.add(pos.asLong());
                }
                break;
            }
        }
        return out;
    }

    private static boolean canBurnAt(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        return (here.isAir() || isVanillaFire(here)) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    // ------------------------------------------------------------------ labo clandestin (mod drogue)

    /**
     * Le labo du mod drogue explose sans casser de blocs (aucun « exploder ») puis pose du feu vanilla autour : on note l'explosion,
     * et au tick suivant ce feu devient une intervention suivie (foyers, alerte, carte, fin d'alerte).
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || e.getExplosion().getExploder() != null) return;
        Vec3 c = e.getExplosion().getPosition();
        BlockPos lab = BlockPos.containing(c.x, c.y - 1.0, c.z);   // le labo explose à +1,2 bloc au-dessus de lui
        if (!DRUG_LAB.equals(ForgeRegistries.BLOCKS.getKey(sl.getBlockState(lab).getBlock()))) return;
        LAB_BLASTS.add(new Object[]{sl, lab});
    }

    private static void labFires() {
        if (LAB_BLASTS.isEmpty()) return;
        List<Object[]> todo = new ArrayList<>(LAB_BLASTS);
        LAB_BLASTS.clear();
        for (Object[] o : todo) {
            ServerLevel level = (ServerLevel) o[0];
            BlockPos lab = (BlockPos) o[1];
            if (tracked(level, lab, 8)) continue;   // déjà une intervention sur ce labo : ses foyers suffisent
            List<Long> fires = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(lab.offset(-3, -1, -3), lab.offset(3, 3, 3))) {
                if (isVanillaFire(level.getBlockState(p))) {
                    level.setBlock(p, Pompier.FOYER.get().defaultBlockState(), 3);
                    fires.add(p.asLong());
                }
            }
            if (fires.isEmpty()) continue;
            MinecraftServer s = level.getServer();
            SecoursData d = SecoursData.get(s);
            Incident inc = new Incident();
            inc.id = UUID.randomUUID();
            inc.site = LAB_SITE + " " + lab.getX() + "/" + lab.getZ();
            inc.dim = dimOf(level); inc.x = lab.getX(); inc.y = lab.getY(); inc.z = lab.getZ();
            inc.startMs = System.currentTimeMillis();
            inc.lab = true;
            inc.fires.addAll(fires);
            d.incidents.put(inc.id, inc);
            d.setDirty();
            alert(s, inc, "§c§l[Secours] FEU DE LABORATOIRE §r§c(" + lab.getX() + " " + lab.getY() + " " + lab.getZ() + ") : fumées chimiques, masque ARI conseillé.");
        }
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

    // ------------------------------------------------------------------ requêtes des outils et des blocs

    /** Ce foyer / cette braise appartient-il à un incendie en cours ? */
    public static boolean tracked(ServerLevel level, BlockPos pos) {
        long l = pos.asLong();
        String dim = dimOf(level);
        for (Incident inc : SecoursData.get(level.getServer()).incidents.values()) if (inc.dim.equals(dim) && inc.fires.contains(l)) return true;
        return false;
    }

    /** Un incendie en cours a-t-il un foyer (ou son centre) à moins de radius blocs ? */
    public static boolean tracked(ServerLevel level, BlockPos pos, int radius) {
        String dim = dimOf(level);
        double r2 = (double) radius * radius;
        for (Incident inc : SecoursData.get(level.getServer()).incidents.values()) {
            if (!inc.dim.equals(dim)) continue;
            if (pos.distToCenterSqr(inc.x + 0.5, inc.y + 0.5, inc.z + 0.5) <= r2) return true;
            for (long l : inc.fires) if (BlockPos.of(l).distSqr(pos) <= r2) return true;
        }
        return false;
    }

    /** Intervention à proximité : incendie en cours, ou joueur inconscient (pour forcer une porte). */
    public static boolean nearIntervention(ServerLevel level, BlockPos pos, int radius) {
        if (tracked(level, pos, radius)) return true;
        SecoursData d = SecoursData.get(level.getServer());
        double r2 = (double) radius * radius;
        for (ServerPlayer q : level.players()) {
            Injury j = d.peek(q.getUUID());
            if (j != null && j.coma && q.blockPosition().distSqr(pos) <= r2) return true;
        }
        return false;
    }

    /** Points chauds suivis à moins de radius blocs : {x, y, z, type} avec type 0 = foyer, 1 = braise. */
    public static List<int[]> hotspots(ServerLevel level, BlockPos center, int radius) {
        List<int[]> out = new ArrayList<>();
        String dim = dimOf(level);
        double r2 = (double) radius * radius;
        for (Incident inc : SecoursData.get(level.getServer()).incidents.values()) {
            if (!inc.dim.equals(dim)) continue;
            for (long l : inc.fires) {
                BlockPos p = BlockPos.of(l);
                if (p.distSqr(center) > r2 || !level.isLoaded(p)) continue;
                BlockState st = level.getBlockState(p);
                if (isFoyer(st)) out.add(new int[]{p.getX(), p.getY(), p.getZ(), 0});
                else if (isBraise(st)) out.add(new int[]{p.getX(), p.getY(), p.getZ(), 1});
            }
        }
        return out;
    }

    /**
     * Noie un bloc de feu (foyer, braise, feu vanilla) : bruit d'extinction et vapeur. Le suivi de l'incendie s'en rend compte
     * à sa prochaine passe (une fois par seconde). Renvoie faux s'il n'y avait pas de feu.
     */
    public static boolean douse(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!st.is(BlockTags.FIRE)) return false;
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.6f + RNG.nextFloat() * 0.4f);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 4, 0.25, 0.2, 0.25, 0.02);
        return true;
    }

    /**
     * Une braise n'a pas été noyée à temps : le feu repart à sa place si l'incendie le permet encore (durée minimale non écoulée,
     * reprises restantes, pas de mode calme). Renvoie faux si la braise doit simplement disparaître.
     */
    public static boolean rekindle(ServerLevel level, BlockPos pos) {
        MinecraftServer s = level.getServer();
        SecoursData d = SecoursData.get(s);
        SecoursConfig cfg = SecoursConfig.get();
        long now = System.currentTimeMillis();
        for (Incident inc : d.incidents.values()) {
            if (!inc.dim.equals(dimOf(level)) || !inc.fires.contains(pos.asLong())) continue;
            boolean allowed = !calm && now < inc.startMs + SecoursConfig.ms(cfg.incendie_duree_min_minutes) && inc.rekindles < cfg.incendie_reprises_max;
            if (!allowed) return false;
            level.setBlock(pos, Pompier.FOYER.get().defaultBlockState(), 3);
            List<Long> more = ignite(level, pos, 2, 1 + RNG.nextInt(2), 12, Pompier.FOYER.get().defaultBlockState());
            for (long l : more) if (!inc.fires.contains(l)) inc.fires.add(l);
            inc.rekindles++;
            inc.embersPlaced = false;
            d.setDirty();
            alert(s, inc, "§c[Secours] Le feu reprend à " + inc.site + " ! Des braises n'avaient pas été noyées.");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ boucle

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        labFires();
        if (e.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = e.getServer();
        SecoursConfig cfg = SecoursConfig.get();
        SecoursData d = SecoursData.get(s);
        long now = System.currentTimeMillis();
        List<ServerPlayer> duty = onDuty(s);
        boolean even = s.getTickCount() % 40 == 0;

        tickGuides(s, d, duty);
        for (Incident inc : d.incidents.values()) smoke(s, inc, cfg, even);

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

    /** Fumée au-dessus des foyers, et dégâts aux joueurs qui la respirent sans masque ARI (toutes les 2 s). */
    private static void smoke(MinecraftServer s, Incident inc, SecoursConfig cfg, boolean hurt) {
        ServerLevel level = level(s, inc.dim);
        if (level == null) return;
        List<BlockPos> foyers = new ArrayList<>();
        for (long l : inc.fires) {
            BlockPos p = BlockPos.of(l);
            if (level.isLoaded(p) && isFoyer(level.getBlockState(p))) foyers.add(p);
        }
        if (foyers.isEmpty()) return;
        for (int i = 0; i < foyers.size(); i += 2) {
            BlockPos p = foyers.get(i);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5, 1, 0.2, 0.3, 0.2, 0.01);
        }
        if (!hurt || cfg.incendie_fumee_degats <= 0) return;
        int r = cfg.incendie_fumee_rayon;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            if (Math.abs(p.getX() - inc.x) > 48 || Math.abs(p.getZ() - inc.z) > 48) continue;
            BlockPos at = p.blockPosition();
            boolean inSmoke = false;
            for (BlockPos f : foyers) {
                if (Math.abs(f.getX() - at.getX()) <= r && Math.abs(f.getZ() - at.getZ()) <= r && Math.abs(f.getY() - at.getY()) <= r) { inSmoke = true; break; }
            }
            if (!inSmoke) continue;
            if (AriMaskItem.protects(p)) continue;
            p.hurt(level.damageSources().inFire(), (float) cfg.incendie_fumee_degats);
            p.displayClientMessage(Component.literal(AriMaskItem.wearing(p) ? "§7Masque ARI sans air : il faut la bouteille d'air ARI." : "§7Vous suffoquez dans la fumée : reculez ou portez un masque ARI."), true);
        }
    }

    private static void tickIncident(MinecraftServer s, SecoursData d, Incident inc, long now, SecoursConfig cfg, List<ServerPlayer> duty, boolean over) {
        ServerLevel level = level(s, inc.dim);
        long limit = inc.startMs + SecoursConfig.ms(Math.max(1, cfg.incendie_duree_max_minutes));
        if (level == null) { close(s, d, inc, null); return; }
        if (now >= limit) {
            clearBlocks(level, inc);
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
        int foyers = 0;
        // Suivi : un foyer ou une braise qui n'est plus là a été éteint (rien ne les retire d'autre). Ancienne sauvegarde : feu vanilla -> foyer.
        for (Long l : new ArrayList<>(inc.fires)) {
            BlockPos pos = BlockPos.of(l);
            if (!level.isLoaded(pos)) { foyers++; continue; }   // zone non chargée : on la compte comme encore en feu
            BlockState st = level.getBlockState(pos);
            if (isVanillaFire(st)) { level.setBlock(pos, Pompier.FOYER.get().defaultBlockState(), 3); st = level.getBlockState(pos); }
            if (isFoyer(st)) foyers++;
            else if (!isBraise(st)) { inc.fires.remove(l); changed = true; }
        }
        long activeUntil = inc.startMs + SecoursConfig.ms(cfg.incendie_duree_min_minutes);
        // Propagation : tant que la durée minimale n'est pas écoulée et que le feu n'est pas déjà éteint, de nouveaux foyers apparaissent.
        int bonus = inc.lab ? 0 : cfg.incendie_propagation_rayon_bonus;
        if (!over && foyers > 0 && cfg.incendie_propagation_secondes > 0 && now < activeUntil && inc.fires.size() < cfg.incendie_foyers_max
                && now >= NEXT_SPREAD.getOrDefault(inc.id, inc.startMs + cfg.incendie_propagation_secondes * 1000L)) {
            List<Long> more = ignite(level, new BlockPos(inc.x, inc.y, inc.z), Math.max(1, cfg.incendie_rayon) + bonus,
                    Math.min(2 + RNG.nextInt(2), cfg.incendie_foyers_max - inc.fires.size()), 40, Pompier.FOYER.get().defaultBlockState());
            for (long l : more) if (!inc.fires.contains(l)) { inc.fires.add(l); changed = true; foyers++; }
            NEXT_SPREAD.put(inc.id, now + cfg.incendie_propagation_secondes * 1000L);
        }
        if (changed) d.setDirty();
        if (foyers > 0 || !inc.fires.isEmpty()) return;   // encore des flammes, ou des braises qui attendent d'être noyées

        // Plus aucune flamme : éteint trop tôt -> 1 ou 2 braises cachées (caméra thermique) à noyer, sinon le feu repart.
        if (!over && !inc.embersPlaced && now < activeUntil && inc.rekindles < cfg.incendie_reprises_max) {
            List<Long> embers = ignite(level, new BlockPos(inc.x, inc.y, inc.z), Math.max(1, cfg.incendie_rayon), 1 + RNG.nextInt(2), 20, Pompier.BRAISE.get().defaultBlockState());
            inc.embersPlaced = true;
            d.setDirty();
            if (!embers.isEmpty()) {
                inc.fires.addAll(embers);
                for (ServerPlayer p : duty) if (dimOf(p).equals(inc.dim)) tell(p, "§6[Secours] Flammes éteintes à " + inc.site
                        + ". Il reste de la fumée : cherchez les braises (caméra thermique) et noyez-les avant " + cfg.incendie_braises_secondes + " s.");
                return;
            }
        }
        long minutes = Math.max(1, (now - inc.startMs) / 60_000L);
        reward(s, inc, cfg);
        close(s, d, inc, "§a[Secours] Incendie maîtrisé à " + inc.site + " en " + minutes + " min. Bien joué !");
    }

    /** Retire les foyers et braises encore posés (zones chargées ; les autres disparaîtront d'eux-mêmes au chargement). */
    private static void clearBlocks(ServerLevel level, Incident inc) {
        for (long l : inc.fires) {
            BlockPos pos = BlockPos.of(l);
            if (!level.isLoaded(pos)) continue;
            BlockState st = level.getBlockState(pos);
            if (isFoyer(st) || isBraise(st) || isVanillaFire(st)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void close(MinecraftServer s, SecoursData d, Incident inc, String message) {
        NEXT_SPREAD.remove(inc.id); PRESENCE.remove(inc.id);
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
            if (level != null) clearBlocks(level, inc);
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

    /** Solo / changement de monde : l'état en mémoire ne doit pas passer d'une partie à l'autre. */
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        GUIDES.clear(); NEXT_SPREAD.clear(); PRESENCE.clear(); LAB_BLASTS.clear();
        pendingStart = nextAuto = noDutySince = 0;
        calm = false;
    }
}
