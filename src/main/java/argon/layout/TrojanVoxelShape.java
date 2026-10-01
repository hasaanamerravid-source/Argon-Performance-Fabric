package argon.layout;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/** Carrier so the vanilla VoxelShape parameter can hold a BoxOctree without changing call sites. */
public final class TrojanVoxelShape extends VoxelShape {
    public final BoxOctree boxOctree;

    public TrojanVoxelShape(BoxOctree boxOctree) {
        super(BitSetDiscreteVoxelShape.withFilledBounds(1, 1, 1, 0, 0, 0, 1, 1, 1));
        this.boxOctree = boxOctree;
    }

    @Override
    public @NonNull DoubleList getCoords(Direction.@NonNull Axis axis) {
        return DoubleLists.EMPTY_LIST;
    }
}
