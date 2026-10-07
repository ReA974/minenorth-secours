package fr.minenorth.secours.command;

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
 *   /secours tablette <joueur> | /secours hopital | /secours reload
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
                .then(Commands.literal("hopital").executes(c -> say(c.getSource(), SecoursService.setHospital(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("reload").executes(c -> {
                    boolean ok = SecoursConfig.load();
                    c.getSource().sendSystemMessage(Component.literal(ok ? "§aConfiguration des secours rechargée."
                            : "§cFichier minenorth_secours.json illisible : ancienne configuration conservée."));
                    return ok ? 1 : 0;
                })));
    }

    private static int grade(CommandSourceStack source, ServerPlayer target, int grade) {
        return say(source, SecoursService.setGrade(target.server, target.getUUID(), target.getGameProfile().getName(), grade));
    }
}
