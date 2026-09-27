package argon.mixin;

import argon.layout.BoxOctree;
import argon.layout.JigsawMatch;
import argon.layout.TrojanArrayList;
import argon.layout.TrojanVoxelShape;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer", remap = false)
public class JigsawPlacementPlacerMixin {
    @Final
    @Shadow
    private net.minecraft.util.RandomSource random;

    @Final
    @Shadow
    private net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager structureTemplateManager;

    @Redirect(
            method = "tryPlacingChildren(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Lorg/apache/commons/lang3/mutable/MutableObject;IZLnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/JigsawBlock;canAttach(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$JigsawBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$JigsawBlockInfo;)Z"),
            require = 1,
            remap = false)
    private boolean argon$matchJigsaws(StructureTemplate.JigsawBlockInfo parent, StructureTemplate.JigsawBlockInfo child) {
        return JigsawMatch.canAttach(parent, child);
    }

    @ModifyExpressionValue(
            method = "tryPlacingChildren",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructurePoolElement;getShuffledJigsawBlocks(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/util/RandomSource;)Ljava/util/List;", ordinal = 1),
            require = 1,
            remap = false)
    private List<StructureTemplate.JigsawBlockInfo> argon$skipBlockedRigid(
            List<StructureTemplate.JigsawBlockInfo> original,
            @Local(name = "childrenFree") MutableObject<VoxelShape> free,
            @Local(name = "targetElement") StructurePoolElement candidate,
            @Local(name = "targetJigsawPos") BlockPos parentTargetPosition) {
        if (!(free.get() instanceof TrojanVoxelShape trojan)) {
            return original;
        }
        if (candidate.getProjection() != StructureTemplatePool.Projection.RIGID) {
            return original;
        }
        // A rigid child cannot fit if the parent jigsaw faces outside the structure box or into a piece already placed.
        // Vanilla would reject that child after the same checks. The shuffle for this list already ran, so the random is unchanged.
        if (!trojan.boxOctree.boundaryContains(parentTargetPosition) || trojan.boxOctree.withinAnyBox(parentTargetPosition)) {
            return new ArrayList<>();
        }
        return original;
    }

    @WrapOperation(
            method = "tryPlacingChildren(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Lorg/apache/commons/lang3/mutable/MutableObject;IZLnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"),
            require = 1,
            remap = false)
    private boolean argon$nearbyIntersection(VoxelShape parentBounds, VoxelShape pieceShape, BooleanOp op, Operation<Boolean> original,
                                             @Local(name = "targetBB") BoundingBox pieceBounds) {
        if (parentBounds instanceof TrojanVoxelShape trojan) {
            AABB piece = AABB.of(pieceBounds).deflate(0.25D);
            // Caller negates this result, same as vanilla joinIsNotEmpty.
            return !trojan.boxOctree.withinBoundsButNotIntersectingChildren(piece);
        }
        return original.call(parentBounds, pieceShape, op);
    }

    @Redirect(
            method = "tryPlacingChildren(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Lorg/apache/commons/lang3/mutable/MutableObject;IZLnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",
            at = @At(value = "INVOKE", target = "Lorg/apache/commons/lang3/mutable/MutableObject;setValue(Ljava/lang/Object;)V", ordinal = 0),
            require = 1,
            remap = false)
    private void argon$resetFreeShape(MutableObject<VoxelShape> instance, Object value, @Local(name = "sourceBB") BoundingBox pieceBounds) {
        instance.setValue(new TrojanVoxelShape(new BoxOctree(AABB.of(pieceBounds))));
    }

    @Redirect(
            method = "tryPlacingChildren(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Lorg/apache/commons/lang3/mutable/MutableObject;IZLnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",
            at = @At(value = "INVOKE", target = "Lorg/apache/commons/lang3/mutable/MutableObject;setValue(Ljava/lang/Object;)V", ordinal = 1),
            require = 1,
            remap = false)
    private void argon$addPlacedBox(MutableObject<VoxelShape> instance, Object value, @Local(name = "targetBB") BoundingBox pieceBounds) {
        if (instance.get() instanceof TrojanVoxelShape trojan) {
            trojan.boxOctree.addBox(AABB.of(pieceBounds));
        }
    }

    @Redirect(
            method = "tryPlacingChildren(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Lorg/apache/commons/lang3/mutable/MutableObject;IZLnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinUnoptimized(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Lnet/minecraft/world/phys/shapes/VoxelShape;"),
            require = 1,
            remap = false)
    private VoxelShape argon$ignoreOldJoin(VoxelShape left, VoxelShape right, BooleanOp op) {
        return Shapes.empty();
    }

    @Redirect(
            method = "tryPlacingChildren(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Lorg/apache/commons/lang3/mutable/MutableObject;IZLnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;", ordinal = 0),
            require = 1,
            remap = false)
    private ArrayList<StructurePoolElement> argon$trackTriedElements() {
        return new TrojanArrayList<>();
    }

    @ModifyExpressionValue(
            method = "tryPlacingChildren(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Lorg/apache/commons/lang3/mutable/MutableObject;IZLnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Rotation;getShuffled:(Lnet/minecraft/util/RandomSource;)Ljava/util/List;", ordinal = 0),
            require = 1,
            remap = false)
    private List<Rotation> argon$skipRepeatElement(List<Rotation> original,
                                                   @Local(name = "targetPieces") List<StructurePoolElement> list,
                                                   @Local(name = "targetElement") StructurePoolElement element) {
        if (!(list instanceof TrojanArrayList<?> trojan)) {
            return original;
        }
        @SuppressWarnings("unchecked")
        TrojanArrayList<StructurePoolElement> tried = (TrojanArrayList<StructurePoolElement>) trojan;
        if (tried.elementsAlreadyParsed.contains(element)) {
            // Same random calls vanilla would make while shuffling this element's jigsaws, then skip the work.
            for (Rotation rotation : original) {
                element.getShuffledJigsawBlocks(this.structureTemplateManager, BlockPos.ZERO, rotation, this.random);
            }
            return new ArrayList<>();
        }
        tried.elementsAlreadyParsed.add(element);
        return original;
    }
}
