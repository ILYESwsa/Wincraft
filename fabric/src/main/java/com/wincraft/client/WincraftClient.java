package com.wincraft.client;

import com.wincraft.Wincraft;
import com.wincraft.native.WincraftNative;
import com.wincraft.window.WindowManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class WincraftClient implements ClientModInitializer {

    // Mirrors waylandcraft's default binds: B = window manager screen,
    // ALT-Q = toggle hard keyboard capture on the focused window.
    private static KeyBinding openLauncherKey;
    private static KeyBinding toggleCaptureKey;

    @Override
    public void onInitializeClient() {
        WincraftNative.ensureLoaded();

        openLauncherKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wincraft.open_launcher",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.wincraft.general"
        ));

        toggleCaptureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wincraft.toggle_capture",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Q, // combined with Alt held, checked in tick
                "category.wincraft.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        Wincraft.LOGGER.info("wincraft client initialized (native loaded: {})", WincraftNative.isLoaded());
    }

    private void onClientTick(MinecraftClient client) {
        WindowManager.get().tick();

        if (!WincraftNative.isLoaded()) {
            return; // native capture unavailable, e.g. running on non-Windows for dev/testing
        }

        while (openLauncherKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new com.wincraft.client.gui.WindowLauncherScreen());
            }
        }

        boolean altHeld = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT)
                || InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_ALT);

        while (toggleCaptureKey.wasPressed()) {
            if (altHeld) {
                InputCaptureController.toggle();
            }
        }
    }
}
