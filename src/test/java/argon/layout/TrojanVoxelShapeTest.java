package argon.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrojanVoxelShapeTest {
    @Test
    @DisplayName("the carrier's coordinates describe the filled placeholder instead of no shape at all")
    void placeholderCoordinatesAreValid() {
        TrojanVoxelShape shape = new TrojanVoxelShape(
            new BoxOctree(new AABB(0.0D, 0.0D, 0.0D, 8.0D, 8.0D, 8.0D)));

        for (Direction.Axis axis : Direction.Axis.values()) {
            DoubleList coords = shape.getCoords(axis);
            assertEquals(2, coords.size(), "axis " + axis);
            assertEquals(0.0D, coords.getDouble(0), "axis " + axis);
            assertEquals(1.0D, coords.getDouble(1), "axis " + axis);
        }
        assertEquals(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D), shape.bounds());
    }
}
