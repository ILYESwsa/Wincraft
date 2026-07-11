package com.wincraft.item;

import com.wincraft.Wincraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.function.Function;

/** Registers wincraft's custom items. */
public final class ModItems {

    public static final Item WINDOW_SPAWNER = register(
            "window_spawner",
            WindowSpawnerItem::new,
            new Item.Properties().stacksTo(1)
    );

    private ModItems() {}

    /** Forces class init (and therefore registration) from the mod entrypoint. */
    public static void init() {}

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Wincraft.MOD_ID, name));
        T item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
