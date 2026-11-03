package fr.minenorth.secours.pompier;

import fr.minenorth.secours.FireService;
import fr.minenorth.secours.config.SecoursConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Braise cachée laissée par un incendie éteint trop vite : invisible, elle ne fait que fumer. La caméra thermique la montre.
 * Elle est dans le tag minecraft:fire : la lance, l'extincteur MTS, l'eau et la couverture la noient. Au bout de
 * incendie_braises_secondes, si elle est toujours là, le feu repart à cet endroit (FireService.rekindle).
 */
public class BraiseBlock extends Block {
    public BraiseBlock() {
        super(Properties.of().replaceable().noCollission().instabreak().pushReaction(PushReaction.DESTROY).noLootTable().noOcclusion());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return Shapes.empty(); }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, SecoursConfig.get().incendie_braises_secondes * 20);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rnd) {
        if (!FireService.rekindle(level, pos)) level.removeBlock(pos, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        if (rnd.nextInt(3) == 0)
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + rnd.nextDouble(), pos.getY() + 0.1, pos.getZ() + rnd.nextDouble(), 0, 0.03, 0);
        if (rnd.nextInt(12) == 0)
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 0, 0.04, 0);
    }
}
