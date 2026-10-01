package argon.mixin;

import argon.debug.ArgonProfiler;
import argon.layout.TrojanVoxelShape;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the octree stand in for the free-space VoxelShape inside tryPlacingChildren.
 *
 * <p>Both wraps answer only when the first operand is Argon's carrier. A child attached inside its
 * own parent piece runs against vanilla's per-piece {@code sourceFree} shape, which the tree does
 * not model, and those calls fall through to vanilla untouched. Accepted pieces are appended to the
 * tree on the carrier path, so the octree keeps the same contents the VoxelShape would have had.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer", remap = false)
public class JigsawPlacementPlacerMixin {
    /**
     * Vanilla accepts on {@code !joinIsNotEmpty(free, deflated, ONLY_SECOND)}, so this wrap answers
     * the reject side: true when the 0.25-deflated piece box leaves the free region or lands on a
     * piece already in the tree. The free region is the tree boundary and the tree holds the placed
     * pieces, which is the same set the carrier VoxelShape would carry.
     */
    @WrapOperation(
        method = "tryPlacingChildren",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"
        ),
        require = 1,
        remap = false
    )
    private boolean argon$overlap(VoxelShape shape1, VoxelShape shape2, BooleanOp op, Operation<Boolean> original,
                                  @Local BoundingBox box) {
        ArgonProfiler.PIECES_TESTED.increment();
        if (shape1 instanceof TrojanVoxelShape trojan) {
            ArgonProfiler.OCTREE_CHECKS_SAVED.increment();
            return !trojan.boxOctree.hasRoomFor(box);
        }
        return original.call(shape1, shape2, op);
    }

    @WrapOperation(
        method = "tryPlacingChildren",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;joinUnoptimized(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
        ),
        require = 1,
        remap = false
    )
    private VoxelShape argon$recordPiece(VoxelShape shape1, VoxelShape shape2, BooleanOp op, Operation<VoxelShape> original,
                                         @Local BoundingBox box) {
        if (shape1 instanceof TrojanVoxelShape trojan) {
            trojan.boxOctree.addBox(box);
            return shape1;
        }
        return original.call(shape1, shape2, op);
    }
}
