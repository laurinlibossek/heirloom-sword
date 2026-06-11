package com.alucard.heirloomsword;

import com.alucard.heirloomsword.client.HeirloomSwordItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class HeirloomSwordItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public HeirloomSwordItem() {
        super(Tiers.NETHERITE, new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                // 7 + netherite bonus 4 + player base 1 = 12 attack damage.
                // -2.4f attack speed = 1.6 final, identical cooldown to a netherite sword.
                .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 7, -2.4f))
                // TieredItem force-applies netherite durability; UNBREAKABLE suppresses it
                // entirely (no damage taken, no bar). false = no "Unbreakable" tooltip line.
                .component(DataComponents.UNBREAKABLE, new Unbreakable(false))
                .component(ModDataComponents.SWORD_MODE.get(), SwordMode.NORMAL));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private HeirloomSwordItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = new HeirloomSwordItemRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        SwordFamiliarEntity.igniteIfUndead(target);
        return super.hurtEnemy(stack, target, attacker);
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
