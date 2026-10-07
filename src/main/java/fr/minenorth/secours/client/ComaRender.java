package fr.minenorth.secours.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.minenorth.secours.MineNorthSecours;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Couche à l'écran les joueurs inconscients.
 * On ne passe pas par la « pose » de Minecraft (elle n'est pas toujours respectée selon les mods installés) :
 * on fait simplement pivoter le personnage de 90° au moment où il est dessiné, pour tous les joueurs qui le voient.
 */
@Mod.EventBusSubscriber(modid = MineNorthSecours.MOD_ID, value = Dist.CLIENT)
public final class ComaRender {
    private ComaRender() {}

    /** +1 ou -1 : sens de la bascule. Inverser si le personnage est couché face contre terre. */
    private static final float FLIP = -90f;
    private static boolean pushed;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(RenderPlayerEvent.Pre e) {
        pushed = false;
        Player p = e.getEntity();
        if (!ClientState.customRender || !ClientState.comaPlayers.contains(p.getUUID())) return;
        float yaw = Mth.rotLerp(e.getPartialTick(), p.yBodyRotO, p.yBodyRot);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        pushed = true;
        // Le moteur fait ensuite tourner le corps selon sa direction : on annule cette rotation, on couche, puis on la remet.
        ps.translate(0.0, 0.2, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        ps.mulPose(Axis.XP.rotationDegrees(FLIP));
        ps.translate(0.0, -0.9, 0.0);   // centre le corps sur la position du joueur
        ps.mulPose(Axis.YP.rotationDegrees(-(180f - yaw)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void after(RenderPlayerEvent.Post e) {
        if (pushed) { e.getPoseStack().popPose(); pushed = false; }
    }
}
