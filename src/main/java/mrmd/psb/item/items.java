package mrmd.psb.item;

import mrmd.psb.MainClass;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 本模组物品统一注册入口（复用 {@link MainClass#ITEMS} 注册表，
 * 注册 id 由 {@link MainClass#MODID} 决定，即 speedgun:sbg）。
 */
public class items {

    /** 注册 id 为 "sbg" 的物品，完整注册 id 为 speedgun:sbg。 */
    public static final DeferredItem<Item> SBG = MainClass.ITEMS.register("sbg", () -> new Item(new Item.Properties()));
}
