package fr.minenorth.secours.vie;

import fr.minenorth.api.CompanyService;
import fr.minenorth.api.MineNorth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion d'une salle de sport (portique ou borne) depuis l'écran : toutes les actions sont revérifiées par le serveur (portée, bloc, droits,
 * valeurs bornées). Remplace l'ancienne commande /salle.
 */
public final class GymAdmin {
    private GymAdmin() {}

    public static final int REFRESH = 0, LINK = 1, UNLINK = 2, SETTINGS = 3, OFFER = 4, REMOVE = 5;
    private static final int MAX_COMPANIES = 24, MAX_SUBS = 64, MAX_NEAR = 16;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    static void forget(UUID id) { LAST.remove(id); }

    /** Entreprises que le joueur peut lier à un équipement : celles qu'il dirige (toutes les actives pour un opérateur). */
    static List<Integer> choices(ServerPlayer p) {
        List<Integer> out = new ArrayList<>();
        CompanyService cs = MineNorth.companies();
        for (String row : cs.activeCompanies(p.server)) {
            int sep = row.indexOf('|');
            if (sep <= 0) continue;
            try {
                int id = Integer.parseInt(row.substring(0, sep));
                if (p.hasPermissions(2) || cs.isMember(p, id, true)) out.add(id);
            } catch (NumberFormatException ignored) {}
            if (out.size() >= MAX_COMPANIES) break;
        }
        return out;
    }

    /** Peut-il gérer cet équipement ? Opérateur, direction de l'entreprise liée, ou (équipement non lié) direction d'une entreprise quelconque. */
    public static boolean canManage(ServerPlayer p, GymSiteBE be) {
        if (p.hasPermissions(2)) return true;
        if (be.company > 0) return MineNorth.companies().isMember(p, be.company, true);
        return !choices(p).isEmpty();
    }

    public static void open(ServerPlayer p, GymSiteBE be) { send(p, be, ""); }

    private static Vec3 center(BlockPos pos) { return Vec3.atCenterOf(pos); }

    private static void send(ServerPlayer p, GymSiteBE be, String msg) {
        CompanyService cs = MineNorth.companies();
        List<VieNet.GymView.Company> comps = new ArrayList<>();
        for (int id : choices(p)) comps.add(new VieNet.GymView.Company(id, cs.name(p.server, id)));
        List<VieNet.GymView.Sub> subs = new ArrayList<>();
        if (be.company > 0) {
            GymPass.get(p.server).entries(be.company).entrySet().stream()
                    .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed()).limit(MAX_SUBS)
                    .forEach(e -> subs.add(new VieNet.GymView.Sub(e.getKey(), name(p, e.getKey()), e.getValue())));
        }
        List<VieNet.GymView.Near> near = new ArrayList<>();
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o != p && o.distanceToSqr(center(be.getBlockPos())) <= 256 && near.size() < MAX_NEAR) near.add(new VieNet.GymView.Near(o.getUUID(), o.getGameProfile().getName()));
        }
        String cname = be.company > 0 ? cs.name(p.server, be.company) : "";
        VieNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new VieNet.GymView(be.getBlockPos(), be.company, cname, be.priceCents, be.days, msg, comps, subs, near));
    }

    private static String name(ServerPlayer p, UUID id) {
        return p.server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString().substring(0, 8));
    }

    /** Traitement d'une action reçue du client (thread serveur). */
    static void handle(ServerPlayer p, VieNet.GymAction a) {
        long now = p.serverLevel().getGameTime();
        Long prev = LAST.put(p.getUUID(), now);
        if (prev != null && now - prev < 4) return;
        BlockPos pos = a.pos();
        if (p.distanceToSqr(center(pos)) > 64 || !p.level().isLoaded(pos)) return;
        if (!(p.level().getBlockEntity(pos) instanceof GymSiteBE be)) return;
        if (!canManage(p, be)) { p.displayClientMessage(Component.literal("§cRéservé au patron et aux gérants de l'entreprise de la salle."), true); return; }
        CompanyService cs = MineNorth.companies();
        String msg = "";
        switch (a.action()) {
            case LINK -> {
                int id = a.a();
                boolean ok = choices(p).contains(id) && !cs.name(p.server, id).isBlank();
                if (!ok) msg = "§cEntreprise non autorisée.";
                else if (be.company > 0 && be.company != id && !p.hasPermissions(2)) msg = "§cCet équipement appartient déjà à une autre entreprise.";
                else { be.company = id; be.setChanged(); msg = "§aLié à « " + cs.name(p.server, id) + " »."; }
            }
            case UNLINK -> { be.company = 0; be.setChanged(); msg = "§7Équipement délié : le portique s'ouvre pour tout le monde."; }
            case SETTINGS -> {
                be.priceCents = Math.max(0, Math.min(10_000_000, a.a()));
                be.days = Math.max(1, Math.min(365, a.b()));
                be.setChanged();
                msg = "§aEnregistré : " + be.days + " jours, " + String.format("%d,%02d €", be.priceCents / 100, be.priceCents % 100) + ".";
            }
            case OFFER -> {
                ServerPlayer who = p.server.getPlayerList().getPlayer(a.uuid());
                if (be.company <= 0) msg = "§cLie d'abord l'équipement à une entreprise.";
                else if (who == null || who.distanceToSqr(center(pos)) > 256) msg = "§cCe joueur n'est pas assez proche.";
                else {
                    int days = Math.max(1, Math.min(365, a.b()));
                    GymPass.get(p.server).grant(be.company, who.getUUID(), days);
                    who.sendSystemMessage(Component.literal("§aAbonnement à la salle offert : " + days + " jours."));
                    msg = "§a" + who.getGameProfile().getName() + " est abonné " + days + " jours (offert).";
                }
            }
            case REMOVE -> {
                if (be.company > 0 && GymPass.get(p.server).remove(be.company, a.uuid())) msg = "§7Abonnement retiré.";
            }
            default -> {}
        }
        send(p, be, msg);
    }
}
