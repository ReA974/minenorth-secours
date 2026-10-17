package fr.minenorth.secours.vie;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Fin de la série en cours (tick de jeu) : tant qu'elle n'est pas atteinte, la machine s'anime. */
public class GymBlockEntity extends BlockEntity {
    public long end;

    public GymBlockEntity(BlockPos pos, BlockState state) { super(ModBlocks.GYM_BE.get(), pos, state); }

    @Override
    protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putLong("fin", end); }

    @Override
    public void load(CompoundTag tag) { super.load(tag); end = tag.getLong("fin"); }
}
