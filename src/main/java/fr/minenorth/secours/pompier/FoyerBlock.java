package fr.minenorth.secours.pompier;

import fr.minenorth.secours.FireService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Foyer d'incendie de service. Contrairement au feu vanilla il ne vieillit pas, ne s'éteint pas seul (pluie, sol en pierre),
 * ne se propage pas et ne détruit aucun bloc : c'est FireService qui décide de la propagation. Il est dans le tag
 * minecraft:fire, donc l'extincteur et le canon à eau d'Immersive Vehicles (MTS), les potions d'eau et le seau d'eau l'éteignent,
 * comme un coup de poing, la lance, la couverture anti-feu.
 * <p>
 * Toutes les 10 s environ le bloc vérifie qu'un incendie en cours le suit encore ; sinon il disparaît (incendie annulé pendant
 * que la zone n'était pas chargée, /setblock d'un admin…). Il ne peut donc jamais brûler indéfiniment.
 */
public class FoyerBlock extends BaseFireBlock {
    public FoyerBlock() {
        super(Properties.of().mapColor(MapColor.FIRE).replaceable().noCollission().instabreak().lightLevel(s -> 15)
                .sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY).noLootTable(), 1.0f);
    }

    @Override
    protected boolean canBurn(BlockState state) { return false; }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, 200 + level.random.nextInt(60));
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rnd) {
        if (FireService.tracked(level, pos)) level.scheduleTick(pos, this, 200 + rnd.nextInt(60));
        else level.removeBlock(pos, false);
    }
}
