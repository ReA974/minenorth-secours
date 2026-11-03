package fr.minenorth.secours.pompier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Hache de pompier et halligan (pied-de-biche) : forcer une porte, une trappe ou un portillon fermé pendant une intervention.
 * Rien n'est cassé : la porte est ouverte au bout de quelques secondes d'effort (voir PompierTools).
 */
public class ForceToolItem extends Item {
    /** Durée du forcement, en ticks. */
    public final int forceTicks;

    public ForceToolItem(int forceTicks, int durability) {
        super(new Properties().durability(durability));
        this.forceTicks = forceTicks;
    }

    /** Porte que la main ne peut pas ouvrir (fer) ou porte non enregistrée : la porte enregistrée dans minenorth-portes passe par son hook. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!PompierTools.forceable(ctx.getLevel().getBlockState(ctx.getClickedPos()))) return InteractionResult.PASS;
        if (ctx.getPlayer() instanceof ServerPlayer p) PompierTools.tryForce(p, ctx.getClickedPos());
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7Clic droit sur une porte fermée : la forcer (" + forceTicks / 20.0 + " s)"));
        tip.add(Component.literal("§8Pompiers en service, près d'un incendie ou d'un blessé inconscient"));
    }
}
