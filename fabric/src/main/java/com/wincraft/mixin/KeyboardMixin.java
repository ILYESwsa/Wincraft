package com.wincraft.mixin;

import com.wincraft.client.InputCaptureController;
import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While {@link InputCaptureController#isCapturing()} is true, keyboard
 * events (both raw key events and resolved character events) are
 * diverted to the focused CapturedWindow instead of the player/GUI.
 *
 * ALT-Q itself (the toggle) is read directly in WincraftClient's tick
 * handler via GLFW polling, not through this mixin, so it always works
 * even while capturing — same as waylandcraft reserving that combo.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void wincraft$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (!InputCaptureController.isCapturing()) return;
        if (isReservedToggleCombo(key, modifiers)) return; // let ALT-Q pass through untouched

        CapturedWindow target = WindowManager.get().getFocused();
        if (target == null) return;

        boolean down = action != 0; // GLFW_RELEASE == 0, PRESS == 1, REPEAT == 2 (both count as down)
        target.forwardKey(key, down);
        ci.cancel();
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void wincraft$onChar(long window, int codepoint, int modifiers, CallbackInfo ci) {
        if (!InputCaptureController.isCapturing()) return;

        CapturedWindow target = WindowManager.get().getFocused();
        if (target == null) return;

        target.forwardChar(codepoint);
        ci.cancel();
    }

    private static boolean isReservedToggleCombo(int key, int modifiers) {
        final int GLFW_KEY_Q = 81;
        final int GLFW_MOD_ALT = 0x0004;
        return key == GLFW_KEY_Q && (modifiers & GLFW_MOD_ALT) != 0;
    }
}
