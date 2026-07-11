package com.wincraft.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Data carried on a WindowSpawnerItem stack: which desktop window it
 * refers to, and — once the player has placed it in the world at least
 * once — which live CapturedWindow (by id in WindowManager) it's
 * currently bound to.
 *
 * windowId is empty right after picking the window from the launcher
 * screen (nothing placed yet). It's set the first time the item is used
 * and cleared again if that CapturedWindow gets closed some other way
 * (e.g. via the B window-manager screen), so a stale id never causes a
 * silent no-op — see WindowSpawnerItem.use().
 */
public record WindowItemData(long hwnd, String title, String className, Optional<UUID> windowId) {

    public static final Codec<WindowItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("hwnd").forGetter(WindowItemData::hwnd),
            Codec.STRING.fieldOf("title").forGetter(WindowItemData::title),
            Codec.STRING.fieldOf("class_name").forGetter(WindowItemData::className),
            UUIDUtil.STRING_CODEC.optionalFieldOf("window_id").forGetter(WindowItemData::windowId)
    ).apply(instance, WindowItemData::new));

    public WindowItemData withWindowId(UUID id) {
        return new WindowItemData(hwnd, title, className, Optional.of(id));
    }

    public WindowItemData withoutWindowId() {
        return new WindowItemData(hwnd, title, className, Optional.empty());
    }
}
