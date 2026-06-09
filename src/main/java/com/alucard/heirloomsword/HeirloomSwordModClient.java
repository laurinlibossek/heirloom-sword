package com.alucard.heirloomsword;

import com.alucard.heirloomsword.client.SwordFamiliarRenderer;
import com.alucard.heirloomsword.network.SwordModePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

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
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SWORD_FAMILIAR.get(), SwordFamiliarRenderer::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            HeirloomSwordMod.LOGGER.info("Heirloom Sword client initialized");
        }
    }

    @EventBusSubscriber(modid = HeirloomSwordMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            while (ModKeybinds.TOGGLE_MODE.consumeClick()) {
                LocalPlayer player = mc.player;
                ItemStack held = player.getMainHandItem();
                if (held.getItem() instanceof HeirloomSwordItem) {
                    PacketDistributor.sendToServer(new SwordModePacket());
                    SwordMode current = HeirloomSwordItem.getMode(held);
                    SwordMode next = current == SwordMode.NORMAL ? SwordMode.FLYING : SwordMode.NORMAL;
                    HeirloomSwordItem.setMode(held, next);
                }
            }
        }

        @SubscribeEvent
        public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            LocalPlayer player = mc.player;
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
        }

        private static void renderPurpleGlow(GuiGraphics guiGraphics, int x, int y) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(0.6f, 0.2f, 0.9f, 0.3f);

            guiGraphics.fill(x, y, x + 22, y + 22, 0x4D9933FF);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
    }
}
