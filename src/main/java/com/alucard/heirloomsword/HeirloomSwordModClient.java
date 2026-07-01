package com.alucard.heirloomsword;

import com.alucard.heirloomsword.client.SwordFamiliarGeoRenderer;
import com.alucard.heirloomsword.client.SweepHoldSoundInstance;
import net.minecraft.util.Mth;
import com.alucard.heirloomsword.network.SwordCancelChargePacket;
import com.alucard.heirloomsword.network.SwordChargePacket;
import com.alucard.heirloomsword.network.SwordGuardPacket;
import com.alucard.heirloomsword.network.SwordLaunchPacket;
import com.alucard.heirloomsword.network.SwordModePacket;
import com.alucard.heirloomsword.network.SwordMomentumPacket;
import com.alucard.heirloomsword.network.SwordQuickFirePacket;
import com.alucard.heirloomsword.network.SwordRecallPacket;
import com.alucard.heirloomsword.network.SwordWarpPacket;
import com.alucard.heirloomsword.network.SwordSweepPacket;
import com.alucard.heirloomsword.network.SwordTetherPacket;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

@Mod(value = HeirloomSwordMod.MODID, dist = Dist.CLIENT)
public class HeirloomSwordModClient {
    public HeirloomSwordModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);   
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

        // A registered layer renders exactly once per frame. The previous RenderGuiLayerEvent.Post
        // handler had no layer filter, so it ran after EVERY vanilla layer (~15-20x/frame),
        // compounding every translucent fill toward opaque.
        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_hud"),
                    ClientEvents::renderSwordHud);
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
        private static boolean chargeConfirmed = false; // server-confirmed CHARGING seen at least once

        private static boolean isSweeping = false;
        private static float lastYaw = 0;
        private static float lastPitch = 0;
        private static boolean sweepConfirmed = false; // server-confirmed SWEEPING_HOLD seen at least once
        private static int sweepTicks = 0;
        private static SweepHoldSoundInstance sweepHoldSound = null;

        private static boolean isBlocking = false;

        // Tether: hold left-click for 300 ms (6 ticks) to yank to the sword midpoint
        // from STUCK.
        // Rising-edge resets the counter so only a fresh hold counts.
        private static boolean wasAttacking = false;
        private static int attackHoldTicks = 0;
        private static final int TETHER_HOLD_TICKS = 6; // 300 ms at 20 TPS [TUNE]

        // Hand shimmer is a rare ambient cue: at most one particle per interval while
        // flying.
        private static final int HAND_PARTICLE_INTERVAL = 300; // 15 s at 20 tps
        private static int handParticleCooldown = 0;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                if (isCharging) {
                    if (mc.player != null)
                        cancelCharging();
                    else
                        resetChargeState();
                }
                if (isBlocking) {
                    if (mc.player != null)
                        cancelBlocking();
                    else
                        resetBlockState();
                }
                return;
            }

            LocalPlayer player = mc.player;
            ItemStack held = player.getMainHandItem();

            // Resolve the owner's familiar ONCE per tick. It was previously looked up 6-8 times per
            // tick (each a 128-block entity query); the reference is stable within a tick, so every
            // branch below reuses this.
            SwordFamiliarEntity selfFamiliar = findClientFamiliar(player);

            // V quick-fire is drained here — above the held-item gate — so the familiar can
            // be
            // loosed even when the sword isn't the selected item (e.g. a quick defense
            // while
            // eating), and so presses never queue up to fire the instant the sword is
            // reselected.
            // The familiar only exists in flying mode, so its presence is the flying-mode
            // signal;
            // normal-mode warp still requires the sword in hand.
            while (ModKeybinds.QUICK_FIRE.consumeClick()) {
                SwordFamiliarEntity familiar = selfFamiliar;
                if (familiar != null) {
                    if (!isManaExempt(player) && ClientManaState.lockoutTicks > 0) {
                        playDeniedClient(player);
                        continue;
                    }
                    if (familiar.getState() != FamiliarState.HOVERING)
                        continue;
                    if (familiar.getAwarenessTarget() == null)
                        continue; // needs a lock-on
                    PacketDistributor.sendToServer(new SwordQuickFirePacket());
                } else if (held.getItem() instanceof HeirloomSwordItem && !HeirloomSwordItem.isFlying(held)) {
                    // Normal mode: server validates target / mana / cooldown and gives feedback.
                    PacketDistributor.sendToServer(new SwordWarpPacket());
                }
            }

            // Mode-switch cooldown ticks down regardless of which item is held — the player
            // should not need to keep the sword selected for the cooldown to expire.
            if (ClientManaState.modeSwitchCooldownTicks > 0) {
                ClientManaState.modeSwitchCooldownTicks--;
                if (ClientManaState.modeSwitchCooldownTicks == 0) {
                    playModeReadyClient(player); // soft "ready to summon again" chime
                }
            }

            // Guard (G) runs above the held-item gate, mirroring V quick-fire: the familiar only
            // exists in flying mode, so its presence is the flying-mode signal and the guard can be
            // raised even when the sword isn't the selected hotbar slot (e.g. while eating). It
            // persists while G is held and ends when G is released or the familiar is gone.
            SwordFamiliarEntity guardFamiliar = selfFamiliar;
            if (!isBlocking) {
                if (ModKeybinds.GUARD.isDown() && guardFamiliar != null && guardFamiliar.getGuardCooldown() == 0) {
                    FamiliarState gs = guardFamiliar.getState();
                    if (gs == FamiliarState.HOVERING
                            || gs == FamiliarState.CHARGING
                            || gs == FamiliarState.SWEEPING_HOLD) {
                        if (!isManaExempt(player) && ClientManaState.current < ManaService.minBlock()) {
                            playDeniedClient(player);
                        } else {
                            if (isCharging)
                                resetChargeState(); // G cancels the charge — no launch packet
                            if (isSweeping)
                                resetSweepState(); // G arrests the sweep — no release packet
                            PacketDistributor.sendToServer(new SwordGuardPacket(true));
                            isBlocking = true;
                        }
                    }
                }
            } else if (!ModKeybinds.GUARD.isDown() || guardFamiliar == null) {
                cancelBlocking();
            }

            if (!(held.getItem() instanceof HeirloomSwordItem)) {
                if (isCharging)
                    resetChargeState();
                if (isSweeping)
                    resetSweepState();
                return;
            }

            // Count down the depletion lockout locally (mirrors the server; re-synced on
            // its
            // final tick). While > 0, every sword input below except the F mode toggle is
            // blocked.
            if (ClientManaState.lockoutTicks > 0) {
                ClientManaState.lockoutTicks--;
            }

            // Handle F key (toggle mode)
            while (ModKeybinds.TOGGLE_MODE.consumeClick()) {
                if (player.isSpectator()) continue;
                
                SwordMode toggledCurrent = HeirloomSwordItem.getMode(held);
                if (toggledCurrent == SwordMode.NORMAL) {
                    // Re-entry cooldown (set on the last exit). Gates entering flying mode only;
                    // mirrors the server gate so we don't optimistically predict a FLYING the
                    // server will reject. Exiting (the FLYING branch below) is never gated.
                    if (ClientManaState.modeSwitchCooldownTicks > 0) {
                        playModeCooldownDenied(player);
                        continue;
                    }
                    if (player.isSwimming() || player.isFallFlying() || player.isPassenger()) {
                        continue;
                    }
                    boolean alreadyFlying = false;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                            alreadyFlying = true;
                            break;
                        }
                    }
                    if (alreadyFlying || selfFamiliar != null) {
                        continue;
                    }
                }

                if (HeirloomSwordItem.isFlying(held)) {
                    SwordFamiliarEntity familiar = selfFamiliar;
                    if (familiar != null
                            && familiar.getState() != FamiliarState.HOVERING
                            && familiar.getState() != FamiliarState.SWEEPING_HOLD
                            && familiar.getState() != FamiliarState.BLOCKING) {
                        continue; // F is locked during other active states
                    }
                }
                if (isSweeping)
                    resetSweepState();
                if (isBlocking)
                    resetBlockState();
                PacketDistributor.sendToServer(new SwordModePacket());
                SwordMode next = toggledCurrent == SwordMode.NORMAL ? SwordMode.FLYING : SwordMode.NORMAL;
                HeirloomSwordItem.setMode(held, next);
                if (next == SwordMode.NORMAL) {
                    // Predict the re-entry cooldown the server arms on exit, plus a small grace so
                    // network skew (the server arms ~ping later than this) can never leave the
                    // client *less* strict — i.e. we never predict a FLYING the server will reject,
                    // which would stick. The ready chime fires a hair after the server is truly free.
                    ClientManaState.modeSwitchCooldownTicks =
                            SwordModePacket.MODE_SWITCH_COOLDOWN_TICKS + MODE_COOLDOWN_PREDICT_GRACE;
                }
            }

            // Handle R key (recall) — ignored during sweep states
            while (ModKeybinds.RECALL.consumeClick()) {
                if (!HeirloomSwordItem.isFlying(held))
                    continue;
                if (!isManaExempt(player) && ClientManaState.lockoutTicks > 0) {
                    playDeniedClient(player);
                    continue;
                }
                SwordFamiliarEntity familiar = selfFamiliar;
                if (familiar != null && (familiar.getState() == FamiliarState.SWEEPING_HOLD
                        || familiar.getState() == FamiliarState.SWEEPING_RELEASE))
                    continue;
                // Predict the recall mana gate for states the server actually recalls from.
                if (familiar != null
                        && (familiar.getState() == FamiliarState.LAUNCHING
                                || familiar.getState() == FamiliarState.STUCK)
                        && !isManaExempt(player)
                        && ClientManaState.current < ManaService.recallCost()) {
                    playDeniedClient(player);
                    continue;
                }
                PacketDistributor.sendToServer(new SwordRecallPacket());
            }

            // Tether: hold left-click for TETHER_HOLD_TICKS while in flying mode to yank to
            // the
            // sword midpoint from STUCK. Rising-edge resets the counter so only a fresh
            // hold counts.
            // Gate is at threshold — counting happens regardless of state, packet only
            // fires if STUCK.
            boolean attackingNow = mc.options.keyAttack.isDown();
            if (!attackingNow) {
                attackHoldTicks = 0;
            } else if (!wasAttacking) {
                attackHoldTicks = 0; // fresh press — restart count
            } else if (HeirloomSwordItem.isFlying(held)
                    && (isManaExempt(player) || ClientManaState.lockoutTicks <= 0)) {
                attackHoldTicks++;
                if (attackHoldTicks == TETHER_HOLD_TICKS) {
                    SwordFamiliarEntity tetherFamiliar = selfFamiliar;
                    if (tetherFamiliar != null && tetherFamiliar.getState() == FamiliarState.STUCK) {
                        if (!isManaExempt(player) && ClientManaState.current < ManaService.tetherCost()) {
                            playDeniedClient(player);
                        } else {
                            PacketDistributor.sendToServer(new SwordTetherPacket());
                        }
                    }
                }
            }
            wasAttacking = attackingNow;

            // Track charge hold state
            if (isCharging) {
                if (!HeirloomSwordItem.isFlying(held)) {
                    resetChargeState();
                    return;
                }

                // Detect if the server dropped the charge (e.g. mana exhaustion). Only act
                // once we've actually seen the server enter CHARGING — otherwise the network
                // round-trip lag right after the click would reset before charging begins.
                SwordFamiliarEntity chargeFamiliar = selfFamiliar;
                if (chargeFamiliar != null) {
                    if (chargeFamiliar.getState() == FamiliarState.CHARGING) {
                        chargeConfirmed = true;
                    } else if (chargeConfirmed) {
                        resetChargeState();
                        return;
                    }
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

            // Handle sweep hold state
            if (isSweeping) {
                if (!HeirloomSwordItem.isFlying(held)) {
                    resetSweepState();
                    return;
                }

                // Detect if the server ended the sweep (e.g. mana exhaustion) to avoid
                // sending a stale SwordLaunchPacket(Vec3.ZERO) that would corrupt server state.
                // Only act once we've seen the server confirm SWEEPING_HOLD (network lag).
                SwordFamiliarEntity sweepFamiliar = selfFamiliar;
                if (sweepFamiliar != null) {
                    if (sweepFamiliar.getState() == FamiliarState.SWEEPING_HOLD) {
                        sweepConfirmed = true;
                        sweepTicks++;
                        if (sweepTicks == 3 && sweepHoldSound == null) {
                            sweepHoldSound = new SweepHoldSoundInstance(sweepFamiliar);
                            mc.getSoundManager().play(sweepHoldSound);
                        }
                    } else if (sweepConfirmed) {
                        resetSweepState();
                        return;
                    }
                }

                boolean useHeld = mc.options.keyUse.isDown();
                if (!useHeld) {
                    // Right-click released — trigger SWEEPING_RELEASE on server
                    SwordFamiliarEntity familiar = selfFamiliar;
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

            // === Telekinetic hand shimmer (state-driven) ===
            if (HeirloomSwordItem.isFlying(held) && mc.level != null) {
                SwordFamiliarEntity shimmerFamiliar = selfFamiliar;
                FamiliarState shimmerState = shimmerFamiliar != null ? shimmerFamiliar.getState() : null;
                Vec3 handPos = telekineticHandPos(player);
                var rng = player.getRandom();

                if (shimmerState == FamiliarState.BLOCKING) {
                    // Violet dome: hemisphere of witch particles biased forward along the look vector.
                    // Heavily reduced density as requested.
                    if (rng.nextFloat() < 0.15f) { // 15% chance per tick (approx. 3 particles per second)
                        Vec3 look = player.getLookAngle();
                        Vec3 base = handPos.add(look.scale(0.4));
                        double a = rng.nextDouble() * Math.PI * 2;
                        double el = rng.nextDouble() * Math.PI * 0.5;
                        double rr = 0.6;
                        double lx = Math.cos(a) * Math.cos(el) * rr;
                        double ly = Math.sin(el) * rr;
                        double lz = Math.sin(a) * Math.cos(el) * rr;
                        mc.level.addParticle(ParticleTypes.WITCH, base.x + lx, base.y + ly, base.z + lz, 0, 0, 0);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onMouseClick(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null)
                return;

            LocalPlayer player = mc.player;
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem))
                return;
            if (!HeirloomSwordItem.isFlying(held))
                return;

            // Left click (attack) — begin charging or suppress during active states
            if (event.isAttack()) {
                if (isCharging || isSweeping) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                    return;
                }

                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar != null && familiar.getState() == FamiliarState.HOVERING) {
                    if (!isManaExempt(player) && ClientManaState.current < ManaService.minCharge()) {
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
                    if (!isManaExempt(player) && ClientManaState.current < ManaService.minSweep()) {
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
            player.playSound(ModSounds.SWORD_MODE_EXIT.value(), 0.35f, 1.0f);
        }

        // Grace ticks added to the client's predicted re-entry cooldown so it expires no earlier
        // than the server's (which arms ~ping later). Covers ping up to this many ticks (250ms).
        private static final int MODE_COOLDOWN_PREDICT_GRACE = 5;

        /** Very quiet "still on cooldown" tick when re-summon is blocked — softer than playDeniedClient. */
        private static void playModeCooldownDenied(LocalPlayer player) {
            player.playSound(ModSounds.SWORD_MODE_EXIT.value(), 0.10f, 1.0f);
        }

        /** Soft XP-pickup chime when the re-entry cooldown clears: the sword can be summoned again. */
        private static void playModeReadyClient(LocalPlayer player) {
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.08f, 1.2f);
        }

        /**
         * Creative/spectator players have infinite mana — client prediction mirrors the
         * server exemption.
         */
        private static boolean isManaExempt(LocalPlayer player) {
            return player.getAbilities().instabuild || player.isSpectator();
        }

        private static void resetChargeState() {
            isCharging = false;
            clientChargeTimer = 0;
            chargeConfirmed = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(ModSounds.SWORD_CHARGING.value().getLocation(), net.minecraft.sounds.SoundSource.PLAYERS);
            }
        }

        private static void resetSweepState() {
            isSweeping = false;
            lastYaw = 0;
            lastPitch = 0;
            sweepConfirmed = false;
            sweepTicks = 0;
            if (sweepHoldSound != null) {
                sweepHoldSound.stopPlaying();
                sweepHoldSound = null;
            }
        }

        private static void resetBlockState() {
            isBlocking = false;
        }

        private static void cancelBlocking() {
            PacketDistributor.sendToServer(new SwordGuardPacket(false));
            isBlocking = false;
        }

        private static void cancelCharging() {
            // Abort the charge server-side (no launch) so it can't get stuck in CHARGING
            // limbo
            // when the player opens a screen / pauses mid-charge.
            PacketDistributor.sendToServer(new SwordCancelChargePacket());
            resetChargeState();
        }

        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND)
                return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null)
                return;
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                event.setCanceled(true);
                com.alucard.heirloomsword.client.TelekinesisHandRenderer.render(
                        event, findClientFamiliar(mc.player));
            }
        }

        /** Custom GUI layer body (registered above the hotbar in {@code onRegisterGuiLayers}). */
        static void renderSwordHud(GuiGraphics layerGraphics, DeltaTracker deltaTracker) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null)
                return;

            // Respect the F1 "hide GUI" toggle — suppress the mana bar, hotbar glow,
            // and charge bar just like vanilla hides its own HUD. (The layer system already
            // skips hidden GUIs; this guard keeps the behavior explicit and future-proof.)
            if (mc.options.hideGui)
                return;

            LocalPlayer player = mc.player;
            SwordFamiliarEntity familiar = findClientFamiliar(player); // resolved once for this render

            // Mana bar: shown whenever the sword is in hand (normal or flying) or the
            // familiar is present. Hidden in creative — mana is infinite, so it conveys
            // nothing.
            boolean showMana = !isManaExempt(player)
                    && (player.getMainHandItem().getItem() instanceof HeirloomSwordItem
                            || familiar != null);
            if (showMana) {
                renderManaBar(layerGraphics,
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), player);
            }

            int selectedSlot = player.getInventory().selected;
            ItemStack stack = player.getInventory().getItem(selectedSlot);

            if (!(stack.getItem() instanceof HeirloomSwordItem)) {
                return;
            }

            boolean isFlying = HeirloomSwordItem.isFlying(stack);
            boolean isCooldown = !isFlying && ClientManaState.modeSwitchCooldownTicks > 0;

            if (!isFlying && !isCooldown) {
                return;
            }

            GuiGraphics guiGraphics = layerGraphics;
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int hotbarX = screenWidth / 2 - 91 + selectedSlot * 20;
            int hotbarY = screenHeight - 22;

            if (!player.isSpectator()) {
                if (isFlying) {
                    boolean stuck = familiar != null && familiar.getState() == FamiliarState.STUCK;
                    renderPurpleGlow(guiGraphics, hotbarX, hotbarY, stuck);
                } else {
                    renderCooldownGlow(guiGraphics, hotbarX, hotbarY);
                }
            }

            // Render charge bar when charging (only after 1 second hold)
            if (isCharging && clientChargeTimer >= 20) {
                renderChargeBar(guiGraphics, screenWidth, screenHeight);
            }
        }

        private static void renderManaBar(GuiGraphics guiGraphics, int screenWidth, int screenHeight, LocalPlayer player) {
            float ratio = Mth.clamp(ClientManaState.current / ManaService.maxMana(), 0f, 1f);

            int barWidth = 81; // [TUNE] matches hunger-bar span (10 icons × 9px, right-aligned)
            int barHeight = 4;
            int barX = screenWidth / 2 + 10; // left edge of hunger bar
            int barY = screenHeight - 45; // moved 2px down from -47
            // Shift up when air bubbles are visible so they don't overlap.
            if (player.getAirSupply() < player.getMaxAirSupply()) {
                barY -= 10; // offset adjusts to keep it at -55 when air bubbles are shown
            }
            int fillWidth = (int) (barWidth * ratio);

            boolean isLockedOut = ClientManaState.lockoutTicks > 0;

            // 1. Draw Casing (Styled rounded border)
            int borderCol;
            if (isLockedOut) {
                // Pulse border color between dark crimson and dark slate to represent lockout
                float pulse = (float) Math.sin(player.tickCount * 0.2f) * 0.5f + 0.5f;
                int r = (int) (0x2C + pulse * (0x6F - 0x2C));
                int g = (int) (0x1F - pulse * (0x10 - 0x05));
                int b = (int) (0x2C - pulse * (0x10 - 0x05));
                borderCol = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else if (ratio >= 1.0f) {
                // Fully charged glow (glowing celestial teal-blue border)
                float pulse = (float) Math.sin(player.tickCount * 0.15f) * 0.5f + 0.5f;
                int r = (int) (0x1F + pulse * 0x1A);
                int g = (int) (0x55 + pulse * 0x2C);
                int b = (int) (0x9F + pulse * 0x36);
                borderCol = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else {
                // Sleek dark metallic/navy casing
                borderCol = 0xFF1B2030;
            }

            // Draw beveled outer frame (omitting extreme corner pixels for beautiful rounded edges)
            guiGraphics.fill(barX, barY - 1, barX + barWidth, barY, borderCol); // Top
            guiGraphics.fill(barX, barY + barHeight, barX + barWidth, barY + barHeight + 1, borderCol); // Bottom
            guiGraphics.fill(barX - 1, barY, barX, barY + barHeight, borderCol); // Left
            guiGraphics.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barHeight, borderCol); // Right

            // Soft-blend corner pixels to round the rectangle's sharp vertices.
            // Alpha compensated for the single-pass layer: was 0x77, tuned while the old
            // event handler drew ~15x/frame (compounding to ~opaque). [TUNE in-game]
            int cornerCol = (borderCol & 0x00FFFFFF) | 0xF5000000;
            guiGraphics.fill(barX - 1, barY - 1, barX, barY, cornerCol); // Top-left
            guiGraphics.fill(barX + barWidth, barY - 1, barX + barWidth + 1, barY, cornerCol); // Top-right
            guiGraphics.fill(barX - 1, barY + barHeight, barX, barY + barHeight + 1, cornerCol); // Bottom-left
            guiGraphics.fill(barX + barWidth, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, cornerCol); // Bottom-right

            // 2. Draw Interior Track
            int trackCol = isLockedOut ? 0xFF0D0C12 : 0xFF070911;
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, trackCol);

            // 3. Draw Liquid Fill (Glowing 3D Cylindrical Gradient)
            if (fillWidth > 0 && !isLockedOut) {
                // Row 0 (top): Lightest cyan highlight (liquid surface sheen)
                guiGraphics.fill(barX, barY, barX + fillWidth, barY + 1, 0xFF6BF0FF);
                // Row 1: Magical cyan-blue
                guiGraphics.fill(barX, barY + 1, barX + fillWidth, barY + 2, 0xFF358CFC);
                // Row 2: Deep rich blue
                guiGraphics.fill(barX, barY + 2, barX + fillWidth, barY + 3, 0xFF1C4BC4);
                // Row 3 (bottom): Dark navy shadow
                guiGraphics.fill(barX, barY + 3, barX + fillWidth, barY + 4, 0xFF0B1B66);

                // Glistening leading edge (bright white-cyan vertical sparkle at the rightmost tip)
                if (fillWidth > 1) {
                    float pulse = (float) Math.sin(player.tickCount * 0.3f) * 0.5f + 0.5f;
                    int r = (int) (0xCF + pulse * 0x30);
                    int g = (int) (0xFA + pulse * 0x05);
                    int b = (int) (0xFF);
                    int leadCol = 0xFF000000 | (r << 16) | (g << 8) | b;
                    guiGraphics.fill(barX + fillWidth - 1, barY, barX + fillWidth, barY + barHeight, leadCol);
                }
            } else if (isLockedOut) {
                // Lockout phase: Draw a faint, desaturated pulsing crimson core to represent broken/overheated connection
                float pulse = (float) Math.sin(player.tickCount * 0.15f) * 0.5f + 0.5f;
                int r = (int) (0x1F + pulse * 0x1A);
                int g = (int) (0x07 + pulse * 0x05);
                int b = (int) (0x0C + pulse * 0x0A);
                int pulseCol = 0xFF000000 | (r << 16) | (g << 8) | b;
                guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, pulseCol);
            }

            // 4. Draw Segment Notches (Thin, elegant markers dividing the bar at 25%, 50%, and 75%)
            // Notch alphas compensated for the single-pass layer: were 0x3C / 0x30, tuned
            // while the old event handler drew ~15x/frame (compounding to ~opaque). [TUNE in-game]
            int[] notches = {20, 40, 60};
            for (int notch : notches) {
                int notchX = barX + notch;
                if (fillWidth > notch) {
                    // Shaded notch on filled bar (dark overlay)
                    guiGraphics.fill(notchX, barY, notchX + 1, barY + barHeight, 0xF0000030);
                } else {
                    // Empty notch on empty bar (slate overlay)
                    guiGraphics.fill(notchX, barY, notchX + 1, barY + barHeight, 0xE82C324D);
                }
            }
        }

        private static void renderChargeBar(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
            int barWidth = 40;
            int barHeight = 3;
            int barX = screenWidth / 2 - barWidth / 2;
            int barY = screenHeight / 2 + 14;

            // Progress maps ticks 20-60 to 0.0-1.0
            float progress = Math.min(1.0f, (clientChargeTimer - 20) / 40.0f);
            int fillWidth = (int) (barWidth * progress);

            // 1. Draw Casing (Styled dark-slate border with rounded corners)
            int borderCol = 0xFF242630;
            guiGraphics.fill(barX, barY - 1, barX + barWidth, barY, borderCol); // Top
            guiGraphics.fill(barX, barY + barHeight, barX + barWidth, barY + barHeight + 1, borderCol); // Bottom
            guiGraphics.fill(barX - 1, barY, barX, barY + barHeight, borderCol); // Left
            guiGraphics.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barHeight, borderCol); // Right

            // Corner/track alphas compensated for the single-pass layer: were 0x66 / 0xAA,
            // tuned while the old event handler drew ~15x/frame (compounding). [TUNE in-game]
            int cornerCol = (borderCol & 0x00FFFFFF) | 0xF0000000;
            guiGraphics.fill(barX - 1, barY - 1, barX, barY, cornerCol); // Top-left
            guiGraphics.fill(barX + barWidth, barY - 1, barX + barWidth + 1, barY, cornerCol); // Top-right
            guiGraphics.fill(barX - 1, barY + barHeight, barX, barY + barHeight + 1, cornerCol); // Bottom-left
            guiGraphics.fill(barX + barWidth, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, cornerCol); // Bottom-right

            // 2. Draw Interior Track
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFA000000);

            // 3. Draw Cylindrical Fill
            if (fillWidth > 0) {
                if (progress >= 1.0f) {
                    // FULLY CHARGED: Pulsing celestial gold/sunlight theme
                    double ms = System.currentTimeMillis();
                    float pulse = (float) Math.sin((ms / 150.0) % (Math.PI * 2)) * 0.12f + 0.88f; // fast subtle golden throb
                    
                    int r1 = (int) (0xFF * pulse);
                    int g1 = (int) (0xFA * pulse);
                    int b1 = (int) (0xB2 * pulse);
                    int topCol = 0xFF000000 | (r1 << 16) | (g1 << 8) | b1; // golden highlight
                    
                    int r2 = (int) (0xFF * pulse);
                    int g2 = (int) (0xD7 * pulse);
                    int b2 = 0x00;
                    int midCol = 0xFF000000 | (r2 << 16) | (g2 << 8) | b2; // bright gold
                    
                    int r3 = (int) (0xC5 * pulse);
                    int g3 = (int) (0x9B * pulse);
                    int b3 = 0x00;
                    int botCol = 0xFF000000 | (r3 << 16) | (g3 << 8) | b3; // deep amber shadow

                    guiGraphics.fill(barX, barY, barX + fillWidth, barY + 1, topCol);
                    guiGraphics.fill(barX, barY + 1, barX + fillWidth, barY + 2, midCol);
                    guiGraphics.fill(barX, barY + 2, barX + fillWidth, barY + 3, botCol);
                } else {
                    // CHARGING: Magical pulsing purple/violet theme
                    guiGraphics.fill(barX, barY, barX + fillWidth, barY + 1, 0xFFCCA2FF); // Row 0 (top highlight)
                    guiGraphics.fill(barX, barY + 1, barX + fillWidth, barY + 2, 0xFF9933FF); // Row 1 (mid purple)
                    guiGraphics.fill(barX, barY + 2, barX + fillWidth, barY + 3, 0xFF5D12B8); // Row 2 (bottom shadow)
                }
            }
        }

        private static void renderPurpleGlow(GuiGraphics guiGraphics, int x, int y, boolean stuck) {
            long ms = System.currentTimeMillis();

            if (stuck) {
                // STUCK STATE: Rapid warning-red distress heartbeat frame.
                double time = ms / 1000.0;
                float pulse = (float) (Math.sin(time * 7.5) * 0.45f + 0.55f); // intense oscillation

                // 1. High-contrast pulsing crimson outer outline
                int r1 = (int) (0xB5 + pulse * 0x4A);
                int g1 = (int) (0x0F + pulse * 0x1A);
                int b1 = 0x12;
                int outerCol = 0xFF000000 | (r1 << 16) | (g1 << 8) | b1;
                guiGraphics.renderOutline(x + 2, y + 2, 18, 18, outerCol);

                // 2. Nested warning orange/yellow inner alert ring
                int r2 = 0xFF;
                int g2 = (int) (0x24 + pulse * 0x6E);
                int b2 = 0x1F;
                int innerCol = 0xFF000000 | (r2 << 16) | (g2 << 8) | b2;
                guiGraphics.renderOutline(x + 3, y + 3, 16, 16, innerCol);

                // 3. Solid hazard-orange corner brackets
                int bracketCol = 0xFFFF4E41;
                // Top-Left
                guiGraphics.fill(x + 2, y + 2, x + 5, y + 3, bracketCol);
                guiGraphics.fill(x + 2, y + 3, x + 3, y + 5, bracketCol);
                // Top-Right
                guiGraphics.fill(x + 17, y + 2, x + 20, y + 3, bracketCol);
                guiGraphics.fill(x + 19, y + 3, x + 20, y + 5, bracketCol);
                // Bottom-Left
                guiGraphics.fill(x + 2, y + 19, x + 5, y + 20, bracketCol);
                guiGraphics.fill(x + 2, y + 17, x + 3, y + 19, bracketCol);
                // Bottom-Right
                guiGraphics.fill(x + 17, y + 19, x + 20, y + 20, bracketCol);
                guiGraphics.fill(x + 19, y + 17, x + 20, y + 19, bracketCol);
            } else {
                // FLYING STATE: Breathing magical telekinetic violet/magenta halo frame.
                double time = ms / 1000.0;
                float pulse = (float) (Math.sin(time * 2.8) * 0.5f + 0.5f); // 0..1 smooth wave

                // 1. Elegant, pulsing medium purple outer outline
                int r1 = (int) (0x7F + pulse * 0x2A);
                int g1 = (int) (0x22 + pulse * 0x1C);
                int b1 = (int) (0xE8 + pulse * 0x17);
                int outerCol = 0xFF000000 | (r1 << 16) | (g1 << 8) | b1;
                guiGraphics.renderOutline(x + 2, y + 2, 18, 18, outerCol);

                // 2. Highlighted bright magenta corner bounds (glowing runes)
                int r2 = (int) (0xDE + pulse * 0x21);
                int g2 = 0x48;
                int b2 = (int) (0xF2 + pulse * 0x0D);
                int bracketCol = 0xFF000000 | (r2 << 16) | (g2 << 8) | b2;
                // Top-Left
                guiGraphics.fill(x + 2, y + 2, x + 5, y + 3, bracketCol);
                guiGraphics.fill(x + 2, y + 3, x + 3, y + 5, bracketCol);
                // Top-Right
                guiGraphics.fill(x + 17, y + 2, x + 20, y + 3, bracketCol);
                guiGraphics.fill(x + 19, y + 3, x + 20, y + 5, bracketCol);
                // Bottom-Left
                guiGraphics.fill(x + 2, y + 19, x + 5, y + 20, bracketCol);
                guiGraphics.fill(x + 2, y + 17, x + 3, y + 19, bracketCol);
                // Bottom-Right
                guiGraphics.fill(x + 17, y + 19, x + 20, y + 20, bracketCol);
                guiGraphics.fill(x + 19, y + 17, x + 20, y + 19, bracketCol);
            }
        }

        private static void renderCooldownGlow(GuiGraphics guiGraphics, int x, int y) {
            long ms = System.currentTimeMillis();
            double time = ms / 1000.0;
            
            // Pulse at a medium steady pace
            float pulse = (float) (Math.sin(time * 3.5) * 0.35f + 0.65f); // 0.3 .. 1.0

            // 1. Ruby/Crimson pulsing outer outline
            int r = (int) (0x9F + pulse * 0x60); // 0x9F to 0xFF
            int g = (int) (0x0C * pulse);
            int b = (int) (0x1C * pulse);
            int outerCol = 0xFF000000 | (r << 16) | (g << 8) | b;
            guiGraphics.renderOutline(x + 2, y + 2, 18, 18, outerCol);

            // 2. Bright scarlet corner brackets (aligned to outer boundaries)
            int rBracket = (int) (0xC8 + pulse * 0x37); // 0xC8 to 0xFF
            int bracketCol = 0xFF000000 | (rBracket << 16) | 0x1E1E; // Vibrant red/crimson
            
            // Symmetrically aligned 3-pixel corner brackets
            // Top-Left
            guiGraphics.fill(x + 2, y + 2, x + 5, y + 3, bracketCol);
            guiGraphics.fill(x + 2, y + 3, x + 3, y + 5, bracketCol);
            // Top-Right
            guiGraphics.fill(x + 17, y + 2, x + 20, y + 3, bracketCol);
            guiGraphics.fill(x + 19, y + 3, x + 20, y + 5, bracketCol);
            // Bottom-Left
            guiGraphics.fill(x + 2, y + 19, x + 5, y + 20, bracketCol);
            guiGraphics.fill(x + 2, y + 17, x + 3, y + 19, bracketCol);
            // Bottom-Right
            guiGraphics.fill(x + 17, y + 19, x + 20, y + 20, bracketCol);
            guiGraphics.fill(x + 19, y + 17, x + 20, y + 19, bracketCol);
        }

        /** Approximate world position of the telekinetic "fist" (works in 1st and 3rd person). */
        private static Vec3 telekineticHandPos(LocalPlayer player) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.getCameraType().isFirstPerson()) {
                return player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0, -0.3, 0);
            }
            float yRot = player.yBodyRot * ((float) Math.PI / 180F);
            double handOffsetZ = Math.cos(yRot) * 0.4;
            double handOffsetX = Math.sin(yRot) * 0.4;
            return new Vec3(player.getX() - handOffsetX,
                    player.getY() + player.getBbHeight() * 0.5, player.getZ() + handOffsetZ);
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
