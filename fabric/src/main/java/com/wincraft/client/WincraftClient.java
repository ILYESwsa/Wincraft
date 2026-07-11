package com.wincraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wincraft.Wincraft;
import com.wincraft.client.gui.WindowLauncherScreen;
import com.wincraft.client.gui.WindowManagerScreen;
import com.wincraft.client.render.WincraftWorldRenderer;
import com.wincraft.natives.WincraftNative;
import com.wincraft.window.WindowManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class WincraftClient implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Wincraft.MOD_ID, "general")
    );

    private static KeyMapping openLauncherKey;
    private static KeyMapping toggleCaptureKey;
    private static KeyMapping openManagerKey;

    @Override
    public void onInitializeClient() {
        WincraftNative.ensureLoaded();

        openLauncherKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.wincraft.open_launcher",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));

        // Toggles keyboard+mouse capture into the focused window, mirroring
        // waylandcraft's "G" binding. Alt+Q also releases (see InputForwarder).
        toggleCaptureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.wincraft.toggle_capture",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));

        // Opens the window-manager screen for moving/resizing/focusing/closing
        // already-open windows, mirroring waylandcraft's "B" binding.
        openManagerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.wincraft.open_manager",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        ));

        WincraftWorldRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            InputCaptureController.release();
            WindowManager.get().closeAll();
        });

        Wincraft.LOGGER.info("wincraft client initialized (native loaded: {})", WincraftNative.isLoaded());
    }

    private void onClientTick(Minecraft client) {
        // Frame polling for captured windows now happens in
        // WincraftWorldRenderer#render, once per rendered frame, instead
        // of here — the client tick loop is fixed at 20/sec regardless of
        // actual framerate, which capped window updates at a choppy 20fps.
        if (!WincraftNative.isLoaded()) {
            return;
        }

        // consumeClick() only fires while no screen owns keyboard focus
        // (vanilla KeyMapping behavior), so these are naturally gated
        // against firing while the launcher/manager/pause screen is open.
        while (openLauncherKey.consumeClick()) {
            client.setScreen(new WindowLauncherScreen());
        }

        while (openManagerKey.consumeClick()) {
            client.setScreen(new WindowManagerScreen());
        }

        // Only toggle capture while no Minecraft screen is open — capture
        // is an in-world interaction, not something that should fire while
        // e.g. the launcher or pause menu has keyboard focus.
        while (toggleCaptureKey.consumeClick()) {
            InputCaptureController.toggle();
        }
    }
}