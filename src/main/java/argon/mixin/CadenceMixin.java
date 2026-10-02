package argon.mixin;

import argon.FrameCadence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.FramerateLimiter", remap = false)
public class CadenceMixin {
    @Inject(method = "limitDisplayFPS(I)V", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void argon$cadence(int fps, CallbackInfo callbackInfo) {
        if (!FrameCadence.shouldPace(fps)) {
            FrameCadence.release(fps);
            return;
        }
        callbackInfo.cancel();
        FrameCadence.limit(fps);
    }
}
