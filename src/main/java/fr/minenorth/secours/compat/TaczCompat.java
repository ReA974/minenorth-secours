package fr.minenorth.secours.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lien avec TACZ, par réflexion (le mod compile et démarre sans TACZ).
 * On écoute l'événement « une entité va être blessée par une arme » pour savoir où la balle touche :
 * TACZ indique lui-même les tirs à la tête ; le reste est déduit du point d'impact sur le corps.
 */
public final class TaczCompat {
    private TaczCompat() {}

    public static final int AUCUNE = 0, TETE = 1, TORSE = 2, BRAS = 3, JAMBES = 4;
    public static final String[] ZONES = {"", "tête", "torse", "bras", "jambe"};

    private record Hit(boolean headshot, Vec3 impact, long time) {}
    private static final Map<UUID, Hit> PENDING = new HashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Class<?> event = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class) event, (Consumer) e -> onHurt((Event) e));
        } catch (Throwable ignored) {
            // TACZ absent ou version différente : les balles restent détectées, mais toujours comptées « torse ».
        }
    }

    private static void onHurt(Event e) {
        try {
            if (!"SERVER".equals(String.valueOf(e.getClass().getMethod("getLogicalSide").invoke(e)))) return;
            if (!(e.getClass().getMethod("getHurtEntity").invoke(e) instanceof ServerPlayer victim)) return;
            boolean head = Boolean.TRUE.equals(e.getClass().getMethod("isHeadShot").invoke(e));
            Vec3 impact = null;
            if (e.getClass().getMethod("getBullet").invoke(e) instanceof Entity bullet) {
                Vec3 start = bullet.position(), dir = bullet.getDeltaMovement();
                AABB box = victim.getBoundingBox().inflate(0.1);
                if (box.contains(start)) impact = start;
                else if (dir.lengthSqr() > 1.0e-6) {
                    Vec3 end = start.add(dir.normalize().scale(start.distanceTo(victim.position()) + 4.0));
                    impact = box.clip(start, end).orElse(null);
                }
            }
            PENDING.put(victim.getUUID(), new Hit(head, impact, System.currentTimeMillis()));
        } catch (Throwable ignored) {}
    }

    /** Zone touchée par la balle qui vient de blesser ce joueur. Sans information : torse. */
    public static int zone(ServerPlayer p) {
        Hit h = PENDING.remove(p.getUUID());
        if (h == null || System.currentTimeMillis() - h.time() > 1000) return TORSE;
        if (h.headshot()) return TETE;
        if (h.impact() == null) return TORSE;
        double rel = (h.impact().y - p.getY()) / Math.max(0.1, p.getBbHeight());
        if (rel >= 0.80) return TETE;
        if (rel < 0.45) return JAMBES;
        // Entre les deux : bras si la balle touche le bord du corps, torse si elle touche le centre.
        double yaw = Math.toRadians(p.yBodyRot);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        double lateral = (h.impact().x - p.getX()) * rightX + (h.impact().z - p.getZ()) * rightZ;
        return Math.abs(lateral) > 0.18 ? BRAS : TORSE;
    }

    public static int bit(int zone) { return zone <= 0 ? 0 : 1 << zone; }
    public static boolean has(int zones, int zone) { return (zones & bit(zone)) != 0; }

    /** "jambe, bras" à partir du masque de zones. */
    public static String describe(int zones) {
        StringBuilder sb = new StringBuilder();
        for (int z = TETE; z <= JAMBES; z++) if (has(zones, z)) sb.append(sb.length() > 0 ? ", " : "").append(ZONES[z]);
        return sb.toString();
    }
}
