package fr.minenorth.secours.vie.client;

import fr.minenorth.secours.vie.VieNet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Écran Santé : plus de touche par défaut (H sert aux emotes) ; la touche reste assignable dans les contrôles, et /sante ouvre l'écran. */
public final class ClientEvents {
    private ClientEvents() {}

    public static final KeyMapping KEY = new KeyMapping("key.minenorthvie.sante", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.minenorthvie");

    @Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod_ {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    @Mod.EventBusSubscriber(modid = fr.minenorth.secours.MineNorthSecours.MOD_ID, value = Dist.CLIENT)
    public static final class Forge_ {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) VieNet.CHANNEL.sendToServer(new VieNet.Request(true));
            }
        }
    }
}
