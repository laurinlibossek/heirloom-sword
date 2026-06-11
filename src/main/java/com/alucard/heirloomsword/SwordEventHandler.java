package com.alucard.heirloomsword;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(player.serverLevel(), player.getUUID());
        if (familiar == null || familiar.getState() != FamiliarState.BLOCKING) return;

        DamageSource source = event.getSource();
        // Shield-equivalent: frontal physical damage only — no explosions, magic, or AoE.
        if (source.is(DamageTypeTags.BYPASSES_SHIELD)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        Vec3 sourcePos = source.getSourcePosition();
        if (sourcePos == null) return;

        // Frontal cone test, mirroring vanilla Player#isDamageSourceBlocked
        Vec3 toPlayer = sourcePos.vectorTo(player.position());
        toPlayer = new Vec3(toPlayer.x, 0.0, toPlayer.z).normalize();
        Vec3 look = player.getViewVector(1.0f);
        look = new Vec3(look.x, 0.0, look.z).normalize();
        if (toPlayer.dot(look) >= 0.0) return; // attack came from the side/behind

        event.setCanceled(true);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.9f);
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof ServerPlayer player)) return;

        Projectile projectile = event.getProjectile();
        // Physical projectiles only (spec): arrows + tridents (AbstractArrow), fireballs.
        // Magic projectiles, explosions, and area effects are not intercepted.
        boolean physical = projectile instanceof AbstractArrow || projectile instanceof Fireball;
        if (!physical) return;

        SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(player.serverLevel(), player.getUUID());
        if (familiar == null || familiar.getState() != FamiliarState.BLOCKING) return;

        // Same frontal cone as melee blocking
        Vec3 toPlayer = projectile.position().vectorTo(player.position());
        toPlayer = new Vec3(toPlayer.x, 0.0, toPlayer.z).normalize();
        Vec3 look = player.getViewVector(1.0f);
        look = new Vec3(look.x, 0.0, look.z).normalize();
        if (toPlayer.dot(look) >= 0.0) return;

        // Geometric deflection off the blade plane (normal = player look) at reduced speed.
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 normal = player.getLookAngle();
        Vec3 reflected = velocity.subtract(normal.scale(2.0 * velocity.dot(normal))).scale(0.4); // [TUNE] speed factor

        event.setCanceled(true);
        projectile.setDeltaMovement(reflected);
        projectile.hurtMarked = true; // force velocity sync to clients
        if (reflected.lengthSqr() > 1.0e-4) {
            // Nudge out of the player's hitbox so it can't re-collide next tick
            projectile.setPos(projectile.position().add(reflected.normalize().scale(0.5)));
        }
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setOwner(player); // a deflected arrow belongs to the blocker now
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.3f);
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
