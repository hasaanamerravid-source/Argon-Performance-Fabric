package argon.layout;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

/**
 * Pieces shared by Argon's {@link net.minecraft.world.phys.shapes.VoxelShape} carriers.
 *
 * <p>Both carriers are a single box. A world-space {@code Shapes.create} of a single box is also
 * a single filled cell plus one coordinate pair per axis, so the same discrete half works for all
 * of them and no per-instance shape is built.
 */
final class VoxelCarriers {
    static final DiscreteVoxelShape PLACEHOLDER =
        BitSetDiscreteVoxelShape.withFilledBounds(1, 1, 1, 0, 0, 0, 1, 1, 1);

    /** Coordinates of the single filled cell, from zero to one on every axis. */
    static final DoubleList PLACEHOLDER_COORDS = DoubleList.of(0.0D, 1.0D);

    private VoxelCarriers() {
    }
}
