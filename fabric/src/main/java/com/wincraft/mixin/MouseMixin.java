package com.wincraft.mixin;

import com.wincraft.client.InputCaptureController;
import com.wincraft.window.CapturedWindow;
import com.wincraft.window.WindowManager;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While {@link InputCaptureController#isCapturing()} is true, mouse
 * button/scroll/move events are diverted to the focused CapturedWindow
 * instead of the player — mirrors waylandcraft's "mouse over window,
 * hard keyboard capture mode" behavior.
 *
 * Method targets match Mojang's official mappings for Minecraft 26.1.
 * NOTE: as of 1.21.11, onMouseButton's signature changed from the old
 * (long, int button, int action, int mods) to (long, MouseInput, int
 * action) — MouseInput is a record carrying button+modifiers. If you're
 * backporting this to an older MC version, use the legacy signature
 * instead.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void wincraft$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (!InputCaptureController.isCapturing()) return;

        CapturedWindow target = WindowManager.get().getFocused();
        if (target == null) return;

        // Coordinates are resolved from the last known cursor position
        // over the in-world window quad; see WindowRenderer for the
        // screen-space -> window-relative-pixel projection math.
        int[] pos = com.wincraft.client.WindowInputCoords.lastRelative();
        boolean down = action != 0; // GLFW_RELEASE == 0
        // MouseInput.button() uses GLFW_MOUSE_BUTTON_* constants:
        // 0 = left, 1 = right, 2 = middle. We only forward left/right.
        int glfwButton = input.button();
        int mappedButton = glfwButton == 1 ? 1 : 0;

        target.forwardMouseButton(mappedButton, down, pos[0], pos[1]);
        ci.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void wincraft$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!InputCaptureController.isCapturing()) return;

        CapturedWindow target = WindowManager.get().getFocused();
        if (target == null) return;

        int[] pos = com.wincraft.client.WindowInputCoords.lastRelative();
        int delta = (int) Math.round(vertical * 120); // WHEEL_DELTA = 120
        target.forwardMouseWheel(delta, pos[0], pos[1]);
        ci.cancel();
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void wincraft$onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (!InputCaptureController.isCapturing()) return;

        CapturedWindow target = WindowManager.get().getFocused();
        if (target == null) return;

        int[] pos = com.wincraft.client.WindowInputCoords.updateFromScreenDelta(x, y);
        target.forwardMouseMove(pos[0], pos[1]);
        ci.cancel();
    }
}
