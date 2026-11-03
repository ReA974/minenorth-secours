package fr.minenorth.secours.pompier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Caméra thermique : tenue en main par un pompier, elle montre les foyers, les braises et les blessés inconscients à travers les murs. */
public class ThermalCameraItem extends Item {
    public ThermalCameraItem() { super(new Properties().stacksTo(1)); }

    public static boolean holding(Player p) {
        return p.getMainHandItem().is(Pompier.CAMERA.get()) || p.getOffhandItem().is(Pompier.CAMERA.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7En main : foyers, braises cachées et victimes"));
        tip.add(Component.literal("§7visibles à travers les murs (48 blocs)"));
    }
}
