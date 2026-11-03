package fr.minenorth.secours.pompier;

import fr.minenorth.secours.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tuyaux des lances branchées, côté serveur (deux fois par seconde) :
 * <ul>
 *   <li>arrache le tuyau (débranche la lance) au-delà de sa longueur, si le point d'eau disparaît ou si le pompier change de dimension ;</li>
 *   <li>prévient le pompier quand le tuyau est presque tendu ;</li>
 *   <li>envoie à tous les joueurs de la dimension la liste des tuyaux à dessiner (lance tenue en main) : {@link ModNetwork.HoseLinksPacket}.</li>
 * </ul>
 * À la mort, toutes les lances du joueur sont débranchées.
 */
@Mod.EventBusSubscriber
public final class HoseLinks {
    private HoseLinks() {}

    /** Dernière liste envoyée par dimension : on ne renvoie que si elle change (et toutes les 2 s pour les nouveaux venus). */
    private static final Map<ResourceKey<Level>, List<int[]>> SENT = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 10 != 0) return;
        MinecraftServer s = e.getServer();
        boolean resend = s.getTickCount() % 40 == 0;
        Map<ResourceKey<Level>, List<int[]>> links = new HashMap<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!p.isAlive()) continue;
            int[] link = check(p);
            if (link != null) links.computeIfAbsent(p.level().dimension(), k -> new ArrayList<>()).add(link);
        }
        for (ServerLevel l : s.getAllLevels()) {
            List<int[]> now = links.getOrDefault(l.dimension(), List.of());
            List<int[]> before = SENT.getOrDefault(l.dimension(), List.of());
            if (!resend && same(now, before)) continue;
            if (now.isEmpty() && before.isEmpty()) continue;
            SENT.put(l.dimension(), now);
            ModNetwork.HoseLinksPacket pkt = new ModNetwork.HoseLinksPacket(now);
            for (ServerPlayer q : l.players()) ModNetwork.send(q, pkt);
        }
    }

    private static boolean same(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!java.util.Arrays.equals(a.get(i), b.get(i))) return false;
        return true;
    }

    /** Vérifie toutes les lances branchées du joueur ; renvoie le tuyau à dessiner (lance en main) ou null. */
    private static int[] check(ServerPlayer p) {
        int[] out = null;
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        for (ItemStack st : hoses(p)) {
            CompoundTag t = HoseItem.connection(st);
            if (t == null) continue;
            if (!t.getString("dim").equals(p.level().dimension().location().toString())) {
                HoseItem.disconnect(p, st, "Tuyau débranché : vous avez quitté les lieux.");
                continue;
            }
            ServerLevel sl = p.serverLevel();
            Vec3 a = HoseItem.anchor(sl, t);
            if (a == null) {
                HoseItem.disconnect(p, st, "Le point d'eau n'est plus là : tuyau débranché.");
                continue;
            }
            int len = HoseItem.length(t);
            double d = Math.sqrt(a.distanceToSqr(p.position()));
            if (d > len + 1.5) {
                HoseItem.disconnect(p, st, "Tuyau arraché : vous êtes allé au-delà de " + len + " m.");
                continue;
            }
            boolean held = st == main || st == off;
            if (!held) continue;
            if (d > len * 0.85) p.displayClientMessage(Component.literal("§6Tuyau presque tendu : " + (int) d + " / " + len + " m"), true);
            if (out != null) continue;
            if (t.hasUUID("vehicule")) {
                Entity v = sl.getEntity(t.getUUID("vehicule"));
                out = new int[]{p.getId(), 1, v == null ? -1 : v.getId(), 0, 0, len};
            } else {
                BlockPos h = BlockPos.of(t.getLong("pos"));
                out = new int[]{p.getId(), 0, h.getX(), h.getY(), h.getZ(), len};
            }
        }
        return out;
    }

    private static List<ItemStack> hoses(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>(2);
        for (ItemStack st : p.getInventory().items) if (st.getItem() instanceof HoseItem) out.add(st);
        for (ItemStack st : p.getInventory().offhand) if (st.getItem() instanceof HoseItem) out.add(st);
        return out;
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        for (ItemStack st : hoses(p)) if (HoseItem.connection(st) != null) HoseItem.disconnect(p, st, null);
    }
}
