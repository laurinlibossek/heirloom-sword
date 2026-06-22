package com.alucard.heirloomsword;

import com.alucard.heirloomsword.client.HeirloomSwordItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.enchantment.Enchantment;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
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
                .attributes(buildAttributes())
                // TieredItem force-applies netherite durability; UNBREAKABLE suppresses it
                // entirely (no damage taken, no bar). false = no "Unbreakable" tooltip line.
                .component(DataComponents.UNBREAKABLE, new Unbreakable(false))
                .component(ModDataComponents.SWORD_MODE.get(), SwordMode.NORMAL));
    }

    // Held-mode melee reach: vanilla entity_interaction_range default is 3.0; +1.5 -> 4.5 so the
    // long greatsword can land hits at a believable distance. MAINHAND only.
    private static ItemAttributeModifiers buildAttributes() {
        return SwordItem.createAttributes(Tiers.NETHERITE, 7, -2.4f)
                .withModifierAdded(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "reach"),
                                1.5,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heirloomswordmod.lore1")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.heirloomswordmod.bloodlust")
                .withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, context, tooltip, flag);
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

    // The blood component decays every few ticks (see inventoryTick). Vanilla's default
    // re-equip check treats ANY stack difference as a swap, so each decay step replayed the
    // first-person "rise in hand" equip bob on a loop. Re-equip only on a real swap or an
    // actual mode change (FLYING<->NORMAL) — never for cosmetic blood decay.
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || oldStack.getItem() != newStack.getItem()) return true;
        return getMode(oldStack) != getMode(newStack);
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

    // Cosmetic blood decay [TUNE]: a fresh 1.0 fades to 0.0 over ~10s without further contact,
    // in BOTH normal and flying mode. Each qualifying flying-mode hit refreshes it to 1.0
    // (see SwordFamiliarEntity#bloodyOwnerBlade), so repeated contact keeps the blade bloodied.
    // Decays in steps to bound component-sync traffic (writes only every interval).
    public static final long BLOOD_DECAY_INTERVAL = 6L;   // ticks between decay steps
    public static final float BLOOD_DECAY_STEP = 0.03f;   // per step -> ~1.0 over ~200 ticks (10s)

    public static float getBlood(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BLOOD.get(), 0f);
    }

    public static void setBlood(ItemStack stack, float value) {
        stack.set(ModDataComponents.BLOOD.get(), Mth.clamp(value, 0f, 1f));
    }

    /** First Heirloom Sword stack in the player's inventory, or {@link ItemStack#EMPTY}. */
    public static ItemStack findInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof HeirloomSwordItem) return s;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide()) return;
        // Decays in both modes: while flying the familiar refreshes blood to 1.0 on each hit,
        // so this only wins out once contact stops for ~10s.
        if (level.getGameTime() % BLOOD_DECAY_INTERVAL != 0L) return;
        float blood = getBlood(stack);
        if (blood <= 0f) return;
        setBlood(stack, Math.max(0f, blood - BLOOD_DECAY_STEP));
    }
}
