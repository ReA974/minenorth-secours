package fr.minenorth.secours;

import fr.minenorth.api.PlayerWipeEvent;
import fr.minenorth.secours.data.SecoursData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Suppression d'un joueur depuis le panneau admin : blessures, grade, nom et dossier médical effacés. */
@Mod.EventBusSubscriber(modid = MineNorthSecours.MOD_ID)
public final class SecoursWipe {
    private SecoursWipe() {}

    @SubscribeEvent
    public static void onWipe(PlayerWipeEvent e) {
        SecoursData d = SecoursData.get(e.server());
        boolean any = d.injuries.remove(e.player()) != null;
        any |= d.staff.remove(e.player()) != null;
        any |= d.names.remove(e.player()) != null;
        any |= d.files.remove(e.player()) != null;
        if (any) {
            d.setDirty();
            e.cleaned("secours");
        }
    }
}
