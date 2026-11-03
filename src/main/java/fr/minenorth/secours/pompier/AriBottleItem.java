package fr.minenorth.secours.pompier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Bouteille d'air de l'ARI (dossard, bretelles, manomètre) : alimente le masque ARI. Modèle 3D : tools/GenAssets.bouteille. */
public class AriBottleItem extends Item {
    public AriBottleItem() { super(new Properties().stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7Alimente le masque ARI : sans elle, le masque ne protège pas de la fumée"));
    }
}
