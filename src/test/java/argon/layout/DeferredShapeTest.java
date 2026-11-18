package argon.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeferredShapeTest {
    private static final AABB BOX = new AABB(-4.0D, 8.0D, -16.0D, 4.0D, 20.0D, -8.0D);

    @Test
    @DisplayName("a deferred shape answers like the vanilla shape it stands for")
    void behavesLikeVanillaShape() {
        DeferredShape deferred = new DeferredShape(BOX);
        VoxelShape vanilla = Shapes.create(BOX);

        assertEquals(vanilla.bounds(), deferred.bounds());
        assertEquals(vanilla.isEmpty(), deferred.isEmpty(), "a piece box is not empty");
        assertFalse(deferred.isEmpty());
        for (Direction.Axis axis : Direction.Axis.values()) {
            assertEquals(vanilla.getCoords(axis), deferred.getCoords(axis), "axis " + axis);
        }
        assertEquals(vanilla.toAabbs(), deferred.toAabbs());
        assertEquals(List.of(BOX), deferred.toAabbs());
        assertEquals(vanilla.singleEncompassing().bounds(), deferred.singleEncompassing().bounds());
    }

    @Test
    @DisplayName("a box inside the unit cube keeps its full bounds even before resolution")
    void unitCubeBoxKeepsItsBounds() {
        AABB tiny = new AABB(0.0D, 0.0D, 0.0D, 0.75D, 0.5D, 0.25D);
        DeferredShape deferred = new DeferredShape(tiny);
        VoxelShape vanilla = Shapes.create(tiny);

        assertEquals(tiny, deferred.bounds());
        assertEquals(vanilla.bounds(), deferred.bounds());
        assertEquals(vanilla.toAabbs(), deferred.toAabbs());
        assertEquals(List.of(tiny), deferred.toAabbs());
    }

    @Test
    @DisplayName("moving a deferred shape keeps the box it carries")
    void moveKeepsTheBox() {
        DeferredShape deferred = new DeferredShape(BOX);
        VoxelShape moved = deferred.move(1.5D, -2.0D, 3.0D);

        assertEquals(BOX.move(1.5D, -2.0D, 3.0D), moved.bounds());
    }

    @Test
    @DisplayName("the vanilla shape is built once and handed back on every resolve")
    void resolutionIsCached() {
        DeferredShape deferred = new DeferredShape(BOX);

        VoxelShape first = deferred.resolved();
        assertSame(first, deferred.resolved());
        assertSame(first, DeferredShape.resolve(deferred));
        assertEquals(BOX, first.bounds());

        VoxelShape vanilla = Shapes.create(BOX);
        assertSame(vanilla, DeferredShape.resolve(vanilla));
    }
}
