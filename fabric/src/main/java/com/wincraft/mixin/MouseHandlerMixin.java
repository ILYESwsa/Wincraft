package com.wincraft.mixin;

import com.wincraft.client.InputForwarder;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void wincraft$forwardButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (InputForwarder.handleMouseButton(button, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void wincraft$forwardMove(long window, double x, double y, CallbackInfo ci) {
        if (InputForwarder.handleMouseMove(x, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void wincraft$forwardScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (InputForwarder.handleMouseScroll(horizontal, vertical)) {
            ci.cancel();
        }
    }
}
