package fr.minenorth.secours.vie;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /salle delier : seule commande restante, secours des opérateurs. */
@Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID)
public final class GymCommands {
    private GymCommands() {}

    /** Secours opérateur : délie l'équipement regardé (tout le reste se règle par l'écran, accroupi + clic droit). */
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("salle").requires(s -> s.hasPermission(2))
                .then(Commands.literal("delier").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    HitResult h = p.pick(6.0, 0.0f, false);
                    if (h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK && p.level().getBlockEntity(b.getBlockPos()) instanceof GymSiteBE be) {
                        be.company = 0;
                        be.setChanged();
                        p.sendSystemMessage(Component.literal("§7Équipement délié."));
                        return 1;
                    }
                    p.sendSystemMessage(Component.literal("§cRegarde un portique ou une borne (6 blocs au plus)."));
                    return 0;
                })));
    }
}
