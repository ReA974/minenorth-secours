package fr.minenorth.secours.vie;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ouvre les portiques de la salle devant les joueurs autorisés (vérification serveur, toutes les 8 ticks, uniquement dans le couloir de chaque portique). */
@Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID)
public final class GymAccess {
    private GymAccess() {}

    private static final Map<UUID, Long> LAST_DENY = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 8 != 0 || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        BlockPos c = p.blockPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = c.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(pos);
                    if (!(st.getBlock() instanceof GymGateBlock) || !GymGateBlock.inCorridor(st, pos, p) || !(level.getBlockEntity(pos) instanceof GymSiteBE be)) continue;
                    if (GymPass.allowed(p, be.company)) {
                        GymGateBlock.open(level, pos, st);
                    } else {
                        long now = level.getGameTime();
                        Long last = LAST_DENY.get(p.getUUID());
                        if (last == null || now - last > 60) {
                            LAST_DENY.put(p.getUUID(), now);
                            GymGateBlock.deny(p, be.company);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        LAST_DENY.remove(e.getEntity().getUUID());
    }
}
