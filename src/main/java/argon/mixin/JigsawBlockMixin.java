package argon.mixin;

import argon.layout.JigsawMatch;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.JigsawBlock", remap = false)
public class JigsawBlockMixin {
    @Inject(
        method = "canAttach(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$JigsawBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$JigsawBlockInfo;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1,
        remap = false
    )
    private static void argon$canAttach(
        StructureTemplate.JigsawBlockInfo parent,
        StructureTemplate.JigsawBlockInfo child,
        CallbackInfoReturnable<Boolean> cir
    ) {
        cir.setReturnValue(JigsawMatch.canAttach(parent, child));
    }
}
