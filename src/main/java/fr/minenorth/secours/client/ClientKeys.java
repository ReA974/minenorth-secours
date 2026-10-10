package fr.minenorth.secours.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.minenorth.secours.MineNorthSecours;
import fr.minenorth.secours.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Touche « se réveiller à l'hôpital » (R par défaut) : seulement quand on est inconscient et qu'aucun secouriste n'est en service.
 * C'est le « bouton réapparition » : il n'existe pas tant que des secours peuvent venir.
 */
@Mod.EventBusSubscriber(modid = MineNorthSecours.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientKeys {
    private ClientKeys() {}

    static final KeyMapping WAKE = new KeyMapping("key.minenorthsecours.wake", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.minenorthsecours");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent e) { e.register(WAKE); }

    static String wakeKeyName() { return WAKE.getTranslatedKeyMessage().getString(); }

    @Mod.EventBusSubscriber(modid = MineNorthSecours.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Ticks {
        private Ticks() {}

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) return;
            while (WAKE.consumeClick()) {
                if (ClientState.coma && !ClientState.rescuers) ModNetwork.CHANNEL.sendToServer(new ModNetwork.WakePacket());
            }
        }
    }
}
