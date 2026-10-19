package fr.minenorth.secours.vie;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.minenorth.api.CompanyService;
import fr.minenorth.api.MineNorth;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * /salle : réglage d'un portique ou d'une borne d'abonnement (celui que l'on regarde, à 6 blocs au plus), par le patron ou un gérant de l'entreprise
 * de la salle (ou un opérateur) : lier à son entreprise, prix, durée, info, délier, abonner quelqu'un gratuitement.
 */
@Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID)
public final class GymCommands {
    private GymCommands() {}

    @Nullable
    private static GymSiteBE target(ServerPlayer p) {
        HitResult h = p.pick(6.0, 0.0f, false);
        if (h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK && p.level().getBlockEntity(b.getBlockPos()) instanceof GymSiteBE be) return be;
        p.sendSystemMessage(Component.literal("§cRegarde un portique ou une borne d'abonnement (6 blocs au plus)."));
        return null;
    }

    private static boolean manages(ServerPlayer p, GymSiteBE be) {
        if (p.hasPermissions(2)) return true;
        if (be.company > 0 && MineNorth.companies().isMember(p, be.company, true)) return true;
        p.sendSystemMessage(Component.literal("§cRéservé au patron et aux gérants de l'entreprise de la salle."));
        return false;
    }

    private static int lier(CommandSourceStack src, String companyName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        GymSiteBE be = target(p);
        if (be == null) return 0;
        CompanyService cs = MineNorth.companies();
        int id = 0;
        if (companyName != null && !companyName.isBlank()) {
            if (!p.hasPermissions(2)) { p.sendSystemMessage(Component.literal("§cSeuls les opérateurs choisissent l'entreprise ; sans nom, c'est la tienne.")); return 0; }
            id = cs.idOf(p.server, companyName);
        } else {
            for (String row : cs.activeCompanies(p.server)) {
                int sep = row.indexOf('|');
                if (sep <= 0) continue;
                try {
                    int cand = Integer.parseInt(row.substring(0, sep));
                    if (cs.isMember(p, cand, true)) { id = cand; break; }
                } catch (NumberFormatException ignored) {}
            }
        }
        if (id <= 0) { p.sendSystemMessage(Component.literal("§cAucune entreprise trouvée (tu dois être patron ou gérant d'une entreprise active).")); return 0; }
        if (be.company > 0 && be.company != id && !p.hasPermissions(2)) { p.sendSystemMessage(Component.literal("§cCet équipement appartient déjà à une autre entreprise.")); return 0; }
        be.company = id;
        be.setChanged();
        p.sendSystemMessage(Component.literal("§aLié à l'entreprise « " + cs.name(p.server, id) + " »."));
        return 1;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("salle")
                .then(Commands.literal("lier").executes(c -> lier(c.getSource(), null))
                        .then(Commands.argument("entreprise", StringArgumentType.greedyString()).executes(c -> lier(c.getSource(), StringArgumentType.getString(c, "entreprise")))))
                .then(Commands.literal("delier").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    GymSiteBE be = target(p);
                    if (be == null || !manages(p, be)) return 0;
                    be.company = 0;
                    be.setChanged();
                    p.sendSystemMessage(Component.literal("§7Équipement délié : le portique s'ouvre de nouveau pour tout le monde."));
                    return 1;
                }))
                .then(Commands.literal("prix").then(Commands.argument("euros", DoubleArgumentType.doubleArg(0, 100000)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    GymSiteBE be = target(p);
                    if (be == null || !manages(p, be)) return 0;
                    be.priceCents = (int) Math.round(DoubleArgumentType.getDouble(c, "euros") * 100);
                    be.setChanged();
                    p.sendSystemMessage(Component.literal("§aPrix de l'abonnement : " + String.format("%d,%02d €", be.priceCents / 100, be.priceCents % 100) + "."));
                    return 1;
                })))
                .then(Commands.literal("jours").then(Commands.argument("jours", IntegerArgumentType.integer(1, 365)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    GymSiteBE be = target(p);
                    if (be == null || !manages(p, be)) return 0;
                    be.days = IntegerArgumentType.getInteger(c, "jours");
                    be.setChanged();
                    p.sendSystemMessage(Component.literal("§aDurée de l'abonnement : " + be.days + " jours."));
                    return 1;
                })))
                .then(Commands.literal("info").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    GymSiteBE be = target(p);
                    if (be == null) return 0;
                    String name = be.company > 0 ? MineNorth.companies().name(p.server, be.company) : "(non liée)";
                    p.sendSystemMessage(Component.literal("§eSalle : " + name + " · abonnement " + be.days + " jours · " + String.format("%d,%02d €", be.priceCents / 100, be.priceCents % 100)));
                    return 1;
                }))
                .then(Commands.literal("abonner").then(Commands.argument("joueur", EntityArgument.player()).then(Commands.argument("jours", IntegerArgumentType.integer(1, 365)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    GymSiteBE be = target(p);
                    if (be == null || !manages(p, be)) return 0;
                    if (be.company <= 0) { p.sendSystemMessage(Component.literal("§cLie d'abord l'équipement à ton entreprise (/salle lier).")); return 0; }
                    ServerPlayer who = EntityArgument.getPlayer(c, "joueur");
                    int days = IntegerArgumentType.getInteger(c, "jours");
                    GymPass.get(p.server).grant(be.company, who.getUUID(), days);
                    p.sendSystemMessage(Component.literal("§a" + who.getGameProfile().getName() + " est abonné " + days + " jours (offert)."));
                    who.sendSystemMessage(Component.literal("§aAbonnement à la salle offert : " + days + " jours."));
                    return 1;
                })))));
    }
}
