package fr.minenorth.secours.command;

import fr.minenorth.secours.FireService;
import fr.minenorth.secours.SecoursService;
import fr.minenorth.secours.config.SecoursConfig;
import fr.minenorth.secours.data.SecoursData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Commandes OP / console (niveau 2) :
 *   /soins <joueur>              ouvre le menu du PNJ de soins chez ce joueur
 *   /reanimer <joueur>           réanime un joueur inconscient
 *   /retirerblessures <joueur>   retire toutes ses blessures (et le réveille)
 *   /secours grade <joueur> <chef|medecin|secouriste|aucun>
 *   /secours tablette <joueur> | /secours reload
 *   /secours hopital|urgences [ajouter <nom> | supprimer <nom> | liste]   points de réveil
 */
@Mod.EventBusSubscriber
public final class SecoursCommands {
    private SecoursCommands() {}

    private static int say(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal("§a" + text));
        return 1;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        d.register(Commands.literal("soins").requires(s -> s.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    SecoursService.openClinic(EntityArgument.getPlayer(c, "joueur"));
                    return 1;
                })));
        d.register(Commands.literal("reanimer").requires(s -> s.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> say(c.getSource(), SecoursService.adminRevive(EntityArgument.getPlayer(c, "joueur"))))));
        d.register(Commands.literal("retirerblessures").requires(s -> s.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> say(c.getSource(), SecoursService.adminHeal(EntityArgument.getPlayer(c, "joueur"))))));

        var grade = Commands.literal("grade").then(Commands.argument("joueur", EntityArgument.player())
                .then(Commands.literal("chef").executes(c -> grade(c.getSource(), EntityArgument.getPlayer(c, "joueur"), SecoursData.CHEF)))
                .then(Commands.literal("medecin").executes(c -> grade(c.getSource(), EntityArgument.getPlayer(c, "joueur"), SecoursData.MEDECIN)))
                .then(Commands.literal("secouriste").executes(c -> grade(c.getSource(), EntityArgument.getPlayer(c, "joueur"), SecoursData.SECOURISTE)))
                .then(Commands.literal("aucun").executes(c -> grade(c.getSource(), EntityArgument.getPlayer(c, "joueur"), -1))));
        d.register(Commands.literal("secours").requires(s -> s.hasPermission(2))
                .then(grade)
                .then(Commands.literal("tablette").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                    SecoursService.giveTablet(target);
                    return say(c.getSource(), "Tablette des secours remise à " + fr.minenorth.api.MineNorth.displayName(target) + ".");
                })))
                .then(Commands.literal("incendie")
                        .then(Commands.literal("ajouter").then(Commands.argument("nom", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(x -> say(x.getSource(), addSite(x.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(x, "nom"))))))
                        .then(Commands.literal("supprimer").then(Commands.argument("nom", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(x -> say(x.getSource(), removeSite(x.getSource().getServer(), com.mojang.brigadier.arguments.StringArgumentType.getString(x, "nom"))))))
                        .then(Commands.literal("liste").executes(x -> say(x.getSource(), listSites(x.getSource().getServer()))))
                        .then(Commands.literal("declencher")
                                .executes(x -> say(x.getSource(), FireService.start(x.getSource().getServer(), null)))
                                .then(Commands.argument("nom", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(x -> say(x.getSource(), FireService.start(x.getSource().getServer(), com.mojang.brigadier.arguments.StringArgumentType.getString(x, "nom"))))))
                        .then(Commands.literal("eteindre").executes(x -> say(x.getSource(),
                                FireService.closeAll(x.getSource().getServer(), SecoursData.get(x.getSource().getServer()), "§e[Secours] Incendie annulé par un administrateur.") + " incendie(s) éteint(s)."))))
                .then(spawnCommand("hopital", false))
                .then(spawnCommand("urgences", true))
                .then(Commands.literal("reload").executes(c -> {
                    boolean ok = SecoursConfig.load();
                    c.getSource().sendSystemMessage(Component.literal(ok ? "§aConfiguration des secours rechargée."
                            : "§cFichier minenorth_secours.json illisible : ancienne configuration conservée."));
                    return ok ? 1 : 0;
                })));
    }

    /** /secours hopital|urgences [ajouter <nom> | supprimer <nom> | liste] : points de réveil. Sans argument : ajoute un point au nom automatique. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spawnCommand(String literal, boolean urgences) {
        var word = com.mojang.brigadier.arguments.StringArgumentType.word();
        return Commands.literal(literal)
                .executes(c -> say(c.getSource(), SecoursService.addSpawn(c.getSource().getPlayerOrException(), urgences, null)))
                .then(Commands.literal("ajouter").then(Commands.argument("nom", word).executes(c -> say(c.getSource(),
                        SecoursService.addSpawn(c.getSource().getPlayerOrException(), urgences, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "nom"))))))
                .then(Commands.literal("supprimer").then(Commands.argument("nom", word).executes(c -> say(c.getSource(),
                        SecoursService.removeSpawn(c.getSource().getServer(), urgences, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "nom"))))))
                .then(Commands.literal("liste").executes(c -> say(c.getSource(), SecoursService.listSpawns(c.getSource().getServer(), urgences))));
    }

    private static String addSite(ServerPlayer p, String name) {
        SecoursData d = SecoursData.get(p.server);
        SecoursData.Site s = new SecoursData.Site();
        s.name = name; s.dim = p.level().dimension().location().toString();
        s.x = p.blockPosition().getX(); s.y = p.blockPosition().getY(); s.z = p.blockPosition().getZ();
        d.sites.put(name.toLowerCase(java.util.Locale.ROOT), s);
        d.setDirty();
        return "Site d'incendie « " + name + " » défini à votre position. Choisissez un sol non inflammable (dalle, route) : le feu peut se propager.";
    }

    private static String removeSite(net.minecraft.server.MinecraftServer server, String name) {
        SecoursData d = SecoursData.get(server);
        if (d.sites.remove(name.toLowerCase(java.util.Locale.ROOT)) == null) return "Site inconnu : " + name;
        d.setDirty();
        return "Site « " + name + " » supprimé.";
    }

    private static String listSites(net.minecraft.server.MinecraftServer server) {
        SecoursData d = SecoursData.get(server);
        if (d.sites.isEmpty()) return "Aucun site d'incendie. /secours incendie ajouter <nom> à l'endroit voulu.";
        StringBuilder b = new StringBuilder("Sites d'incendie (" + d.sites.size() + ") :");
        for (SecoursData.Site s : d.sites.values()) b.append("\n - ").append(s.name).append("  ").append(s.x).append(' ').append(s.y).append(' ').append(s.z);
        b.append("\nIncendies en cours : ").append(d.incidents.size());
        return b.toString();
    }

    private static int grade(CommandSourceStack source, ServerPlayer target, int grade) {
        return say(source, SecoursService.setGrade(target.server, target.getUUID(), target.getGameProfile().getName(), grade));
    }
}
