package com.alucard.heirloomsword;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class SwordEventHandler {
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

        ServerLevel level = player.serverLevel();
        boolean familiarExists = false;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SwordFamiliarEntity familiar
                    && familiar.getOwnerUUID().map(player.getUUID()::equals).orElse(false)) {
                familiarExists = true;
                break;
            }
        }

        if (!familiarExists) {
            HeirloomSwordItem.setMode(swordStack, SwordMode.NORMAL);
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
