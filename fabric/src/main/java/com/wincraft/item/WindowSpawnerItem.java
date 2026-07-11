package com.wincraft.item;

import com.wincraft.natives.WindowHandle;
import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Picking a window in WindowLauncherScreen gives the player one of these
 * instead of opening the capture immediately (see
 * WindowLauncherScreen#openWindow). The window only actually appears in
 * the world once the item is used:
 *
 *  - First use (no live window bound yet, or the previously bound one
 *    was closed some other way): starts the capture and places it at the
 *    crosshair, distanceBlocks in front of the player. The resulting
 *    CapturedWindow's id is written back onto the stack.
 *  - Any later use, while that window is still open: moves the *same*
 *    window to the new crosshair position instead of spawning a
 *    duplicate — this is the "modify its coordinates" behavior.
 *
 * Right-click (use) rather than a screen click is intentional here so
 * placement can reuse the same eye+look raycast math the renderer and
 * WindowManagerScreen already use for "look at a spot, act on it".
 */
public class WindowSpawnerItem extends Item {

    private static final double PLACE_DISTANCE = 3.0D;

    public WindowSpawnerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        // Wincraft is a client-only mod (see fabric.mod.json) — WindowManager,
        // native capture, and the render quads only exist on the client
        // logical side, so unlike a typical block/item interaction there is
        // no separate server-authoritative path to defer to here.
        if (!level.isClientSide()) {
            return InteractionResult.PASS;
        }

        ItemStack stack = user.getItemInHand(hand);
        WindowItemData data = stack.get(ModComponents.WINDOW_DATA);
        if (data == null) {
            return InteractionResult.FAIL;
        }

        WindowManager manager = WindowManager.get();

        // Already placed and still alive -> move it, don't duplicate.
        Optional<UUID> existing = data.windowId();
        if (existing.isPresent() && manager.get(existing.get()) != null) {
            manager.moveToCrosshair(existing.get(), PLACE_DISTANCE);
            return InteractionResult.SUCCESS;
        }

        // Not placed yet (or was closed elsewhere, e.g. the B manager
        // screen's Close button) -> start a fresh capture at the crosshair.
        WindowHandle handle = new WindowHandle(data.hwnd(), data.title(), data.className());
        CapturedWindow window = manager.openAtCrosshair(handle, PLACE_DISTANCE);
        if (window == null) {
            user.displayClientMessage(Component.translatable("item.wincraft.window_spawner.capture_failed", data.title()), true);
            return InteractionResult.FAIL;
        }

        stack.set(ModComponents.WINDOW_DATA, data.withWindowId(window.id));
        return InteractionResult.SUCCESS;
    }
}
