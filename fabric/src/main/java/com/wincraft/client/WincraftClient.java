package com.wincraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wincraft.Wincraft;
import com.wincraft.client.gui.WindowLauncherScreen;
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

    @Override
    public void onInitializeClient() {
        WincraftNative.ensureLoaded();

        openLauncherKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.wincraft.open_launcher",
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
        WindowManager.get().tick();

        if (!WincraftNative.isLoaded()) {
            return;
        }

        // Note: intentionally not gating on "is a screen already open" —
        // that field's exact name in 26.1's official mappings wasn't
        // confirmed after two failed guesses (currentScreen didn't
        // exist). setScreen() simply replaces whatever's open, and
        // consumeClick() already prevents repeat-fire while B is held,
        // so this is safe to call unconditionally.
        while (openLauncherKey.consumeClick()) {
            client.setScreen(new WindowLauncherScreen());
        }
    }
}
