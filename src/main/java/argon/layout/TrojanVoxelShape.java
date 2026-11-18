package argon.layout;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/** Carrier so the vanilla VoxelShape parameter can hold a BoxOctree without changing call sites. */
public final class TrojanVoxelShape extends VoxelShape {
    public final BoxOctree boxOctree;

    /**
     * The placeholder covers one filled cell from zero to one. Its coordinates come from the shared
     * carrier set, so a vanilla method that reaches this shape by accident sees a small valid box
     * rather than indexing an empty coordinate list.
     */
    public TrojanVoxelShape(BoxOctree boxOctree) {
        super(VoxelCarriers.PLACEHOLDER);
        this.boxOctree = boxOctree;
    }

    @Override
    public @NonNull DoubleList getCoords(Direction.@NonNull Axis axis) {
        return VoxelCarriers.PLACEHOLDER_COORDS;
    }
}
