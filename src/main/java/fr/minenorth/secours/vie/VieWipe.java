package fr.minenorth.secours.vie;

import fr.minenorth.api.PlayerWipeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Effacement d'un joueur par le panneau admin : toutes ses statistiques de vie sont supprimées. */
public final class VieWipe {
    @SubscribeEvent
    public void onWipe(PlayerWipeEvent e) {
        VieData.get(e.server()).remove(e.player());
        GymPass.get(e.server()).removePlayer(e.player());
        e.cleaned("Vie (santé et mode de vie)");
    }
}
