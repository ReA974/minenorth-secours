package fr.minenorth.secours.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.minenorth.secours.MineNorthSecours;
import fr.minenorth.secours.pompier.HydrantBlock;
import fr.minenorth.secours.pompier.Pompier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Tuyau des lances à incendie, dessiné dans le monde (étape « après les entités », comme une entité : compatible Embeddium et Oculus).
 * <p>
 * Le tuyau part de la main du pompier et rejoint la sortie de la bouche d'incendie ou le véhicule. Sa forme est une chaînette qui
 * pend d'autant plus qu'il reste du mou, posée sur le sol (hauteur du terrain relevée sous chaque point) : il se soulève et se tend
 * quand on approche de sa longueur. Tube à 6 pans de 12 cm, raccords en laiton aux deux bouts. Tous les tableaux sont alloués une
 * fois par tuyau ; le profil du sol n'est recalculé que quand un bout bouge.
 */
@Mod.EventBusSubscriber(modid = MineNorthSecours.MOD_ID, value = Dist.CLIENT)
public final class HoseRenderer {
    private HoseRenderer() {}

    private static final ResourceLocation TEXTURE = new ResourceLocation(MineNorthSecours.MOD_ID, "textures/entity/tuyau.png");
    private static final int MAX_POINTS = 40, SIDES = 6;
    private static final float RADIUS = 0.06f, COUPLING_RADIUS = 0.085f;
    /** Texture : tissu rouge en haut (v 0 à 0,72), laiton en bas (v 0,8 à 1). */
    private static final float V_HOSE = 0.72f, V_BRASS0 = 0.8f, V_BRASS1 = 1f;
    private static final float[] COS = new float[SIDES + 1], SIN = new float[SIDES + 1];

    static {
        for (int k = 0; k <= SIDES; k++) { COS[k] = Mth.cos(k * Mth.TWO_PI / SIDES); SIN[k] = Mth.sin(k * Mth.TWO_PI / SIDES); }
    }

    /** Un tuyau : ce qu'envoie le serveur, et les tableaux de travail réutilisés à chaque image. */
    private static final class Link {
        final int player, type, a, b, c, length;
        final float[] x = new float[MAX_POINTS], y = new float[MAX_POINTS], z = new float[MAX_POINTS], ground = new float[MAX_POINTS];
        final int[] light = new int[MAX_POINTS];
        double sx = Double.NaN, sz, ex, ez;   // bouts du dernier relevé du sol
        int n;

        Link(int[] v) { player = v[0]; type = v[1]; a = v[2]; b = v[3]; c = v[4]; length = v[5]; }
    }

    private static volatile List<Link> links = List.of();

    public static void receive(List<int[]> data) {
        List<Link> out = new ArrayList<>(data.size());
        for (int[] v : data) {
            Link keep = null;
            for (Link old : links) if (old.player == v[0] && old.type == v[1] && old.a == v[2] && old.b == v[3] && old.c == v[4] && old.length == v[5]) keep = old;
            out.add(keep != null ? keep : new Link(v));
        }
        links = out;
    }

    // Points de travail (pas d'allocation par image).
    private static double hx, hy, hz, wx, wy, wz;
    private static final BlockPos.MutableBlockPos MPOS = new BlockPos.MutableBlockPos();

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        List<Link> list = links;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (list.isEmpty() || level == null) return;
        float pt = e.getPartialTick();
        Camera cam = e.getCamera();
        double cx = cam.getPosition().x, cy = cam.getPosition().y, cz = cam.getPosition().z;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack ps = e.getPoseStack();
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        boolean drew = false;
        for (Link l : list) {
            Entity ent = level.getEntity(l.player);
            if (!(ent instanceof Player p) || !hand(mc, p, cam, pt)) continue;
            if (!water(level, l, pt)) continue;
            if ((hx - cx) * (hx - cx) + (hz - cz) * (hz - cz) > 128 * 128 && (wx - cx) * (wx - cx) + (wz - cz) * (wz - cz) > 128 * 128) continue;
            shape(level, l);
            draw(vc, pose, normal, l, cx, cy, cz);
            drew = true;
        }
        if (drew) buffers.endBatch(RenderType.entityCutoutNoCull(TEXTURE));
    }

    /** Main qui tient la lance : devant la caméra en vue subjective, sinon au bout du bras du modèle. */
    private static boolean hand(Minecraft mc, Player p, Camera cam, float pt) {
        boolean right = (p.getMainArm() == HumanoidArm.RIGHT) == (p.getMainHandItem().is(Pompier.LANCE.get()));
        float side = right ? 1 : -1;
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
            var look = cam.getLookVector(); var up = cam.getUpVector(); var left = cam.getLeftVector();
            hx = cam.getPosition().x + look.x() * 0.55 - left.x() * 0.32 * side - up.x() * 0.38;
            hy = cam.getPosition().y + look.y() * 0.55 - left.y() * 0.32 * side - up.y() * 0.38;
            hz = cam.getPosition().z + look.z() * 0.55 - left.z() * 0.32 * side - up.z() * 0.38;
            return true;
        }
        double px = Mth.lerp(pt, p.xo, p.getX()), py = Mth.lerp(pt, p.yo, p.getY()), pz = Mth.lerp(pt, p.zo, p.getZ());
        float yaw = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // devant
        double rx = -fz, rz = fx;                         // à droite du joueur
        hx = px + fx * 0.45 + rx * 0.36 * side;
        hy = py + (p.isCrouching() ? 0.75 : 0.95);
        hz = pz + fz * 0.45 + rz * 0.36 * side;
        return true;
    }

    /** Point de branchement : sortie avant de la bouche d'incendie (selon son orientation), ou flanc du véhicule. */
    private static boolean water(ClientLevel level, Link l, float pt) {
        if (l.type == 1) {
            Entity v = l.a < 0 ? null : level.getEntity(l.a);
            if (v == null) return false;
            wx = Mth.lerp(pt, v.xo, v.getX()); wy = Mth.lerp(pt, v.yo, v.getY()) + 0.7; wz = Mth.lerp(pt, v.zo, v.getZ());
            return true;
        }
        MPOS.set(l.a, l.b, l.c);
        BlockState st = level.getBlockState(MPOS);
        if (!st.is(Pompier.HYDRANT.get())) return false;
        Direction f = st.getValue(HydrantBlock.FACING);
        wx = l.a + 0.5 + f.getStepX() * 0.36; wy = l.b + 0.44; wz = l.c + 0.5 + f.getStepZ() * 0.36;
        return true;
    }

    /** Calcule les points : chaînette entre la main et le point d'eau, posée sur le sol. */
    private static void shape(ClientLevel level, Link l) {
        double dx = wx - hx, dy = wy - hy, dz = wz - hz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = Mth.clamp((int) Math.ceil(dist * 1.3) + 2, 8, MAX_POINTS);
        boolean moved = n != l.n || Double.isNaN(l.sx) || Math.abs(hx - l.sx) + Math.abs(hz - l.sz) > 0.3 || Math.abs(wx - l.ex) + Math.abs(wz - l.ez) > 0.3;
        // Mou du tuyau : beaucoup de jeu = il traîne au sol ; presque tendu = il se soulève.
        double slack = Math.max(0, l.length - dist);
        double sag = Mth.clamp(slack * 0.6, 0.12, 8);
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            double x = hx + dx * t, z = hz + dz * t;
            double y = hy + dy * t - sag * 4 * t * (1 - t);
            if (moved) {
                l.ground[i] = ground(level, x, Math.max(hy, wy) + 0.5, z);
                MPOS.set(Mth.floor(x), Mth.floor(Math.max(y, l.ground[i] + 0.1)), Mth.floor(z));
                l.light[i] = LevelRenderer.getLightColor(level, MPOS);
            }
            if (i > 0 && i < n - 1) y = Math.max(y, l.ground[i] + RADIUS + 0.01);
            l.x[i] = (float) x; l.y[i] = (float) y; l.z[i] = (float) z;
        }
        if (moved) { l.sx = hx; l.sz = hz; l.ex = wx; l.ez = wz; l.n = n; }
    }

    /** Hauteur du dessus du premier bloc solide sous (x, z), en partant de top (12 blocs au plus). */
    private static float ground(ClientLevel level, double x, double top, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(top);
        for (int y = by; y > by - 12; y--) {
            MPOS.set(bx, y, bz);
            BlockState st = level.getBlockState(MPOS);
            if (st.isAir()) continue;
            VoxelShape shape = st.getCollisionShape(level, MPOS);
            if (!shape.isEmpty()) return (float) (y + shape.max(Direction.Axis.Y));
        }
        return (float) (by - 12);
    }

    // Repère du tronçon courant (réutilisé).
    private static float nx, ny, nz, bx, by, bz;

    private static void draw(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Link l, double cx, double cy, double cz) {
        int n = l.n;
        for (int i = 0; i + 1 < n; i++) {
            int i0 = Math.max(0, i - 1), i2 = Math.min(n - 1, i + 2);
            tube(vc, pose, normal, l, i, i + 1, i0, i2, RADIUS, (i & 1) == 0 ? 0 : V_HOSE, (i & 1) == 0 ? V_HOSE : 0, cx, cy, cz);
        }
        coupling(vc, pose, normal, l, 0, 1, cx, cy, cz);
        coupling(vc, pose, normal, l, n - 1, n - 2, cx, cy, cz);
    }

    /** Repère perpendiculaire à la direction (tx, ty, tz) normalisée : (nx, ny, nz) et (bx, by, bz). */
    private static void frame(float tx, float ty, float tz) {
        // n = t x haut (ou t x est si le tronçon est vertical)
        float ax = 0, ay = 1, az = 0;
        if (Math.abs(ty) > 0.95f) { ax = 1; ay = 0; }
        nx = ty * az - tz * ay; ny = tz * ax - tx * az; nz = tx * ay - ty * ax;
        float ln = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= ln; ny /= ln; nz /= ln;
        bx = ny * tz - nz * ty; by = nz * tx - nx * tz; bz = nx * ty - ny * tx;
    }

    /** Tronçon i -> j du tube ; i0 et j2 servent à lisser l'orientation des anneaux. */
    private static void tube(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Link l, int i, int j, int i0, int j2, float r,
                             float v0, float v1, double cx, double cy, double cz) {
        float tx = l.x[j2] - l.x[i0], ty = l.y[j2] - l.y[i0], tz = l.z[j2] - l.z[i0];
        float lt = Mth.sqrt(tx * tx + ty * ty + tz * tz);
        if (lt < 1e-4f) return;
        frame(tx / lt, ty / lt, tz / lt);
        float ax = (float) (l.x[i] - cx), ay = (float) (l.y[i] - cy), az = (float) (l.z[i] - cz);
        float qx = (float) (l.x[j] - cx), qy = (float) (l.y[j] - cy), qz = (float) (l.z[j] - cz);
        ring(vc, pose, normal, ax, ay, az, qx, qy, qz, r, v0, v1, l.light[i], l.light[j]);
    }

    /** Raccord en laiton : manchon plus gros sur 18 cm au bout du tuyau (bout = from, vers = to). */
    private static void coupling(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Link l, int from, int to, double cx, double cy, double cz) {
        float tx = l.x[to] - l.x[from], ty = l.y[to] - l.y[from], tz = l.z[to] - l.z[from];
        float lt = Mth.sqrt(tx * tx + ty * ty + tz * tz);
        if (lt < 1e-4f) return;
        tx /= lt; ty /= lt; tz /= lt;
        frame(tx, ty, tz);
        float ax = (float) (l.x[from] - cx), ay = (float) (l.y[from] - cy), az = (float) (l.z[from] - cz);
        float len = Math.min(0.18f, lt);
        ring(vc, pose, normal, ax, ay, az, ax + tx * len, ay + ty * len, az + tz * len, COUPLING_RADIUS, V_BRASS0, V_BRASS1, l.light[from], l.light[from]);
    }

    /** Pans du tube entre deux anneaux centrés en a et q, avec le repère (n, b) courant. */
    private static void ring(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float ax, float ay, float az, float qx, float qy, float qz,
                             float r, float v0, float v1, int la, int lq) {
        for (int k = 0; k < SIDES; k++) {
            float c0 = COS[k], s0 = SIN[k], c1 = COS[k + 1], s1 = SIN[k + 1];
            float ox0 = (c0 * nx + s0 * bx), oy0 = (c0 * ny + s0 * by), oz0 = (c0 * nz + s0 * bz);
            float ox1 = (c1 * nx + s1 * bx), oy1 = (c1 * ny + s1 * by), oz1 = (c1 * nz + s1 * bz);
            float u0 = k / (float) SIDES, u1 = (k + 1) / (float) SIDES;
            vertex(vc, pose, normal, ax + ox0 * r, ay + oy0 * r, az + oz0 * r, u0, v0, la, ox0, oy0, oz0);
            vertex(vc, pose, normal, qx + ox0 * r, qy + oy0 * r, qz + oz0 * r, u0, v1, lq, ox0, oy0, oz0);
            vertex(vc, pose, normal, qx + ox1 * r, qy + oy1 * r, qz + oz1 * r, u1, v1, lq, ox1, oy1, oz1);
            vertex(vc, pose, normal, ax + ox1 * r, ay + oy1 * r, az + oz1 * r, u1, v0, la, ox1, oy1, oz1);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v, int light,
                               float nx, float ny, float nz) {
        vc.vertex(pose, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, nx, ny, nz).endVertex();
    }
}
