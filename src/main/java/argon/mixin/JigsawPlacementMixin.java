package argon.mixin;

import argon.layout.BoxOctree;
import argon.layout.TrojanVoxelShape;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Carries the piece bounds in place of the free-space VoxelShape.
 *
 * <p>Vanilla builds {@code join(create(aabb), create(AABB.of(box)), ONLY_FIRST)} here, which is the
 * structure region minus the centre piece. The carrier keeps both halves: the region is the tree
 * boundary and the centre piece goes in as the first placed box. Later joins then keep the carrier
 * and append, so the tree always holds every accepted piece.
 *
 * <p>The wrap answers only for that operator. A different one would mean the free region is no
 * longer the structure region minus the centre piece, and the tree has no answer to give, so the
 * call goes through to vanilla instead.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement", remap = false)
public class JigsawPlacementMixin {
    @WrapOperation(
        method = "lambda$addPieces$2",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;join(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Lnet/minecraft/world/phys/shapes/VoxelShape;"),
        require = 1,
        remap = false
    )
    private static VoxelShape argon$seedFreeShape(
        VoxelShape shape1,
        VoxelShape shape2,
        BooleanOp function,
        Operation<VoxelShape> original,
        @Local(name = "aabb") @NonNull AABB aabb,
        @Local(ordinal = 0, argsOnly = true) @NonNull BoundingBox startBox
    ) {
        if (function != BooleanOp.ONLY_FIRST) {
            return original.call(shape1, shape2, function);
        }
        TrojanVoxelShape shape = new TrojanVoxelShape(new BoxOctree(aabb));
        shape.boxOctree.addBox(startBox);
        return shape;
    }
}
