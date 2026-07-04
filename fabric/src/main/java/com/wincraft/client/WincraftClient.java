package com.wincraft.client;

import com.wincraft.Wincraft;
import com.wincraft.natives.WincraftNative;
import com.wincraft.window.WindowManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint. UI/keybinding layer intentionally minimal for now —
 * this just loads the native lib and ticks the window manager. Launcher
 * screen and capture-toggle keybind will be layered back in once the
 * core capture pipeline is confirmed working against real MC 26.1 APIs.
 */
public class WincraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WincraftNative.ensureLoaded();
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        Wincraft.LOGGER.info("wincraft client initialized (native loaded: {})", WincraftNative.isLoaded());
    }

    private void onClientTick(Minecraft client) {
        WindowManager.get().tick();
    }
}
