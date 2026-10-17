package fr.minenorth.secours.vie.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.minenorth.secours.vie.GymSeat;
import fr.minenorth.secours.vie.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/** Animations de la salle de sport vues en première et troisième personne (rendu seulement, rien d'autoritaire). */
public final class GymClient {
    private GymClient() {}

    private static final Map<Integer, GymSeat> ACTIVE = new HashMap<>();
    private static boolean pushed;

    @Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod_ {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(ModEntities.SEAT.get(), NoopRenderer::new); }
    }

    @Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID, value = Dist.CLIENT)
    public static final class Forge_ {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            ACTIVE.clear();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.isPaused()) return;
            for (Entity en : mc.level.entitiesForRendering()) {
                if (!(en instanceof GymSeat s) || s.owner() < 0) continue;
                ACTIVE.put(s.owner(), s);
                if (!(mc.level.getEntity(s.owner()) instanceof Player p)) continue;
                if (s.kind() == fr.minenorth.secours.vie.GymBlock.Kind.TREADMILL) {
                    p.walkAnimation.update(1.1f, 0.6f);          // jambes et bras qui courent sur place
                    if (p == mc.player) { p.walkDistO = p.walkDist; p.walkDist += 0.55f; p.bob = 0.1f; }   // léger balancement en vue à la première personne
                }
            }
        }

        @SubscribeEvent
        public static void pre(RenderPlayerEvent.Pre e) {
            Player p = e.getEntity();
            GymSeat s = ACTIVE.get(p.getId());
            if (s == null || s.isRemoved()) return;
            float yaw = s.getYRot();
            p.yBodyRot = yaw; p.yBodyRotO = yaw;
            PoseStack ps = e.getPoseStack();
            float t = p.tickCount + e.getPartialTick();
            ps.pushPose();
            pushed = true;
            switch (s.kind()) {
                case TREADMILL -> ps.translate(0, 0.035 * Math.abs(Math.sin(t * 0.9)), 0);
                case BIKE -> ps.translate(0, 0.02 * Math.sin(t * 0.7), 0);
                case DUMBBELLS -> ps.translate(0, -0.09 * (0.5 + 0.5 * Math.sin(t * 0.45)), 0);
                case BAG -> {
                    double a = Math.toRadians(yaw);
                    double lunge = 0.07 * Math.max(0, Math.sin(t * 1.05));
                    ps.translate(-Math.sin(a) * lunge, 0, Math.cos(a) * lunge);
                }
                case BENCH -> {
                    // allongé sur le dos, tête vers le fond du banc, genoux en l'air : rotation autour de la hanche
                    double a = Math.toRadians(yaw);
                    double lx = -Math.sin(a), lz = Math.cos(a);
                    ps.translate(0, 0.75, 0);
                    ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(90), (float) -lz, 0, (float) lx));
                    ps.translate(0, -0.75 + 0.02 * Math.sin(t * 0.5), 0);
                }
            }
        }

        @SubscribeEvent
        public static void post(RenderPlayerEvent.Post e) {
            if (!pushed) return;
            pushed = false;
            e.getPoseStack().popPose();
        }
    }
}
