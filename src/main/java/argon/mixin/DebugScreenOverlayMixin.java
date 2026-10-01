package argon.mixin;

import argon.debug.ArgonDebugLines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.components.DebugScreenOverlay", remap = false)
public class DebugScreenOverlayMixin {
    @Unique
    private static GuiGraphicsExtractor argon$extractor;

    @Unique
    private static boolean argon$wrote;

    @Inject(method = "extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;ZI)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void argon$debugLine(GuiGraphicsExtractor extractor, List<String> lines, boolean leftSide, int width,
                                 CallbackInfo callbackInfo) {
        if (lines == null) {
            return;
        }
        rememberPass(extractor);
        int kept = stripExtraBrandLines(lines, leftSide);
        if (kept >= 0 || !leftSide || argon$wrote) {
            if (kept >= 0) {
                argon$wrote = true;
            }
            return;
        }
        lines.add(ArgonDebugLines.current());
        argon$wrote = true;
    }

    @Unique
    private static void rememberPass(GuiGraphicsExtractor extractor) {
        if (extractor == argon$extractor) {
            return;
        }
        argon$extractor = extractor;
        argon$wrote = false;
    }

    /**
     * Keeps the first brand line and drops the rest. The replacement text is only built when a
     * brand line is actually there to rewrite, which is what the right-side batches skip.
     */
    @Unique
    private static int stripExtraBrandLines(List<String> lines, boolean leftSide) {
        int kept = -1;
        for (int i = 0; i < lines.size(); ) {
            String existing = lines.get(i);
            if (!ArgonDebugLines.isBrandLine(existing)) {
                i++;
                continue;
            }
            if (!leftSide || kept >= 0 || argon$wrote) {
                lines.remove(i);
                continue;
            }
            String line = ArgonDebugLines.current();
            if (!line.equals(existing)) {
                lines.set(i, line);
            }
            kept = i;
            i++;
        }
        return kept;
    }
}
