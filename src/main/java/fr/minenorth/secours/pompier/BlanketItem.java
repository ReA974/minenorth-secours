package fr.minenorth.secours.pompier;

import fr.minenorth.secours.FireService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Couverture anti-feu : étouffe un petit départ de feu (jusqu'à 3 flammes sous la couverture) ou éteint une personne en feu.
 * Utilisable par tout le monde ; s'use à chaque emploi.
 */
public class BlanketItem extends Item {
    public BlanketItem() { super(new Properties().durability(12)); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos c = ctx.getLevel().getBlockState(ctx.getClickedPos()).is(net.minecraft.tags.BlockTags.FIRE) ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace());
        int n = 0;
        if (FireService.douse(sl, c)) n++;
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 0, 1))) {
            if (n >= 3) break;
            if (FireService.douse(sl, b)) n++;
        }
        if (n == 0) return InteractionResult.PASS;
        sl.playSound(null, c, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        if (ctx.getPlayer() != null) ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), pl -> pl.broadcastBreakEvent(ctx.getHand()));
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!target.isOnFire()) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            target.clearFire();
            player.level().playSound(null, target.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
            stack.hurtAndBreak(1, player, pl -> pl.broadcastBreakEvent(hand));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** Clic droit dans le vide alors qu'on brûle soi-même : on s'enroule dedans. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isOnFire()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            player.clearFire();
            level.playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
            stack.hurtAndBreak(1, player, pl -> pl.broadcastBreakEvent(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7Clic droit sur des flammes : les étouffer (3 max.)"));
        tip.add(Component.literal("§7Clic droit sur une personne en feu (ou sur soi) : l'éteindre"));
    }
}
