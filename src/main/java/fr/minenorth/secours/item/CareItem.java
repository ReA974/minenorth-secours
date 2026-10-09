package fr.minenorth.secours.item;

import net.minecraft.world.item.Item;

/** Objet de soin du mod. Le soin lui-même est géré par SecoursService (clic droit sur un joueur). */
public class CareItem extends Item {
    public final int type;

    public CareItem(int type, int stack) {
        super(new Item.Properties().stacksTo(stack));
        this.type = type;
    }
}
