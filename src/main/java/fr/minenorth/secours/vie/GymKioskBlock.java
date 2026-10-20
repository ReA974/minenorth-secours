package fr.minenorth.secours.vie;

import fr.minenorth.api.MineNorth;
import fr.minenorth.api.PayResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Borne d'abonnement de la salle de sport. Clic droit : affiche le tarif et l'abonnement du joueur ; accroupi + clic droit : achète (virement du
 * compte du joueur vers le compte de l'entreprise de la salle, rien ne passe par le trésor). Le patron / les gérants (ou un opérateur) règlent la salle par un écran : accroupi + clic droit main vide.
 */
public class GymKioskBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 26, 13);

    public GymKioskBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 8.0f).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GymSiteBE(pos, state); }

    private static void say(ServerPlayer p, String t) { p.displayClientMessage(Component.literal(t), true); }

    private static String euros(int cents) { return String.format("%d,%02d €", cents / 100, cents % 100); }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer p) || !(level.getBlockEntity(pos) instanceof GymSiteBE be)) return InteractionResult.PASS;
        boolean manager = GymAdmin.canManage(p, be);
        // gérant / OP : accroupi + main vide = réglages ; accroupi avec un objet en main (ou borne pas encore liée) = achat
        if (p.isShiftKeyDown() && manager && (p.getMainHandItem().isEmpty() || be.company <= 0)) { GymAdmin.open(p, be); return InteractionResult.SUCCESS; }
        if (be.company <= 0) {
            say(p, "§7Borne non configurée : le patron de la salle doit la lier à son entreprise (accroupi + clic droit).");
            return InteractionResult.SUCCESS;
        }
        String name = MineNorth.companies().name(p.server, be.company);
        if (name.isBlank()) { say(p, "§cCette salle n'existe plus."); return InteractionResult.SUCCESS; }
        GymPass passes = GymPass.get(p.server);
        long end = passes.expiry(be.company, p.getUUID());
        String mine = end > System.currentTimeMillis() ? " §7(le tien court jusqu'au " + new SimpleDateFormat("dd/MM/yyyy").format(new Date(end)) + ")" : "";
        if (!p.isShiftKeyDown()) {
            say(p, "§e" + name + " : abonnement " + be.days + " jours, " + euros(be.priceCents) + ". §fAccroupi + clic droit pour acheter" + (manager ? " (un objet en main ; main vide = réglages)" : "") + "." + mine);
            return InteractionResult.SUCCESS;
        }
        UUID account = account(p, be.company);
        if (account == null) { say(p, "§cLa salle ne peut pas encaisser pour le moment."); return InteractionResult.SUCCESS; }
        if (be.priceCents > 0) {
            if (!MineNorth.bank().hasAccount(p.server, p.getUUID())) { say(p, "§cVous n'avez pas de compte bancaire."); return InteractionResult.SUCCESS; }
            PayResult r = MineNorth.bank().transfer(p.server, p.getUUID(), account, be.priceCents, "PAYMENT", "Abonnement salle : " + name, "Salle de sport");
            if (!r.ok()) { say(p, "§cPaiement refusé" + (r.message() == null || r.message().isBlank() ? "." : " : " + r.message())); return InteractionResult.SUCCESS; }
        }
        long until = passes.grant(be.company, p.getUUID(), be.days);
        say(p, "§aAbonnement « " + name + " » payé (" + euros(be.priceCents) + ") : valable jusqu'au " + new SimpleDateFormat("dd/MM/yyyy").format(new Date(until)) + ".");
        return InteractionResult.SUCCESS;
    }

    /** Compte bancaire de l'entreprise (mod Entreprises, par réflexion) ; null si indisponible. */
    @Nullable
    static UUID account(ServerPlayer p, int company) {
        try {
            Object o = Class.forName("fr.minenorth.entreprises.api.EntrepriseApi").getMethod("accountOf", net.minecraft.server.MinecraftServer.class, int.class).invoke(null, p.server, company);
            return o instanceof UUID u ? u : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
