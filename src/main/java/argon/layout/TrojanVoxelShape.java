package argon.layout;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/** Carrier so the vanilla VoxelShape parameter can hold a BoxOctree without changing call sites. */
public final class TrojanVoxelShape extends VoxelShape {
    private static final DiscreteVoxelShape PLACEHOLDER =
        BitSetDiscreteVoxelShape.withFilledBounds(1, 1, 1, 0, 0, 0, 1, 1, 1);

    /**
     * The placeholder covers one filled cell from zero to one, so its coordinates have to say the
     * same. An empty coordinate list contradicted the filled discrete shape: any vanilla method
     * that reached the carrier by accident, such as bounds or a face lookup, would have indexed an
     * empty list instead of getting a small but valid shape.
     */
    private static final DoubleList PLACEHOLDER_COORDS = DoubleList.of(0.0D, 1.0D);

    public final BoxOctree boxOctree;

    public TrojanVoxelShape(BoxOctree boxOctree) {
        super(PLACEHOLDER);
        this.boxOctree = boxOctree;
    }

    @Override
    public @NonNull DoubleList getCoords(Direction.@NonNull Axis axis) {
        return PLACEHOLDER_COORDS;
    }
}
