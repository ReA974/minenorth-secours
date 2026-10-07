package fr.minenorth.secours.item;

import fr.minenorth.secours.SecoursService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Tablette des secours : clic droit = alertes et effectifs. Réservée aux pompiers et au SAMU (vérifié côté serveur). */
public class TabletItem extends Item {
    public TabletItem() { super(new Item.Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) SecoursService.open(sp);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
