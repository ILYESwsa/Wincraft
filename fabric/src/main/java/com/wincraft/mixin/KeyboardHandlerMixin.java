package com.wincraft.mixin;

import com.wincraft.client.InputForwarder;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void wincraft$forwardKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (InputForwarder.handleKey(event, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void wincraft$forwardChar(long window, CharacterEvent event, CallbackInfo ci) {
        if (InputForwarder.handleChar(event)) {
            ci.cancel();
        }
    }
}
