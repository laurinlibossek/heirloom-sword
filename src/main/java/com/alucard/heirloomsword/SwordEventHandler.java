package com.alucard.heirloomsword;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class SwordEventHandler {
    private static final ResourceLocation ANCIENT_CITY_LOOT =
            ResourceLocation.withDefaultNamespace("chests/ancient_city");

    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        if (!event.getName().equals(ANCIENT_CITY_LOOT)) return;

        event.getTable().addPool(LootPool.lootPool()
                .add(LootItem.lootTableItem(HeirloomSwordMod.HEIRLOOM_SWORD.get())
                        .when(LootItemRandomChanceCondition.randomChance(0.05f)))
                .build());
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
            event.setCanceled(true);
            Player player = event.getPlayer();
            player.getInventory().add(stack.copy());
            player.displayClientMessage(
                    Component.translatable("msg.heirloomswordmod.no_drop"), true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack swordStack = findFlyingSword(player);
        if (swordStack == null) return;

        java.util.UUID familiarUUID = swordStack.get(ModDataComponents.FAMILIAR_UUID.get());
        if (familiarUUID == null) {
            HeirloomSwordItem.setMode(swordStack, SwordMode.NORMAL);
            player.displayClientMessage(
                    Component.translatable("msg.heirloomswordmod.sword_returns"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(familiarUUID);
        if (!(entity instanceof SwordFamiliarEntity) || entity.isRemoved()) {
            HeirloomSwordItem.setMode(swordStack, SwordMode.NORMAL);
            swordStack.remove(ModDataComponents.FAMILIAR_UUID.get());
            player.displayClientMessage(
                    Component.translatable("msg.heirloomswordmod.sword_returns"), true);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack swordStack = findFlyingSword(player);
        if (swordStack != null) {
            HeirloomSwordItem.setMode(swordStack, SwordMode.NORMAL);
            swordStack.remove(ModDataComponents.FAMILIAR_UUID.get());
        }

        SwordFamiliarEntity.despawnForOwner(player.serverLevel(), player.getUUID());
    }

    private ItemStack findFlyingSword(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                return stack;
            }
        }
        return null;
    }
}
