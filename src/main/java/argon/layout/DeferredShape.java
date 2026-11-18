package argon.layout;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A box carried as the shape the layout would have built for it, without building it.
 *
 * <p>Vanilla builds a {@link VoxelShape} for every shape it passes to {@code Shapes.join} and
 * {@code Shapes.joinIsNotEmpty}. A shape for a world-space box is one filled cell plus three
 * two-entry coordinate lists, and Argon answers the same question from the octree, so every one of
 * those shapes is built and then thrown away. A deferred shape keeps the box instead and builds
 * the vanilla shape only when a call falls through to vanilla, which is the inside-parent attach
 * path and nothing else.
 *
 * <p>The placeholder cell is one filled cell and the coordinate pair is the box's own bounds, so
 * the inherited geometry is the box itself even before the shape is resolved. Resolution exists
 * only for the vanilla join, which needs the exact discretisation {@code Shapes.create} would have
 * produced for a box inside the unit cube.
 */
public final class DeferredShape extends VoxelShape {
    private final AABB box;
    private @Nullable VoxelShape resolved;

    public DeferredShape(@NonNull AABB box) {
        super(VoxelCarriers.PLACEHOLDER);
        this.box = box;
    }

    /**
     * The shape {@code Shapes.create(box)} would have returned, built once and kept.
     *
     * <p>A shape is only resolved when a call falls through to vanilla. The layout asks the octree
     * its own questions and never reads coordinates off this carrier.
     */
    public @NonNull VoxelShape resolved() {
        VoxelShape shape = resolved;
        if (shape == null) {
            shape = Shapes.create(box);
            resolved = shape;
        }
        return shape;
    }

    /** The carrier's box as a plain shape, or the shape itself when it is already vanilla's. */
    public static @NonNull VoxelShape resolve(@NonNull VoxelShape shape) {
        return shape instanceof DeferredShape deferred ? deferred.resolved() : shape;
    }

    @Override
    public @NonNull DoubleList getCoords(Direction.@NonNull Axis axis) {
        return switch (axis) {
            case X -> DoubleList.of(box.minX, box.maxX);
            case Y -> DoubleList.of(box.minY, box.maxY);
            case Z -> DoubleList.of(box.minZ, box.maxZ);
        };
    }
}
