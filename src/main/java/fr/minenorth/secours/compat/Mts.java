package fr.minenorth.secours.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lien avec Immersive Vehicles / MTS (modid « mts »), par réflexion : rien n'est requis à la compilation.
 * Un véhicule est une entité « mts:builder_existing » (objet MTS dans son champ « entity »), un siège est une entité
 * « mts:builder_seat ». Les pièces « siège » (PartSeat) du véhicule portent un nom de définition (systemName) :
 * « seat_brancard » pour le brancard des véhicules de secours. Si MTS change, on retombe sur le comportement vanilla.
 */
public final class Mts {
    private Mts() {}

    /** Pièce « brancard » du pack de véhicules (tools/seats.py). */
    public static final String STRETCHER_SEAT = "seat_brancard";

    private static boolean is(Entity e, String path) {
        if (e == null) return false;
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k != null && "mts".equals(k.getNamespace()) && path.equals(k.getPath());
    }

    public static boolean isBuilder(Entity e) { return is(e, "builder_existing"); }

    public static boolean isSeat(Entity e) { return is(e, "builder_seat"); }

    @Nullable
    private static Object field(Object o, String name) {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /** Pièces siège du véhicule dont le systemName vaut name (null = tous les sièges). */
    private static List<Object> seats(Entity vehicle, @Nullable String name) {
        List<Object> out = new ArrayList<>();
        try {
            if (!isBuilder(vehicle) || !(field(field(vehicle, "entity"), "allParts") instanceof Iterable<?> parts)) return out;
            for (Object part : parts) {
                if (!part.getClass().getSimpleName().equals("PartSeat")) continue;
                if (name != null && !(field(field(part, "definition"), "systemName") instanceof String s && s.equals(name))) continue;
                out.add(part);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static boolean occupiedBy(Object seat, UUID id) {
        try {
            Object rider = field(seat, "rider");
            return rider != null && id.equals(rider.getClass().getMethod("getID").invoke(rider));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Vrai si ce véhicule MTS possède un siège de ce type (libre ou non). */
    public static boolean hasSeat(Entity vehicle, @Nullable String name) { return !seats(vehicle, name).isEmpty(); }

    /** Véhicule MTS à qui appartient l'entité cliquée (le véhicule lui-même, ou le plus proche d'un siège cliqué). */
    @Nullable
    public static Entity vehicleOf(Entity clicked) {
        if (isBuilder(clicked)) return clicked;
        if (!isSeat(clicked)) return null;
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : clicked.level().getEntities((Entity) null, clicked.getBoundingBox().inflate(12), Mts::isBuilder)) {
            double d = e.distanceToSqr(clicked);
            if (d < bd && !seats(e, null).isEmpty()) { bd = d; best = e; }
        }
        return best;
    }

    /** Installe le joueur dans le premier siège libre de ce type (name null = n'importe quel siège). */
    public static boolean seatPlayer(ServerPlayer p, Entity vehicle, @Nullable String name) {
        try {
            Object wrapper = Class.forName("mcinterface1201.WrapperPlayer").getMethod("getWrapperFor", Player.class).invoke(null, p);
            for (Object seat : seats(vehicle, name)) {
                if (field(seat, "rider") != null) continue;
                for (Method m : seat.getClass().getMethods()) {
                    if (m.getName().equals("setRider") && m.getParameterCount() == 2) {
                        if (Boolean.TRUE.equals(m.invoke(seat, wrapper, Boolean.TRUE))) return true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Vrai si le joueur est assis dans un siège MTS. */
    public static boolean isSeated(ServerPlayer p) { return isSeat(p.getVehicle()); }

    /** Retire le joueur de son siège MTS ; renvoie false si on n'a pas trouvé le siège (le joueur est alors descendu à la façon vanilla). */
    public static boolean unseat(ServerPlayer p) {
        Entity ride = p.getVehicle();
        if (!isSeat(ride)) return false;
        try {
            for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(24), Mts::isBuilder)) {
                for (Object seat : seats(e, null)) {
                    if (occupiedBy(seat, p.getUUID())) { seat.getClass().getMethod("removeRider").invoke(seat); return true; }
                }
            }
        } catch (Throwable ignored) {}
        p.stopRiding();
        return false;
    }
}
