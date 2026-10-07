package fr.minenorth.secours.item;

import fr.minenorth.secours.MineNorthSecours;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthSecours.MOD_ID);
    public static final RegistryObject<Item> TABLET = ITEMS.register("tablette_secours", TabletItem::new);

    /** Onglet « Secours MineNorth » du menu créatif. */
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNorthSecours.MOD_ID);
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("secours", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.minenorthsecours"))
            .icon(() -> new ItemStack(TABLET.get()))
            .displayItems((params, out) -> out.accept(TABLET.get()))
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
