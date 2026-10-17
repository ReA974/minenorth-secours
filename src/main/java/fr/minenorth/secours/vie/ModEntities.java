package fr.minenorth.secours.vie;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Siège invisible des machines de sport. */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MineNorthVie.NAMESPACE);

    public static final RegistryObject<EntityType<GymSeat>> SEAT = ENTITIES.register("gym_seat",
            () -> EntityType.Builder.<GymSeat>of(GymSeat::new, MobCategory.MISC).sized(0.1f, 0.1f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(8).updateInterval(20).build("gym_seat"));

    public static void register(IEventBus modBus) { ENTITIES.register(modBus); }
}
