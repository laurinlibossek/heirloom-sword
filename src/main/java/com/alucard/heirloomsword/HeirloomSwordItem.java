package com.alucard.heirloomsword;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;

public class HeirloomSwordItem extends Item {
    public HeirloomSwordItem() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .component(ModDataComponents.SWORD_MODE.get(), SwordMode.NORMAL));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    public static SwordMode getMode(ItemStack stack) {
        SwordMode mode = stack.get(ModDataComponents.SWORD_MODE.get());
        return mode != null ? mode : SwordMode.NORMAL;
    }

    public static void setMode(ItemStack stack, SwordMode mode) {
        stack.set(ModDataComponents.SWORD_MODE.get(), mode);
    }

    public static boolean isFlying(ItemStack stack) {
        return getMode(stack) == SwordMode.FLYING;
    }
}
