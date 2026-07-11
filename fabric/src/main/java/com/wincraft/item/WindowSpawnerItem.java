package com.wincraft.item;

import com.wincraft.Wincraft;
import com.wincraft.natives.WindowHandle;
import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Picking a window in WindowLauncherScreen gives the player one of these
 * instead of opening the capture immediately (see
 * WindowLauncherScreen#openWindow). Each item remembers which desktop
 * window it refers to (hwnd + app title), and the window only actually
 * appears in the world once the item is used:
 *
 *  - If no capture for this hwnd is currently open, use() starts one and
 *    places it at the crosshair, distanceBlocks in front of the player.
 *  - If a capture for this hwnd is already open — including one placed
 *    by a *different* item stack for the same app, e.g. after the
 *    original item despawned on the ground and the player picked a
 *    fresh one from the launcher — use() moves that same window to the
 *    new crosshair position instead of spawning a duplicate.
 *
 * The dedupe lives in WindowManager#openAtCrosshair, keyed by hwnd
 * (WindowManager#findByHwnd), not by the UUID this item happens to
 * remember — an item's own windowId is only a best-effort cache; the
 * hwnd lookup is what actually prevents duplicates, since it doesn't
 * rely on any single ItemStack surviving.
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

        // openAtCrosshair itself checks WindowManager#findByHwnd first and
        // just moves the existing capture if one's already running for
        // this hwnd, so this single call handles both "first placement"
        // and "move the existing window" — no duplicate is possible even
        // if this item's own remembered windowId is stale or was never set.
        WindowHandle handle = new WindowHandle(data.hwnd(), data.title(), data.className());
        CapturedWindow window = manager.openAtCrosshair(handle, PLACE_DISTANCE);
        if (window == null) {
            // No confirmed in-chat message API for this MC version yet —
            // log it like every other capture failure in this mod
            // (see CapturedWindow/WincraftNative) rather than guess again
            // at a player-facing method name.
            Wincraft.LOGGER.warn("Failed to capture window \"{}\" (hwnd={})", data.title(), data.hwnd());
            return InteractionResult.FAIL;
        }

        stack.set(ModComponents.WINDOW_DATA, data.withWindowId(window.id));
        return InteractionResult.SUCCESS;
    }
}