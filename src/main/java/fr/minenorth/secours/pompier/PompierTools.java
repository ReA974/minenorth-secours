package fr.minenorth.secours.pompier;

import com.mojang.logging.LogUtils;
import fr.minenorth.secours.FireService;
import fr.minenorth.secours.SecoursService;
import fr.minenorth.secours.compat.Mts;
import fr.minenorth.secours.config.SecoursConfig;
import fr.minenorth.secours.data.SecoursData;
import fr.minenorth.secours.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Côté serveur des outils des pompiers : forcement des portes (hache, halligan), branchement de la lance sur un véhicule MTS,
 * et données de la caméra thermique.
 * <p>
 * Forcer une porte : réservé aux pompiers en service, à moins de forcement_rayon blocs d'un incendie en cours ou d'un blessé
 * inconscient. Rien n'est cassé, la porte est simplement ouverte après quelques secondes d'effort. Les portes enregistrées dans
 * minenorth-portes passent par leur propre événement de clic (le mod Portes appelle {@link #portesForce}), comme le crochet de
 * serrurier : une interaction refusée par WorldGuard n'arrive donc jamais jusqu'ici.
 */
@Mod.EventBusSubscriber
public final class PompierTools {
    private PompierTools() {}

    private static final Logger LOG = LogUtils.getLogger();

    private record Forcing(ResourceKey<Level> dim, BlockPos pos, ForceToolItem tool, long start, long end) {}

    private static final Map<UUID, Forcing> FORCING = new HashMap<>();

    // ------------------------------------------------------------------ forcement

    public static boolean forceable(BlockState st) {
        return st.getBlock() instanceof DoorBlock || st.getBlock() instanceof TrapDoorBlock || st.getBlock() instanceof FenceGateBlock;
    }

    private static boolean closed(BlockState st) {
        return forceable(st) && st.hasProperty(BlockStateProperties.OPEN) && !st.getValue(BlockStateProperties.OPEN);
    }

    /**
     * Appelé (par réflexion) par minenorth-portes quand un joueur clique une porte enregistrée qu'il ne peut pas ouvrir.
     * Renvoie vrai si le joueur tient une hache ou un halligan : le clic est alors traité ici (forcement ou refus expliqué).
     */
    public static boolean portesForce(ServerPlayer p, BlockPos pos) {
        if (!(p.getMainHandItem().getItem() instanceof ForceToolItem)) return false;
        tryForce(p, pos);
        return true;
    }

    /** Commence à forcer la porte, ou explique pourquoi ce n'est pas possible. */
    public static void tryForce(ServerPlayer p, BlockPos pos) {
        if (!(p.getMainHandItem().getItem() instanceof ForceToolItem tool)) return;
        ServerLevel l = p.serverLevel();
        BlockState st = l.getBlockState(pos);
        if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) { pos = pos.below(); st = l.getBlockState(pos); }
        if (!closed(st)) return;
        if (!SecoursService.onDuty(p)) {
            p.displayClientMessage(Component.literal("§cRéservé aux pompiers en service."), true);
            return;
        }
        SecoursConfig cfg = SecoursConfig.get();
        if (!cfg.forcement_hors_intervention && !FireService.nearIntervention(l, pos, cfg.forcement_rayon)) {
            p.displayClientMessage(Component.literal("§eAucune intervention à proximité : rien ne justifie de forcer cette porte."), true);
            return;
        }
        Forcing cur = FORCING.get(p.getUUID());
        if (cur != null && cur.pos().equals(pos)) return;
        long now = l.getGameTime();
        FORCING.put(p.getUUID(), new Forcing(l.dimension(), pos.immutable(), tool, now, now + tool.forceTicks));
        p.displayClientMessage(Component.literal("§6Forcement de la porte… restez devant, outil en main."), true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        if (!FORCING.isEmpty()) tickForcing(s);
        if (s.getTickCount() % 20 == 5) thermal(s);
    }

    private static void tickForcing(MinecraftServer s) {
        for (Map.Entry<UUID, Forcing> en : new ArrayList<>(FORCING.entrySet())) {
            Forcing f = en.getValue();
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            ServerLevel l = s.getLevel(f.dim());
            if (p == null || l == null || p.level() != l) { FORCING.remove(en.getKey()); continue; }
            BlockState st = l.getBlockState(f.pos());
            if (!closed(st)) { FORCING.remove(en.getKey()); continue; }   // ouverte entre-temps
            if (p.getMainHandItem().getItem() != f.tool() || f.pos().distToCenterSqr(p.position()) > 16) {
                FORCING.remove(en.getKey());
                p.displayClientMessage(Component.literal("§7Forcement interrompu."), true);
                continue;
            }
            long now = l.getGameTime();
            long done = now - f.start(), total = f.end() - f.start();
            if (done % 10 == 0) {
                l.playSound(null, f.pos(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.8f, 0.9f + l.random.nextFloat() * 0.2f);
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), f.pos().getX() + 0.5, f.pos().getY() + 0.9, f.pos().getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.1);
                p.swing(InteractionHand.MAIN_HAND, true);
                int bars = (int) Math.min(10, done * 10 / Math.max(1, total));
                p.displayClientMessage(Component.literal("§6Forcement  §e" + "▮".repeat(bars) + "§8" + "▮".repeat(10 - bars)), true);
            }
            if (now < f.end()) continue;
            FORCING.remove(en.getKey());
            open(l, f.pos(), st);
            l.playSound(null, f.pos(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 0.7f, 1.2f);
            ItemStack held = p.getMainHandItem();
            held.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            p.displayClientMessage(Component.literal("§aPorte forcée."), true);
            LOG.info("[Secours] {} a forcé la porte en {} {} ({})", p.getGameProfile().getName(), l.dimension().location(), f.pos().toShortString(),
                    st.getBlock().getDescriptionId());
        }
    }

    private static void open(ServerLevel l, BlockPos pos, BlockState st) {
        if (st.getBlock() instanceof DoorBlock door) door.setOpen(null, l, st, pos, true);
        else l.setBlock(pos, st.setValue(BlockStateProperties.OPEN, true), 10);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { FORCING.remove(e.getEntity().getUUID()); }

    // ------------------------------------------------------------------ lance branchée sur un véhicule MTS

    @SubscribeEvent
    public static void interactEntity(PlayerInteractEvent.EntityInteract e) { hoseOnVehicle(e, e.getTarget()); }

    @SubscribeEvent
    public static void interactEntityAt(PlayerInteractEvent.EntityInteractSpecific e) { hoseOnVehicle(e, e.getTarget()); }

    private static void hoseOnVehicle(PlayerInteractEvent e, Entity target) {
        ItemStack stack = e.getItemStack();
        if (!(stack.getItem() instanceof HoseItem)) return;
        Entity vehicle = Mts.vehicleOf(target);
        if (vehicle == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!Pompier.isSecours(p)) { p.displayClientMessage(Component.literal("§cLa lance est réservée aux pompiers."), true); return; }
        if (!HoseItem.acceptsVehicle(vehicle)) { p.displayClientMessage(Component.literal("§eCe véhicule n'a pas de réserve d'eau."), true); return; }
        HoseItem.connectVehicle(p, stack, vehicle);
    }

    // ------------------------------------------------------------------ caméra thermique

    /** Une fois par seconde : points chauds autour des pompiers qui tiennent la caméra (48 blocs). */
    private static void thermal(MinecraftServer s) {
        SecoursData d = null;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!ThermalCameraItem.holding(p) || !Pompier.isSecours(p)) continue;
            if (d == null) d = SecoursData.get(s);
            ServerLevel l = p.serverLevel();
            List<int[]> pts = FireService.hotspots(l, p.blockPosition(), 48);
            for (ServerPlayer q : l.players()) {
                if (q == p || q.distanceToSqr(p) > 48 * 48) continue;
                SecoursData.Injury j = d.peek(q.getUUID());
                if (j != null && j.coma) pts.add(new int[]{q.getBlockX(), q.getBlockY(), q.getBlockZ(), 2});
            }
            if (pts.size() > 64) pts = pts.subList(0, 64);
            ModNetwork.send(p, new ModNetwork.ThermalPacket(pts));
        }
    }
}
