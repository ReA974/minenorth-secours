package fr.minenorth.secours.vie;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Machines de la salle de sport : tapis de course, banc de musculation, sac de frappe, vélo d'appartement, haltères. Clic droit = une série
 * (4 secondes, la machine s'anime en 3D) qui donne de la forme et élimine des toxines ; petit temps de repos entre deux séries. Pas de
 * contrainte : on s'amuse, la forme reste plafonnée par heure comme la course (anti-triche, calcul côté serveur).
 */
public class GymBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 0 = repos ; 1 à 3 = images de l'animation. */
    public static final IntegerProperty ANIM = IntegerProperty.create("anim", 0, 3);
    /** Vrai pour la 2e case (sans modèle) du tapis et du banc, qui occupent 2 blocs de long. */
    public static final BooleanProperty EXT = BooleanProperty.create("ext");
    private static final int SESSION_TICKS = 140, REST_TICKS = 200;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public enum Kind {
        // Position de l'utilisateur dans le repère du bloc (orientation par défaut, nord) : dx/dz en blocs depuis le centre, y = pieds (debout)
        // ou assise (assis). Les modèles sont plus grands qu'un bloc : ils débordent sur les cases voisines, laissez un peu de place.
        TREADMILL("Tapis de course", 2.0, SoundEvents.NOTE_BLOCK_HAT.value(), ParticleTypes.CLOUD, 0, 0.4, 0.26, false, false, false, 0, 0, 0.9, Block.box(0, 0, 0, 16, 4, 16)),
        BENCH("Banc de musculation", 2.5, SoundEvents.ANVIL_PLACE, ParticleTypes.CRIT, 0, 0.55, 0.5, true, true, false, 18, 1.1, 0, Block.box(0, 0, 0, 16, 12, 16)),
        BAG("Sac de frappe", 2.0, SoundEvents.PLAYER_ATTACK_STRONG, ParticleTypes.CRIT, 0, -0.65, 0, false, false, false, 6, 0, 0, Block.box(4, 0, 4, 12, 16, 12)),
        BIKE("Vélo d'appartement", 2.0, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), ParticleTypes.SPLASH, 0, 0.22, 0.63, true, false, true, 0, 1.0, 0, Block.box(2, 0, 2, 14, 10, 14)),
        DUMBBELLS("Rack d'haltères", 1.5, SoundEvents.ANVIL_LAND, ParticleTypes.CRIT, 0, -0.85, 0, false, false, false, 14, 0, 0, Block.box(0, 0, 3, 16, 10, 13)),
        BACK("Machine de tirage (dos)", 2.2, SoundEvents.ANVIL_PLACE, ParticleTypes.CRIT, 0, 0.36, 0.32, true, false, true, 0, 1.2, 0, Block.box(0, 0, 0, 16, 16, 16));

        public final String label;
        final double gain;
        final SoundEvent sound;
        final ParticleOptions particle;
        final double dx, dz, y;
        final boolean seated, lying, front;
        final int swing;
        final double exitDx, exitDz;
        final VoxelShape collision;

        /** Le tapis et le banc occupent 2 blocs de long (le modèle de la case principale déborde sur la 2e). */
        public boolean isLong() { return this == TREADMILL || this == BENCH || this == BAG; }

        /** Le sac de frappe occupe 2 blocs de HAUT (la 2e case est au-dessus) ; le tapis et le banc 2 blocs de long. */
        public boolean isTall() { return this == BAG; }

        Kind(String label, double gain, SoundEvent sound, ParticleOptions particle, double dx, double dz, double y, boolean seated, boolean lying,
             boolean front, int swing, double exitDx, double exitDz, VoxelShape collision) {
            this.label = label; this.gain = gain; this.sound = sound; this.particle = particle; this.dx = dx; this.dz = dz; this.y = y;
            this.seated = seated; this.lying = lying; this.front = front; this.swing = swing; this.exitDx = exitDx; this.exitDz = exitDz;
            this.collision = collision;
        }
    }

    /** Point du monde correspondant à un décalage (dx, dz) du repère « nord » du bloc, tourné selon l'orientation du bloc. */
    static Vec3 world(BlockPos pos, Direction f, double dx, double dz, double y) {
        double rx, rz;
        switch (f) {
            case EAST -> { rx = -dz; rz = dx; }
            case SOUTH -> { rx = -dx; rz = -dz; }
            case WEST -> { rx = dz; rz = -dx; }
            default -> { rx = dx; rz = dz; }
        }
        return new Vec3(pos.getX() + 0.5 + rx, pos.getY() + y, pos.getZ() + 0.5 + rz);
    }

    private static final VoxelShape SHAPE_FULL = Block.box(0, 0, 0, 16, 16, 16), SHAPE_LOW = Block.box(0, 0, 0, 16, 12, 16);

    private final Kind kind;

    public GymBlock(Kind kind) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ANIM, 0).setValue(EXT, false));
    }

    public Kind kind() { return kind; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ANIM, EXT); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection().getOpposite();
        if (kind.isLong()) {
            BlockPos ext = ctx.getClickedPos().relative(kind.isTall() ? Direction.UP : f.getOpposite());
            if (!ctx.getLevel().getWorldBorder().isWithinBounds(ext) || !ctx.getLevel().getBlockState(ext).canBeReplaced(ctx)) return null;
        }
        return defaultBlockState().setValue(FACING, f);
    }

    /** Pose la 2e case du tapis / du banc. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && kind.isLong() && !state.getValue(EXT)) {
            level.setBlock(pos.relative(kind.isTall() ? Direction.UP : state.getValue(FACING).getOpposite()), state.setValue(EXT, true).setValue(ANIM, 0), 3);
        }
    }

    /** Case principale d'une extension (la partie visible et active). */
    private BlockPos mainPos(BlockPos pos, BlockState s) { return pos.relative(kind.isTall() ? Direction.DOWN : s.getValue(FACING)); }

    /** Une moitié disparaît : l'autre aussi (sans doublon d'objet). */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (kind.isLong()) {
            Direction partner = kind.isTall() ? (state.getValue(EXT) ? Direction.DOWN : Direction.UP)
                    : state.getValue(EXT) ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
            if (dir == partner && !(neighbor.is(this) && neighbor.getValue(EXT) != state.getValue(EXT))) return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Casser la 2e case fait tomber l'objet une seule fois (par la case principale). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && kind.isLong() && state.getValue(EXT)) {
            BlockPos main = mainPos(pos, state);
            BlockState ms = level.getBlockState(main);
            if (ms.is(this) && !ms.getValue(EXT)) {
                if (player.isCreative()) level.setBlock(main, Blocks.AIR.defaultBlockState(), 35);
                else level.destroyBlock(main, true, player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return kind == Kind.DUMBBELLS ? SHAPE_LOW : SHAPE_FULL; }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return kind.collision; }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.getValue(EXT) ? null : new GymBlockEntity(pos, state); }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (state.getValue(EXT)) {   // 2e case : on agit sur la case principale
            BlockPos main = mainPos(pos, state);
            BlockState ms = level.getBlockState(main);
            return ms.is(this) && !ms.getValue(EXT) ? use(ms, level, main, player, hand, hit) : InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel sl) || !(level.getBlockEntity(pos) instanceof GymBlockEntity be)) return InteractionResult.PASS;
        long now = level.getGameTime();
        if (be.end > now) { p.displayClientMessage(Component.literal("§7La machine est déjà utilisée."), true); return InteractionResult.SUCCESS; }
        if (p.isPassenger()) { p.displayClientMessage(Component.literal("§7Descendez d'abord de votre monture."), true); return InteractionResult.SUCCESS; }
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < SESSION_TICKS) { p.displayClientMessage(Component.literal("§7Vous êtes déjà en séance."), true); return InteractionResult.SUCCESS; }
        if (last != null && now - last < REST_TICKS) {
            p.displayClientMessage(Component.literal("§7Reprenez votre souffle… (" + ((REST_TICKS - (now - last) + 19) / 20) + " s)"), true);
            return InteractionResult.SUCCESS;
        }
        LAST.put(p.getUUID(), now);
        be.end = now + SESSION_TICKS;
        be.setChanged();
        String msg;
        if (VieConfig.enabled()) {
            Stats s = Vie.stats(p);
            double gain = kind.gain * Math.max(0.25, 1.15 - s.forme / 100.0) * (1 + (s.wheyLeft > 0 ? 0.4 : 0) + (s.creaLeft > 0 ? 0.25 : 0));
            if (s.hourGain + gain <= VieConfig.trainMaxHour()) {
                s.forme = Stats.clamp(s.forme + gain);
                s.hourGain += gain;
                s.idleMinutes = 0;
                s.toxines = Math.max(0, s.toxines - 2);   // on transpire : le sport élimine les toxines
                Vie.dirty(p.server);
                msg = String.format("§a%s : série lancée ! Forme +%.1f", kind.label, gain);
            } else {
                msg = "§e" + kind.label + " : vous vous faites plaisir, mais vous êtes déjà bien chaud pour l'heure (forme au maximum de l'heure).";
            }
        } else {
            msg = "§a" + kind.label + " : série lancée !";
        }
        p.displayClientMessage(Component.literal(msg), true);
        sl.setBlock(pos, state.setValue(ANIM, 1), 3);
        sl.scheduleTick(pos, this, 2);
        mount(sl, pos, state.getValue(FACING), p, be.end);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof GymBlockEntity be)) return;
        long now = level.getGameTime();
        if (now >= be.end) {
            if (state.getValue(ANIM) != 0) level.setBlock(pos, state.setValue(ANIM, 0), 3);
            return;
        }
        level.setBlock(pos, state.setValue(ANIM, state.getValue(ANIM) % 3 + 1), 3);
        if (now % 6 == 0) level.playSound(null, pos, kind.sound, SoundSource.BLOCKS, 0.5f, 0.9f + random.nextFloat() * 0.3f);
        if (now % 8 == 0) level.sendParticles(kind.particle, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.02);
        level.scheduleTick(pos, this, 2);
    }

    /** Place le joueur sur la machine (debout sur le tapis, assis sur le vélo, allongé sur le banc) grâce à un siège invisible. */
    private void mount(ServerLevel sl, BlockPos pos, Direction f, ServerPlayer p, long end) {
        Vec3 at = world(pos, f, kind.dx, kind.dz, kind.y);
        float yaw = (kind.front ? f : f.getOpposite()).toYRot();
        GymSeat seat = new GymSeat(ModEntities.SEAT.get(), sl);
        seat.setup(pos, kind.ordinal(), p.getId(), end, f);
        seat.moveTo(at.x, at.y, at.z, yaw, 0);
        if (kind.seated) {
            p.connection.teleport(at.x, pos.getY(), at.z, yaw, 0);
            sl.addFreshEntity(seat);
            p.startRiding(seat, true);
        } else {
            p.connection.teleport(at.x, at.y, at.z, yaw, p.getXRot());
            p.setDeltaMovement(0, 0, 0);
            sl.addFreshEntity(seat);
        }
    }
}
