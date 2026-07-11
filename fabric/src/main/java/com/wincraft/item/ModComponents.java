package com.wincraft.item;

import com.wincraft.Wincraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Registers wincraft's custom item data components. */
public final class ModComponents {

    public static final DataComponentType<WindowItemData> WINDOW_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(Wincraft.MOD_ID, "window_data"),
            DataComponentType.<WindowItemData>builder().persistent(WindowItemData.CODEC).build()
    );

    private ModComponents() {}

    /** Forces class init (and therefore registration) from the mod entrypoint. */
    public static void init() {}
}
