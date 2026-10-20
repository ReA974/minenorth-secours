package fr.minenorth.secours.vie;

import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Accroupi avec un objet en main, Minecraft ignore le clic droit sur un bloc (il utilise l'objet). Pour la borne de la salle, on force
 * l'utilisation du bloc : sinon un gérant / OP ne pourrait jamais acheter d'abonnement (accroupi + objet en main = achat).
 */
@Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID)
public final class GymKioskEvents {
    private GymKioskEvents() {}

    @SubscribeEvent
    public static void kiosk(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !e.getEntity().isShiftKeyDown() || e.getEntity().getMainHandItem().isEmpty()) return;
        if (!(e.getLevel().getBlockState(e.getPos()).getBlock() instanceof GymKioskBlock)) return;
        e.setUseBlock(Event.Result.ALLOW);
        e.setUseItem(Event.Result.DENY);
    }
}
