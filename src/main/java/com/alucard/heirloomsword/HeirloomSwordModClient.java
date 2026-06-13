package com.alucard.heirloomsword;

import com.alucard.heirloomsword.ClientManaState;
import com.alucard.heirloomsword.ManaService;
import com.alucard.heirloomsword.client.SwordFamiliarGeoRenderer;
import net.minecraft.util.Mth;
import com.alucard.heirloomsword.network.SwordChargePacket;
import com.alucard.heirloomsword.network.SwordGuardPacket;
import com.alucard.heirloomsword.network.SwordLaunchPacket;
import com.alucard.heirloomsword.network.SwordModePacket;
import com.alucard.heirloomsword.network.SwordMomentumPacket;
import com.alucard.heirloomsword.network.SwordQuickFirePacket;
import com.alucard.heirloomsword.network.SwordRecallPacket;
import com.alucard.heirloomsword.network.SwordSweepPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

@Mod(value = HeirloomSwordMod.MODID, dist = Dist.CLIENT)
public class HeirloomSwordModClient {
    public HeirloomSwordModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }

    @EventBusSubscriber(modid = HeirloomSwordMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ModKeybinds.TOGGLE_MODE);
            event.register(ModKeybinds.RECALL);
            event.register(ModKeybinds.GUARD);
            event.register(ModKeybinds.QUICK_FIRE);
        }

                @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SWORD_FAMILIAR.get(), SwordFamiliarGeoRenderer::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            HeirloomSwordMod.LOGGER.info("Heirloom Sword client initialized");
        }
    }

    @EventBusSubscriber(modid = HeirloomSwordMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class ClientEvents {
        private static boolean isCharging = false;
        private static int clientChargeTimer = 0;

        private static boolean isSweeping = false;
        private static float lastYaw = 0;
        private static float lastPitch = 0;

        private static boolean isBlocking = false;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                if (isCharging) resetChargeState();
                if (isBlocking) {
                    if (mc.player != null) cancelBlocking(); else resetBlockState();
                }
                return;
            }

            LocalPlayer player = mc.player;
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) {
                if (isCharging) resetChargeState();
                if (isSweeping) resetSweepState();
                if (isBlocking) cancelBlocking();
                return;
            }

                        // Handle F key (toggle mode)
                        while (ModKeybinds.TOGGLE_MODE.consumeClick()) {
                            SwordMode toggledCurrent = HeirloomSwordItem.getMode(held);
                            if (toggledCurrent == SwordMode.NORMAL) {
                                if (player.isSwimming() || player.isFallFlying() || player.isPassenger()) {
                                    continue;
                                }
                            }
                
                            if (HeirloomSwordItem.isFlying(held)) {
                                SwordFamiliarEntity familiar = findClientFamiliar(player);
                                if (familiar != null
                                        && familiar.getState() != FamiliarState.HOVERING
                                        && familiar.getState() != FamiliarState.SWEEPING_HOLD
                                        && familiar.getState() != FamiliarState.BLOCKING) {
                                    continue; // F is locked during other active states
                                }
                            }
                            if (isSweeping) resetSweepState();
                            if (isBlocking) resetBlockState();
                            PacketDistributor.sendToServer(new SwordModePacket());
                            SwordMode next = toggledCurrent == SwordMode.NORMAL ? SwordMode.FLYING : SwordMode.NORMAL;
                            HeirloomSwordItem.setMode(held, next);
                        }

            // Handle R key (recall) — ignored during sweep states
            while (ModKeybinds.RECALL.consumeClick()) {
                if (!HeirloomSwordItem.isFlying(held)) continue;
                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar != null && (familiar.getState() == FamiliarState.SWEEPING_HOLD
                        || familiar.getState() == FamiliarState.SWEEPING_RELEASE)) continue;
                PacketDistributor.sendToServer(new SwordRecallPacket());
            }

            // Handle V key (quick fire at the locked-on target)
            while (ModKeybinds.QUICK_FIRE.consumeClick()) {
                if (!HeirloomSwordItem.isFlying(held)) continue;
                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar == null || familiar.getState() != FamiliarState.HOVERING) continue;
                if (familiar.getAwarenessTarget() == null) continue; // needs a lock-on
                PacketDistributor.sendToServer(new SwordQuickFirePacket());
            }

            // Track charge hold state
            if (isCharging) {
                if (!HeirloomSwordItem.isFlying(held)) {
                    resetChargeState();
                    return;
                }

                boolean attackHeld = mc.options.keyAttack.isDown();
                if (!attackHeld) {
                    Vec3 lookDir = player.getLookAngle();
                    boolean charged = clientChargeTimer >= 60;
                    PacketDistributor.sendToServer(new SwordLaunchPacket(lookDir, charged));
                    resetChargeState();
                } else {
                    clientChargeTimer++;
                }
            }

            // Track guard hold state (G key)
            if (!isBlocking) {
                if (ModKeybinds.GUARD.isDown() && HeirloomSwordItem.isFlying(held)) {
                    SwordFamiliarEntity familiar = findClientFamiliar(player);
                    if (familiar != null && familiar.getGuardCooldown() == 0) {
                        FamiliarState s = familiar.getState();
                        if (s == FamiliarState.HOVERING
                                || s == FamiliarState.CHARGING
                                || s == FamiliarState.SWEEPING_HOLD) {
                            if (ClientManaState.current < ManaService.MIN_BLOCK) {
                                playDeniedClient(player);
                            } else {
                                if (isCharging) resetChargeState();   // G cancels the charge — no launch packet
                                if (isSweeping) resetSweepState();    // G arrests the sweep — no release packet
                                PacketDistributor.sendToServer(new SwordGuardPacket(true));
                                isBlocking = true;
                            }
                        }
                    }
                }
            } else {
                if (!HeirloomSwordItem.isFlying(held) || !ModKeybinds.GUARD.isDown()) {
                    cancelBlocking();
                }
            }

                        // Handle sweep hold state
            if (isSweeping) {
                if (!HeirloomSwordItem.isFlying(held)) {
                    resetSweepState();
                    return;
                }

                boolean useHeld = mc.options.keyUse.isDown();
                if (!useHeld) {
                    // Right-click released — trigger SWEEPING_RELEASE on server
                    SwordFamiliarEntity familiar = findClientFamiliar(player);
                    if (familiar != null && familiar.getState() == FamiliarState.SWEEPING_HOLD) {
                        familiar.releaseSweep();
                    }
                    PacketDistributor.sendToServer(new SwordLaunchPacket(Vec3.ZERO, false));
                    resetSweepState();
                } else {
                    // Send per-tick momentum delta
                    float currentYaw = player.getYRot();
                    float currentPitch = player.getXRot();
                    float yawDelta = currentYaw - lastYaw;
                    float pitchDelta = currentPitch - lastPitch;

                    if (Math.abs(yawDelta) > 0.1f || Math.abs(pitchDelta) > 0.1f) {
                        PacketDistributor.sendToServer(new SwordMomentumPacket(yawDelta, pitchDelta));
                    }

                    lastYaw = currentYaw;
                    lastPitch = currentPitch;
                }
            }
            
                        // Telekinetic shimmer at the hand while flying mode is active
            if (HeirloomSwordItem.isFlying(held) && mc.level != null
                    && player.getRandom().nextFloat() < 0.15f) {
                double dx = (player.getRandom().nextDouble() - 0.5) * 0.2;
                double dy = (player.getRandom().nextDouble() - 0.5) * 0.2;
                double dz = (player.getRandom().nextDouble() - 0.5) * 0.2;

                Vec3 handPos;
                if (mc.options.getCameraType().isFirstPerson()) {
                    handPos = player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0, -0.3, 0);
                } else {
                    float yRot = player.yBodyRot * ((float) Math.PI / 180F);
                    double handOffsetZ = Math.cos(yRot) * 0.4;
                    double handOffsetX = Math.sin(yRot) * 0.4;
                    handPos = new Vec3(player.getX() - handOffsetX,
                            player.getY() + player.getBbHeight() * 0.5, player.getZ() + handOffsetZ);
                }

                mc.level.addParticle(ParticleTypes.WITCH, handPos.x + dx, handPos.y + dy, handPos.z + dz, 0, 0, 0);
            }
        }

        @SubscribeEvent
        public static void onMouseClick(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            LocalPlayer player = mc.player;
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            // Left click (attack) — begin charging or suppress during active states
            if (event.isAttack()) {
                if (isCharging || isSweeping) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                    return;
                }

                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar != null && familiar.getState() == FamiliarState.HOVERING) {
                    if (ClientManaState.current < ManaService.MIN_CHARGE) {
                        playDeniedClient(player);
                        event.setCanceled(true);
                        event.setSwingHand(false);
                        return;
                    }
                    PacketDistributor.sendToServer(new SwordChargePacket());
                    isCharging = true;
                    clientChargeTimer = 0;
                    event.setCanceled(true);
                    event.setSwingHand(false);
                } else {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
                return;
            }

            // Right click (use) — begin sweeping or suppress during active states
            if (event.isUseItem()) {
                if (isSweeping || isCharging) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                    return;
                }

                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar != null && familiar.getState() == FamiliarState.HOVERING) {
                    if (ClientManaState.current < ManaService.MIN_SWEEP) {
                        playDeniedClient(player);
                        event.setCanceled(true);
                        event.setSwingHand(false);
                        return;
                    }
                    PacketDistributor.sendToServer(new SwordSweepPacket());
                    isSweeping = true;
                    lastYaw = player.getYRot();
                    lastPitch = player.getXRot();
                    event.setCanceled(true);
                    event.setSwingHand(false);
                } else {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
            }
        }

        private static void playDeniedClient(LocalPlayer player) {
            // Mirror of SwordSounds.playDenied, played locally for client-predicted denials.
            player.playSound(net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, 0.5f, 1.2f);
        }

        private static void resetChargeState() {
            isCharging = false;
            clientChargeTimer = 0;
        }

        private static void resetSweepState() {
            isSweeping = false;
            lastYaw = 0;
            lastPitch = 0;
        }

        private static void resetBlockState() {
            isBlocking = false;
        }

        private static void cancelBlocking() {
            PacketDistributor.sendToServer(new SwordGuardPacket(false));
            isBlocking = false;
        }

                @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                event.setCanceled(true);
                com.alucard.heirloomsword.client.TelekinesisHandRenderer.render(
                        event, findClientFamiliar(mc.player));
            }
        }

        @SubscribeEvent
        public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            LocalPlayer player = mc.player;

            // Mana bar: shown whenever the sword is in hand (normal or flying) or the
            // familiar is present. Independent of the flying-only HUD below.
            boolean showMana = player.getMainHandItem().getItem() instanceof HeirloomSwordItem
                    || findClientFamiliar(player) != null;
            if (showMana) {
                renderManaBar(event.getGuiGraphics(),
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }

            int selectedSlot = player.getInventory().selected;
            ItemStack stack = player.getInventory().getItem(selectedSlot);

            if (!(stack.getItem() instanceof HeirloomSwordItem) || !HeirloomSwordItem.isFlying(stack)) {
                return;
            }

            GuiGraphics guiGraphics = event.getGuiGraphics();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int hotbarX = screenWidth / 2 - 91 + selectedSlot * 20;
            int hotbarY = screenHeight - 22;

            renderPurpleGlow(guiGraphics, hotbarX, hotbarY);

            // Render charge bar when charging (only after 1 second hold)
            if (isCharging && clientChargeTimer >= 20) {
                renderChargeBar(guiGraphics, screenWidth, screenHeight);
            }
        }

        private static void renderManaBar(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
            float ratio = Mth.clamp(ClientManaState.current / ManaService.MAX_MANA, 0f, 1f);

            int barWidth = 80;          // [TUNE] placement/size — inconspicuous, above the hotbar
            int barHeight = 4;
            int barX = screenWidth / 2 - barWidth / 2;
            int barY = screenHeight - 34;
            int fillWidth = (int) (barWidth * ratio);

            // Dark backing + a conventional mana-blue fill.
            guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x88000000);
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF3A7BD5);
        }

        private static void renderChargeBar(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
            int barWidth = 40;
            int barHeight = 3;
            int barX = screenWidth / 2 - barWidth / 2;
            int barY = screenHeight / 2 + 14;

            // Progress maps ticks 20-60 to 0.0-1.0
            float progress = Math.min(1.0f, (clientChargeTimer - 20) / 40.0f);
            int fillWidth = (int) (barWidth * progress);

            // Background
            guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xAA000000);

            // Fill — purple when charging, gold when fully charged
            int fillColor = progress >= 1.0f ? 0xFFFFD700 : 0xFF9933FF;
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
        }

        private static void renderPurpleGlow(GuiGraphics guiGraphics, int x, int y) {
            // Subtle 1px purple outline around the slot's item area
            guiGraphics.renderOutline(x + 2, y + 2, 18, 18, 0x669933FF);
        }

        @Nullable
        private static SwordFamiliarEntity findClientFamiliar(LocalPlayer player) {
            var entities = player.level().getEntitiesOfClass(SwordFamiliarEntity.class,
                    player.getBoundingBox().inflate(64));
            for (SwordFamiliarEntity familiar : entities) {
                if (familiar.getOwnerUUID().map(player.getUUID()::equals).orElse(false)) {
                    return familiar;
                }
            }
            return null;
        }
    }
}
