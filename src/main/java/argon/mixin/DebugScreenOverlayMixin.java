package argon.mixin;

import argon.Argon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.components.DebugScreenOverlay", remap = false)
public class DebugScreenOverlayMixin {
    private static final char CODE = '\u00A7';

    @Inject(method = "extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;ZI)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void argon$debugLine(GuiGraphicsExtractor extractor, List<String> lines, boolean leftSide, int width,
                                 CallbackInfo callbackInfo) {
        if (!leftSide) {
            return;
        }
        lines.add(CODE + "bArgon " + CODE + "a" + Argon.VERSION + "+26.3" + CODE + "7 layout optimizer, frame cadence");
    }
}
