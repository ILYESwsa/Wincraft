package com.wincraft;

import com.wincraft.item.ModComponents;
import com.wincraft.item.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Wincraft implements ModInitializer {

    public static final String MOD_ID = "wincraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("wincraft initializing — Windows desktop windows in Minecraft");

        // Registries must run on both logical sides (in case multiplayer
        // window-sharing lands later), so these live in the common
        // entrypoint rather than WincraftClient.
        ModComponents.init();
        ModItems.init();

        // Server-side networking registration (opening/closing/frame
        // sync packets) goes here once multiplayer window-sharing is
        // wired up. For now this mirrors waylandcraft's client-only
        // v1 behavior.
    }
}
