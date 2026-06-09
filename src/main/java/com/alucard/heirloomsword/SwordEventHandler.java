package com.alucard.heirloomsword;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

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
}
