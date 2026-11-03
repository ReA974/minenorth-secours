package fr.minenorth.secours.pompier;

import fr.minenorth.secours.FireService;
import fr.minenorth.secours.compat.Mts;
import fr.minenorth.secours.config.SecoursConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Lance à incendie. Clic droit sur une bouche d'incendie (ou sur un véhicule MTS de pompiers) : la lance y est branchée.
 * Clic droit maintenu : jet d'eau qui noie les foyers, les braises et le feu vanilla sur son trajet et éteint les gens en feu,
 * tant que le pompier reste à moins de lance_longueur_tuyau blocs de son point d'eau.
 */
public class HoseItem extends Item {
    private static final String TAG = "branchement";

    public HoseItem() { super(new Properties().stacksTo(1)); }

    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    // ------------------------------------------------------------------ branchement

    /** Branche la lance sur une bouche d'incendie. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!ctx.getLevel().getBlockState(ctx.getClickedPos()).is(Pompier.HYDRANT.get())) return InteractionResult.PASS;
        if (ctx.getLevel() instanceof ServerLevel sl && ctx.getPlayer() instanceof ServerPlayer p) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", ctx.getClickedPos().asLong());
            connect(p, ctx.getItemInHand(), t, ctx.getClickedPos(), "la bouche d'incendie");
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    /** Branche la lance sur un véhicule MTS (appelé par PompierTools sur un clic sur le véhicule). */
    public static void connectVehicle(ServerPlayer p, ItemStack stack, Entity vehicle) {
        CompoundTag t = new CompoundTag();
        t.putUUID("vehicule", vehicle.getUUID());
        connect(p, stack, t, vehicle.blockPosition(), "le véhicule");
    }

    /** Rouleaux de tuyau portés : chacun ajoute lance_longueur_tuyau blocs (3 au plus). */
    private static int rolls(ServerPlayer p) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(Pompier.ROULEAU.get())) n += st.getCount();
        if (p.getOffhandItem().is(Pompier.ROULEAU.get())) n += p.getOffhandItem().getCount();
        return Math.min(3, n);
    }

    private static void connect(ServerPlayer p, ItemStack stack, CompoundTag t, BlockPos at, String what) {
        int n = rolls(p);
        if (n == 0) {
            p.displayClientMessage(Component.literal("§eIl vous faut au moins un rouleau de tuyau dans l'inventaire."), true);
            return;
        }
        int len = n * SecoursConfig.get().lance_longueur_tuyau;
        t.putString("dim", p.level().dimension().location().toString());
        t.putInt("longueur", len);
        stack.getOrCreateTag().put(TAG, t);
        p.level().playSound(null, at, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.8f, 1.4f);
        p.displayClientMessage(Component.literal("§bLance branchée sur " + what + " (" + n + " rouleau" + (n > 1 ? "x" : "") + " : " + len + " m de tuyau)."), true);
    }

    /** Branchement de cette lance, ou null si elle n'est pas branchée. */
    @Nullable
    public static CompoundTag connection(ItemStack stack) {
        if (!(stack.getItem() instanceof HoseItem) || stack.getTag() == null || !stack.getTag().contains(TAG)) return null;
        CompoundTag t = stack.getTag().getCompound(TAG);
        return t.isEmpty() ? null : t;
    }

    /** Longueur du tuyau branché (anciennes lances branchées avant les rouleaux : un rouleau). */
    public static int length(CompoundTag t) { return t.contains("longueur") ? t.getInt("longueur") : SecoursConfig.get().lance_longueur_tuyau; }

    /** Débranche la lance (message facultatif à l'action bar, bruit de raccord). */
    public static void disconnect(ServerPlayer p, ItemStack stack, @Nullable String why) {
        if (stack.getTag() == null) return;
        stack.getTag().remove(TAG);
        p.level().playSound(null, p.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.7f, 1.6f);
        if (why != null) p.displayClientMessage(Component.literal("§e" + why), true);
    }

    /** Point de branchement (centre de la bouche d'incendie ou position du véhicule), ou null s'il n'existe plus. */
    @Nullable
    public static Vec3 anchor(ServerLevel sl, CompoundTag t) {
        if (t.hasUUID("vehicule")) {
            Entity v = sl.getEntity(t.getUUID("vehicule"));
            return v == null || !v.isAlive() ? null : v.position();
        }
        BlockPos h = BlockPos.of(t.getLong("pos"));
        if (!sl.isLoaded(h) || !sl.getBlockState(h).is(Pompier.HYDRANT.get())) return null;
        return Vec3.atCenterOf(h);
    }

    /** Point d'eau encore valable à portée de tuyau ? Sinon renvoie la raison, à afficher au joueur. */
    @Nullable
    private static String supplyProblem(ServerPlayer p, ItemStack stack) {
        CompoundTag t = connection(stack);
        if (t == null) return "Branchez d'abord la lance : clic droit sur une bouche d'incendie ou sur le camion.";
        if (!t.getString("dim").equals(p.level().dimension().location().toString())) return "Le tuyau ne va pas jusqu'ici : rebranchez la lance.";
        Vec3 a = anchor(p.serverLevel(), t);
        if (a == null) return "Le point d'eau où la lance était branchée n'est plus là.";
        double max = length(t);
        if (a.distanceToSqr(p.position()) > max * max) return "Tuyau trop court : rapprochez-vous du point d'eau (" + (int) max + " m).";
        return null;
    }

    // ------------------------------------------------------------------ jet

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            if (!Pompier.isSecours(sp)) {
                sp.displayClientMessage(Component.literal("§cLa lance est réservée aux pompiers."), true);
                return InteractionResultHolder.fail(stack);
            }
            String why = supplyProblem(sp, stack);
            if (why != null) {
                sp.displayClientMessage(Component.literal("§e" + why), true);
                return InteractionResultHolder.fail(stack);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        double range = SecoursConfig.get().lance_portee;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        double len = hit.getLocation().distanceTo(eye);

        if (level.isClientSide) {   // jet visible chez tout le monde (le client fait tourner l'utilisation des autres joueurs aussi)
            for (double d = 0.8; d < len; d += 0.55) {
                double drop = 0.012 * d * d;   // l'eau retombe un peu en bout de jet
                Vec3 v = eye.add(look.scale(d)).add(0, -0.2 - drop, 0);
                level.addParticle(ParticleTypes.SPLASH, v.x, v.y, v.z, look.x * 0.4, look.y * 0.4 + 0.05, look.z * 0.4);
            }
            Vec3 end = hit.getLocation();
            for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.CLOUD, end.x, end.y + 0.2, end.z, (Math.random() - 0.5) * 0.1, 0.05, (Math.random() - 0.5) * 0.1);
            return;
        }
        if (!(user instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) return;
        if (used % 20 == 19) {
            String why = supplyProblem(p, stack);
            if (why != null) { p.displayClientMessage(Component.literal("§e" + why), true); p.stopUsingItem(); return; }
        }
        if (used % 10 == 0) sl.playSound(null, p.blockPosition(), SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 0.9f, 1.8f);
        if (used % 4 != 0) return;

        // Noie le premier feu rencontré le long du jet, puis autour du point d'impact.
        boolean done = false;
        for (double d = 1; d <= len + 0.5 && !done; d += 1) {
            BlockPos b = BlockPos.containing(eye.add(look.scale(Math.min(d, len))));
            done = FireService.douse(sl, b) || FireService.douse(sl, b.below());
        }
        if (!done) {
            BlockPos c = hit.getBlockPos().relative(hit.getDirection());
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) if (FireService.douse(sl, b)) break;
        }
        // Les gens en feu sur le trajet sont éteints et repoussés.
        AABB box = new AABB(eye, hit.getLocation()).inflate(0.7);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != p)) {
            if (e.getBoundingBox().inflate(0.6).clip(eye, hit.getLocation()).isEmpty()) continue;
            e.clearFire();
            e.push(look.x * 0.12, 0.02, look.z * 0.12);
            e.hurtMarked = true;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7Clic droit sur une bouche d'incendie ou le camion : brancher"));
        tip.add(Component.literal("§7(rouleau de tuyau nécessaire : " + SecoursConfig.get().lance_longueur_tuyau + " m par rouleau, 3 max.)"));
        tip.add(Component.literal("§7Clic droit maintenu : jet d'eau"));
        CompoundTag t = connection(stack);
        if (t != null) tip.add(Component.literal((t.hasUUID("vehicule") ? "§bBranchée sur un véhicule" : "§bBranchée sur une bouche d'incendie") + " (" + length(t) + " m)"));
        else tip.add(Component.literal("§8Non branchée"));
    }

    /** Pour PompierTools : un véhicule MTS convient-il (nom contenant un des mots de lance_vehicules, liste vide = tous) ? */
    public static boolean acceptsVehicle(Entity vehicle) {
        List<String> words = SecoursConfig.get().lance_vehicules;
        if (words.isEmpty()) return true;
        String name = Mts.vehicleName(vehicle);
        for (String w : words) if (!w.isBlank() && name.contains(w.toLowerCase(java.util.Locale.ROOT))) return true;
        return false;
    }
}
