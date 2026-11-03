package fr.minenorth.secours.pompier;

import fr.minenorth.secours.MineNorthSecours;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Matériel des pompiers : foyers et braises des incendies, bouche d'incendie, lance, hache, halligan, caméra thermique,
 * couverture anti-feu et masque ARI.
 */
public final class Pompier {
    private Pompier() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthSecours.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthSecours.MOD_ID);

    /** Flamme d'incendie : ne vieillit pas, ne se propage pas, ne brûle rien ; seuls les pompiers (ou l'eau) l'éteignent. */
    public static final RegistryObject<Block> FOYER = BLOCKS.register("foyer", FoyerBlock::new);
    /** Point chaud invisible qui fume : repart en flamme s'il n'est pas noyé à temps. */
    public static final RegistryObject<Block> BRAISE = BLOCKS.register("braise", BraiseBlock::new);
    public static final RegistryObject<Block> HYDRANT = BLOCKS.register("bouche_incendie", HydrantBlock::new);

    public static final RegistryObject<Item> HYDRANT_ITEM = ITEMS.register("bouche_incendie", () -> new BlockItem(HYDRANT.get(), new Item.Properties()));
    public static final RegistryObject<Item> LANCE = ITEMS.register("lance_incendie", HoseItem::new);
    /** Rouleau de tuyau : nécessaire pour brancher la lance (lance_longueur_tuyau blocs par rouleau, 3 rouleaux au plus). */
    public static final RegistryObject<Item> ROULEAU = ITEMS.register("rouleau_tuyau", () -> new Item(new Item.Properties().stacksTo(3)) {
        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack st, @javax.annotation.Nullable net.minecraft.world.level.Level level,
                                    java.util.List<net.minecraft.network.chat.Component> tip, net.minecraft.world.item.TooltipFlag flag) {
            tip.add(net.minecraft.network.chat.Component.literal("§7Nécessaire pour brancher la lance à incendie"));
            tip.add(net.minecraft.network.chat.Component.literal("§7" + fr.minenorth.secours.config.SecoursConfig.get().lance_longueur_tuyau + " m par rouleau, 3 rouleaux au plus"));
        }
    });
    public static final RegistryObject<Item> HACHE = ITEMS.register("hache_pompier", () -> new ForceToolItem(80, 250));
    public static final RegistryObject<Item> HALLIGAN = ITEMS.register("halligan", () -> new ForceToolItem(50, 400));
    public static final RegistryObject<Item> CAMERA = ITEMS.register("camera_thermique", ThermalCameraItem::new);
    public static final RegistryObject<Item> COUVERTURE = ITEMS.register("couverture_antifeu", BlanketItem::new);
    public static final RegistryObject<Item> MASQUE = ITEMS.register("masque_ari", AriMaskItem::new);
    /** Bouteille d'air de l'ARI : sans elle (dans l'inventaire), le masque ne protège pas de la fumée. */
    public static final RegistryObject<Item> BOUTEILLE = ITEMS.register("bouteille_ari", AriBottleItem::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    /** Ordre d'affichage dans l'onglet créatif. */
    public static java.util.List<Item> tabItems() {
        return java.util.List.of(LANCE.get(), ROULEAU.get(), HYDRANT_ITEM.get(), HACHE.get(), HALLIGAN.get(), CAMERA.get(), COUVERTURE.get(), MASQUE.get(), BOUTEILLE.get());
    }

    /** Membre des pompiers / SAMU (pas forcément en service). */
    public static boolean isSecours(net.minecraft.server.level.ServerPlayer p) {
        return fr.minenorth.secours.api.SecoursApi.grade(p.server, p.getUUID()) >= 0;
    }
}
