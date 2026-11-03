package fr.minenorth.secours.pompier;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Masque ARI (appareil respiratoire isolant) : se porte sur la tête (clic droit ou emplacement du casque). Protège de la fumée
 * des incendies. Le modèle 3D s'affiche sur le visage (affichage « head » du modèle d'item).
 */
public class AriMaskItem extends Item implements Equipable {
    public AriMaskItem() { super(new Properties().stacksTo(1)); }

    public static boolean wearing(Player p) { return p.getItemBySlot(EquipmentSlot.HEAD).is(Pompier.MASQUE.get()); }

    /** Bouteille d'air sur soi (inventaire) : le masque n'est alimenté qu'avec elle. */
    public static boolean hasBottle(Player p) { return p.getInventory().contains(new ItemStack(Pompier.BOUTEILLE.get())); }

    /** Protégé de la fumée : masque porté ET bouteille d'air. */
    public static boolean protects(Player p) { return wearing(p) && hasBottle(p); }

    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.HEAD; }

    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7Clic droit : mettre le masque"));
        tip.add(Component.literal("§7Protège de la fumée des incendies, avec la bouteille d'air ARI"));
    }
}
