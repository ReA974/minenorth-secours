package fr.minenorth.secours.vie;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/** /sante (tous les joueurs : ouvre l'écran) et /vie (opérateurs : voir, définir, maladie, guérir, reset). */
@Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID)
public final class VieCommands {
    private VieCommands() {}

    private static String line(ServerPlayer p) {
        Stats s = Vie.stats(p);
        Disease d = Vie.disease(s);
        return String.format("%s : forme %.0f · alimentation %.0f · repos %.0f · toxines %.0f · immunité %.0f%s", p.getGameProfile().getName(),
                s.forme, s.nutrition, s.repos, s.toxines, s.immunity(), d == null ? "" : " · " + d.label + " (" + (int) Math.ceil(s.diseaseLeft) + " min)");
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("sante").executes(c -> { VieNet.send(c.getSource().getPlayerOrException(), true); return 1; }));
        e.getDispatcher().register(Commands.literal("vie").requires(s -> s.hasPermission(2))
                .then(Commands.literal("voir").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    String text = line(EntityArgument.getPlayer(c, "joueur"));
                    c.getSource().sendSuccess(() -> Component.literal(text), false);
                    return 1;
                })))
                .then(Commands.literal("definir").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.asList("forme", "nutrition", "repos", "toxines"), b))
                                .then(Commands.argument("valeur", DoubleArgumentType.doubleArg(0, 100)).executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
                                    Stats s = Vie.stats(p);
                                    double v = DoubleArgumentType.getDouble(c, "valeur");
                                    switch (StringArgumentType.getString(c, "stat")) {
                                        case "forme" -> s.forme = v;
                                        case "nutrition" -> s.nutrition = v;
                                        case "repos" -> s.repos = v;
                                        case "toxines" -> s.toxines = v;
                                        default -> { c.getSource().sendFailure(Component.literal("Statistique inconnue.")); return 0; }
                                    }
                                    Vie.dirty(p.server);
                                    c.getSource().sendSuccess(() -> Component.literal(line(p)), false);
                                    return 1;
                                })))))
                .then(Commands.literal("maladie").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Disease.values()).map(d -> d.id), b))
                                .executes(c -> {
                                    Disease d = Disease.byId(StringArgumentType.getString(c, "id"));
                                    if (d == null) { c.getSource().sendFailure(Component.literal("Maladie inconnue (rhume, grippe, gastro, intoxication).")); return 0; }
                                    boolean ok = Vie.infect(EntityArgument.getPlayer(c, "joueur"), d);
                                    if (!ok) c.getSource().sendFailure(Component.literal("Ce joueur est déjà malade."));
                                    return ok ? 1 : 0;
                                }))))
                .then(Commands.literal("guerir").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> Vie.cure(EntityArgument.getPlayer(c, "joueur")) ? 1 : 0)))
                .then(Commands.literal("reset").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
                    VieData.get(p.server).remove(p.getUUID());
                    c.getSource().sendSuccess(() -> Component.literal("Statistiques de " + p.getGameProfile().getName() + " remises à zéro."), false);
                    return 1;
                }))));
    }
}
