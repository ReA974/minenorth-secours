package fr.minenorth.secours.vie;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Portique d'accès de la salle de sport (portillon à vantaux en verre). Il s'ouvre tout seul devant un abonné de l'entreprise de la salle, un membre
 * de cette entreprise ou un opérateur ; sinon il reste fermé et prévient le joueur. Non configuré (pas d'entreprise liée) : il s'ouvre pour tout le monde.
 * Les droits sont revérifiés par le serveur ; le portique se referme quand plus personne d'autorisé n'est devant.
 */
public class GymGateBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    // Axe de passage = Z quand le portique regarde nord / sud ; X quand il regarde est / ouest.
    private static final VoxelShape PILLARS_Z = Shapes.or(Block.box(0, 0, 1, 3, 24, 15), Block.box(13, 0, 1, 16, 24, 15));
    private static final VoxelShape PILLARS_X = Shapes.or(Block.box(1, 0, 0, 15, 24, 3), Block.box(1, 0, 13, 15, 24, 16));
    private static final VoxelShape CLOSED_Z = Shapes.or(PILLARS_Z, Block.box(3, 0, 7, 13, 24, 9));
    private static final VoxelShape CLOSED_X = Shapes.or(PILLARS_X, Block.box(7, 0, 3, 9, 24, 13));

    public GymGateBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 8.0f).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, OPEN); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        boolean z = s.getValue(FACING).getAxis() == Direction.Axis.Z;
        return z ? CLOSED_Z : CLOSED_X;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        boolean z = s.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (s.getValue(OPEN)) return z ? PILLARS_Z : PILLARS_X;
        return z ? CLOSED_Z : CLOSED_X;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GymSiteBE(pos, state); }

    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, Player player,
                                                      net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer p && level.getBlockEntity(pos) instanceof GymSiteBE be && GymAdmin.canManage(p, be)) GymAdmin.open(p, be);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** Appelé par GymAccess quand un joueur autorisé s'approche : ouvre et programme la fermeture. */
    public static void open(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.5f);
        }
        level.scheduleTick(pos, state.getBlock(), 20);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN)) return;
        AABB zone = new AABB(pos).inflate(1.1, 0.6, 1.1);
        for (Player p : level.getEntitiesOfClass(Player.class, zone)) {
            if (p instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof GymSiteBE be && GymPass.allowed(sp, be.company)) {
                level.scheduleTick(pos, this, 10);   // quelqu'un d'autorisé est encore là
                return;
            }
        }
        level.setBlock(pos, state.setValue(OPEN, false), 3);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.5f);
    }

    public static void deny(ServerPlayer p, int company) {
        String name = company > 0 ? fr.minenorth.api.MineNorth.companies().name(p.server, company) : "";
        p.displayClientMessage(Component.literal("§cAbonnement requis" + (name.isBlank() ? "" : " : salle « " + name + " »") + ". Achète-le à la borne d'abonnement."), true);
    }
}
