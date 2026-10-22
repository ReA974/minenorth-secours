package fr.minenorth.secours.vie;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quand une entreprise de type restaurant est ouverte (mod Entreprises, dépendance facultative, par réflexion), les joueurs qui n'ont pas encore
 * mangé depuis l'ouverture ont faim bien plus vite : un point de faim toutes les N secondes, jamais sous un plancher (on peut toujours courir).
 * Manger une fois suffit à retrouver le rythme normal jusqu'à la prochaine ouverture. Opérateurs soumis aux mêmes règles ; créatif et spectateur exclus.
 */
public final class RestaurantHunger {
    private boolean open;
    private final Set<UUID> fed = new HashSet<>();
    private final Map<UUID, Integer> clock = new HashMap<>();
    private int checkTicks;

    // réflexion vers minenorth-entreprises, résolue une fois
    private static boolean resolved, available;
    private static Method list, isOpen, dataGet, companyGet;
    private static Field activity;

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> api = Class.forName("fr.minenorth.entreprises.api.EntrepriseApi");
            list = api.getMethod("listCompanies", MinecraftServer.class);
            isOpen = api.getMethod("isOpen", MinecraftServer.class, int.class);
            Class<?> data = Class.forName("fr.minenorth.entreprises.data.EntrepriseData");
            dataGet = data.getMethod("get", MinecraftServer.class);
            companyGet = data.getMethod("get", int.class);
            activity = Class.forName("fr.minenorth.entreprises.data.EntrepriseData$Company").getField("activity");
            available = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            available = false;
        }
    }

    /** Une entreprise ouverte a-t-elle une activité de restaurant ? Faux si le mod Entreprises est absent ou change d'API. */
    private static boolean restaurantOpen(MinecraftServer s) {
        resolve();
        if (!available) return false;
        try {
            Object data = dataGet.invoke(null, s);
            for (Object row : (java.util.List<?>) list.invoke(null, s)) {
                String r = String.valueOf(row);
                int sep = r.indexOf('|');
                if (sep <= 0) continue;
                int id = Integer.parseInt(r.substring(0, sep));
                if (!Boolean.TRUE.equals(isOpen.invoke(null, s, id))) continue;
                Object company = companyGet.invoke(data, id);
                if (company == null) continue;
                String act = String.valueOf(activity.get(company)).toLowerCase(Locale.ROOT);
                for (String k : VieConfig.restaurantKeywords()) if (!k.isBlank() && act.contains(k.toLowerCase(Locale.ROOT))) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            available = false;
        }
        return false;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++checkTicks < 100) return;
        checkTicks = 0;
        MinecraftServer s = e.getServer();
        boolean now = VieConfig.enabled() && VieConfig.restaurantHunger() && restaurantOpen(s);
        if (now && !open) {
            fed.clear();
            clock.clear();
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (p.isCreative() || p.isSpectator()) continue;
                p.displayClientMessage(Component.literal("§eUn restaurant vient d'ouvrir : ça creuse l'appétit !"), true);
            }
        }
        open = now;
    }

    @SubscribeEvent
    public void onEat(LivingEntityUseItemEvent.Finish e) {
        if (open && e.getEntity() instanceof ServerPlayer p && e.getItem().getItem().isEdible()) fed.add(p.getUUID());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        fed.remove(e.getEntity().getUUID());
        clock.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (!open || e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        if (p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        UUID id = p.getUUID();
        if (fed.contains(id)) return;
        int sec = clock.merge(id, 1, Integer::sum);
        if (sec < VieConfig.restaurantSecondsPerPoint()) return;
        clock.put(id, 0);
        var food = p.getFoodData();
        if (food.getFoodLevel() > VieConfig.restaurantHungerFloor()) {
            food.setFoodLevel(food.getFoodLevel() - 1);
            food.setSaturation(0);
            if (food.getFoodLevel() == VieConfig.restaurantHungerFloor() + 4) {
                p.displayClientMessage(Component.literal("§eVous avez faim : un restaurant est ouvert !"), true);
            }
        }
    }
}
