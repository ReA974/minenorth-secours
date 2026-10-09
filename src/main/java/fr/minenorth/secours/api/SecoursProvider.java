package fr.minenorth.secours.api;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Fournit « pompier / SAMU en service » aux autres mods via MineNorth API (concessions et garages de secours). */
public final class SecoursProvider implements fr.minenorth.api.SecoursService {
    @Override public boolean isSecours(MinecraftServer s, UUID player) { return player != null && SecoursApi.grade(s, player) >= 0; }
    @Override public boolean onDuty(MinecraftServer s, UUID player) { return player != null && SecoursApi.onDuty(s, player); }
    @Override public boolean setDuty(MinecraftServer s, UUID player, boolean on) {
        net.minecraft.server.level.ServerPlayer p = player == null ? null : s.getPlayerList().getPlayer(player);
        return p != null && fr.minenorth.secours.SecoursService.setDuty(p, on);
    }
}
