package fr.minenorth.secours;

import fr.minenorth.secours.config.SecoursConfig;
import fr.minenorth.secours.item.ModItems;
import fr.minenorth.secours.network.ModNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MineNorthSecours.MOD_ID)
public class MineNorthSecours {
    public static final String MOD_ID = "minenorthsecours";

    public MineNorthSecours() {
        SecoursConfig.load();
        ModNetwork.register();
        fr.minenorth.api.MineNorth.provide(fr.minenorth.api.SecoursService.class, new fr.minenorth.secours.api.SecoursProvider());
        fr.minenorth.secours.compat.TaczCompat.register();
        ModItems.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
