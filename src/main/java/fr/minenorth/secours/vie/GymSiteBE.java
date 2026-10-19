package fr.minenorth.secours.vie;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Portique ou borne d'abonnement : entreprise propriétaire (0 = non configuré), prix (centimes) et durée (jours) de l'abonnement vendu. */
public class GymSiteBE extends BlockEntity {
    public int company;
    public int priceCents = 2000;
    public int days = 30;

    public GymSiteBE(BlockPos pos, BlockState state) { super(ModBlocks.SITE_BE.get(), pos, state); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("entreprise", company);
        tag.putInt("prix", priceCents);
        tag.putInt("jours", days);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        company = Math.max(0, tag.getInt("entreprise"));
        priceCents = tag.contains("prix") ? Math.max(0, Math.min(10_000_000, tag.getInt("prix"))) : 2000;
        days = tag.contains("jours") ? Math.max(1, Math.min(365, tag.getInt("jours"))) : 30;
    }
}
