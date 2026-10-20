package fr.minenorth.secours.vie;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Machines de la salle de sport (blocs 3D animés). */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthVie.NAMESPACE);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineNorthVie.NAMESPACE);

    public static final List<RegistryObject<Block>> GYM = new ArrayList<>();
    public static final List<RegistryObject<Item>> GYM_ITEMS = new ArrayList<>();

    static {
        String[] ids = {"tapis_de_course", "banc_musculation", "sac_de_frappe", "velo_appartement", "halteres", "machine_dos"};
        GymBlock.Kind[] kinds = GymBlock.Kind.values();
        for (int i = 0; i < ids.length; i++) {
            GymBlock.Kind k = kinds[i];
            RegistryObject<Block> b = BLOCKS.register(ids[i], () -> new GymBlock(k));
            GYM.add(b);
            GYM_ITEMS.add(ModItems.ITEMS.register(ids[i], () -> new BlockItem(b.get(), new Item.Properties())));
        }
    }

    /** Portique d'accès et borne d'abonnement de la salle (liés à une entreprise). */
    public static final RegistryObject<Block> GATE = BLOCKS.register("portique_salle", GymGateBlock::new);
    public static final RegistryObject<Block> KIOSK = BLOCKS.register("borne_abonnement", GymKioskBlock::new);
    public static final RegistryObject<Item> GATE_ITEM = ModItems.ITEMS.register("portique_salle", () -> new BlockItem(GATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> KIOSK_ITEM = ModItems.ITEMS.register("borne_abonnement", () -> new BlockItem(KIOSK.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<GymSiteBE>> SITE_BE = BLOCK_ENTITIES.register("salle_site",
            () -> BlockEntityType.Builder.of(GymSiteBE::new, GATE.get(), KIOSK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<GymBlockEntity>> GYM_BE = BLOCK_ENTITIES.register("gym",
            () -> BlockEntityType.Builder.of(GymBlockEntity::new, GYM.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
