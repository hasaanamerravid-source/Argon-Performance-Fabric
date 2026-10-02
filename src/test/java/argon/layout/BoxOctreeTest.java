package argon.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the octree against a plain cell model of the vanilla rule.
 *
 * <p>Vanilla keeps a free-space VoxelShape, seeds it as the structure region minus the centre piece,
 * and accepts a candidate when {@code joinIsNotEmpty(free, deflated, ONLY_SECOND)} is empty. That is
 * every cell of the candidate sitting inside the region and inside free, and free is the region
 * minus every piece already accepted. Both bounds are integer aligned, so the 0.25 deflate only
 * decides whether two boxes that share a cell count as touching, which leaves one rule to reproduce:
 * the candidate sits inside the region and shares no cell with any placed piece. The reference here
 * is that rule in cell coordinates, with no tree and no deflate anywhere in it.
 */
class BoxOctreeTest {
    private static final int REGION_MIN = -64;
    private static final int REGION_MAX = REGION_MIN + 128;
    private static final int MAX_SPAN = 12;
    private static final int OVERHANG = 8;

    private static AABB region() {
        return new AABB(REGION_MIN, REGION_MIN, REGION_MIN, REGION_MAX, REGION_MAX, REGION_MAX);
    }

    /** Half-open cell range, the way a VoxelShape covers a box. */
    private record Cell(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean insideRegion() {
            return minX >= REGION_MIN && minY >= REGION_MIN && minZ >= REGION_MIN
                && maxX <= REGION_MAX && maxY <= REGION_MAX && maxZ <= REGION_MAX;
        }

        /**
         * Whether the cell reaches the region at all. The tree is built around the region and only
         * keeps what reaches it, so a cell that does not is outside the index even though it may
         * well overlap another such cell.
         */
        boolean reachesRegion() {
            return minX < REGION_MAX && minY < REGION_MAX && minZ < REGION_MAX
                && maxX > REGION_MIN && maxY > REGION_MIN && maxZ > REGION_MIN;
        }

        boolean sharesCell(Cell other) {
            return minX < other.maxX && maxX > other.minX
                && minY < other.maxY && maxY > other.minY
                && minZ < other.maxZ && maxZ > other.minZ;
        }

        BoundingBox box() {
            return new BoundingBox(minX, minY, minZ, maxX - 1, maxY - 1, maxZ - 1);
        }

        BlockPos minCorner() {
            return new BlockPos(minX, minY, minZ);
        }

        BlockPos maxCorner() {
            return new BlockPos(maxX - 1, maxY - 1, maxZ - 1);
        }

        /** A cell of this piece that the region covers, or null when the region misses it. */
        BlockPos interiorOfRegion() {
            int x = Math.max(minX, REGION_MIN);
            int y = Math.max(minY, REGION_MIN);
            int z = Math.max(minZ, REGION_MIN);
            if (x >= Math.min(maxX - 1, REGION_MAX - 1)
                || y >= Math.min(maxY - 1, REGION_MAX - 1)
                || z >= Math.min(maxZ - 1, REGION_MAX - 1)) {
                return null;
            }
            return new BlockPos(x, y, z);
        }
    }

    private static boolean vanillaAccepts(List<Cell> placed, Cell candidate) {
        if (!candidate.insideRegion()) {
            return false;
        }
        for (Cell box : placed) {
            if (candidate.sharesCell(box)) {
                return false;
            }
        }
        return true;
    }

    private static Cell randomCell(Random random, boolean mayLeaveRegion) {
        int sizeX = 1 + random.nextInt(MAX_SPAN);
        int sizeY = 1 + random.nextInt(MAX_SPAN);
        int sizeZ = 1 + random.nextInt(MAX_SPAN);
        int reach = REGION_MAX - REGION_MIN - sizeX + 1 + (mayLeaveRegion ? OVERHANG : 0);
        int minX = REGION_MIN - (mayLeaveRegion ? OVERHANG : 0) + random.nextInt(reach);
        int minY = REGION_MIN - (mayLeaveRegion ? OVERHANG : 0) + random.nextInt(reach);
        int minZ = REGION_MIN - (mayLeaveRegion ? OVERHANG : 0) + random.nextInt(reach);
        return new Cell(minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ);
    }

    private static List<Cell> randomCells(Random random, int count, boolean mayLeaveRegion) {
        List<Cell> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cells.add(randomCell(random, mayLeaveRegion));
        }
        return cells;
    }

    @Test
    @DisplayName("every candidate gets the same answer the vanilla cell model gives it")
    void differentialAgainstVanillaCells() {
        for (int seed = 0; seed < 8; seed++) {
            Random random = new Random(seed);
            BoxOctree tree = new BoxOctree(region());
            List<Cell> placed = new ArrayList<>();

            Cell centre = new Cell(0, 0, 0, 8, 8, 8);
            tree.addBox(centre.box());
            placed.add(centre);

            int accepted = 0;
            int rejectedForRegion = 0;
            for (int step = 0; step < 400; step++) {
                Cell candidate = randomCell(random, true);
                boolean expected = vanillaAccepts(placed, candidate);
                boolean actual = tree.hasRoomFor(candidate.box());
                assertEquals(expected, actual,
                    "seed " + seed + " step " + step + " candidate " + candidate);
                if (actual) {
                    tree.addBox(candidate.box());
                    placed.add(candidate);
                    accepted++;
                } else if (!candidate.insideRegion()) {
                    rejectedForRegion++;
                }
            }
            assertTrue(accepted > 0, "seed " + seed + " placed nothing");
            assertTrue(rejectedForRegion > 0, "seed " + seed + " never tested a candidate off the region");
        }
    }

    @Test
    @DisplayName("the tree answers overlaps the same as the list it was built from, inside the region")
    void intersectsAgainstPlacedList() {
        for (int seed = 0; seed < 8; seed++) {
            Random random = new Random(100 + seed);
            BoxOctree tree = new BoxOctree(region());
            // Boxes may hang over the edge, so the stored set is not confined to the region. The
            // tree is, though: a box that never reaches the region is not kept, so a probe that
            // shares a cell with one of those is outside what this index covers.
            List<Cell> placed = randomCells(random, 200, true);
            for (Cell box : placed) {
                tree.addBox(box.box());
            }
            for (Cell probe : randomCells(random, 200, true)) {
                // The tree reports overlap when the probe reaches the region, the stored piece
                // reaches it, and the two share a cell. Both sides have to be indexed, so a
                // probe or a piece sitting wholly outside the region is out of scope either way.
                boolean expected = false;
                if (probe.reachesRegion()) {
                    for (Cell box : placed) {
                        if (box.reachesRegion() && probe.sharesCell(box)) {
                            expected = true;
                            break;
                        }
                    }
                }
                assertEquals(expected,
                    tree.intersectsAnyBox(probe.minX(), probe.minY(), probe.minZ(),
                        probe.maxX(), probe.maxY(), probe.maxZ()),
                    "seed " + seed + " probe " + probe);
            }
        }
    }

    @Test
    @DisplayName("a probe or a piece entirely outside the region is not the tree's business")
    void boxesOutsideTheRegionAreNotIndexed() {
        BoxOctree tree = new BoxOctree(region());
        Cell outside = new Cell(REGION_MIN - 8, 0, 0, REGION_MIN - 4, 3, 3);
        assertFalse(outside.reachesRegion());
        tree.addBox(outside.box());
        assertFalse(tree.intersectsAnyBox(outside.minX(), outside.minY(), outside.minZ(),
            outside.maxX(), outside.maxY(), outside.maxZ()));
        // A piece that does reach the region is still found, on both sides of the edge.
        Cell hanging = new Cell(REGION_MIN - 4, 0, 0, REGION_MIN + 4, 3, 3);
        assertTrue(hanging.reachesRegion());
        tree.addBox(hanging.box());
        assertTrue(tree.intersectsAnyBox(REGION_MIN - 2, 0, 0, REGION_MIN + 2, 3, 3));
        assertTrue(tree.intersectsAnyBox(REGION_MIN + 2, 0, 0, REGION_MIN + 6, 3, 3));
    }

    @Test
    @DisplayName("a candidate that hangs over the region edge is rejected, like vanilla")
    void candidateOutsideRegionIsRejected() {
        BoxOctree tree = new BoxOctree(region());
        assertTrue(tree.hasRoomFor(new BoundingBox(0, 0, 0, 3, 3, 3)));
        assertFalse(tree.hasRoomFor(new BoundingBox(REGION_MIN - 1, 0, 0, 2, 2, 2)));
        assertFalse(tree.hasRoomFor(new BoundingBox(0, REGION_MIN - 1, 0, 2, 2, 2)));
        assertFalse(tree.hasRoomFor(new BoundingBox(0, 0, REGION_MIN - 1, 2, 2, 2)));
        assertFalse(tree.hasRoomFor(new BoundingBox(REGION_MAX, 0, 0, REGION_MAX + 3, 3, 3)));
        assertFalse(tree.hasRoomFor(new BoundingBox(0, REGION_MAX, 0, 3, REGION_MAX + 3, 3)));
    }

    @Test
    @DisplayName("the 0.25 deflate keeps face-adjacent pieces apart and merges ones that share a cell")
    void deflateSeparatesFaceAdjacentPieces() {
        BoxOctree tree = new BoxOctree(region());
        tree.addBox(new BoundingBox(0, 0, 0, 3, 3, 3));
        assertTrue(tree.hasRoomFor(new BoundingBox(4, 0, 0, 7, 3, 3)), "one cell along x");
        assertTrue(tree.hasRoomFor(new BoundingBox(-4, 0, 0, -1, 3, 3)), "one cell back along x");
        assertTrue(tree.hasRoomFor(new BoundingBox(0, 4, 0, 3, 7, 3)), "one cell up");
        assertTrue(tree.hasRoomFor(new BoundingBox(0, 0, 4, 3, 3, 7)), "one cell along z");
        assertFalse(tree.hasRoomFor(new BoundingBox(3, 0, 0, 6, 3, 3)), "shares a cell along x");
        assertFalse(tree.hasRoomFor(new BoundingBox(0, 3, 0, 3, 6, 3)), "shares a cell up");
        assertFalse(tree.hasRoomFor(new BoundingBox(0, 0, 0, 3, 3, 3)), "the box that is already there");
    }

    @Test
    @DisplayName("inserting the same box twice changes nothing")
    void duplicateInsertionIsIdempotent() {
        BoxOctree tree = new BoxOctree(region());
        BoundingBox box = new BoundingBox(10, 10, 10, 20, 20, 20);
        for (int i = 0; i < 5; i++) {
            tree.addBox(box);
        }
        assertFalse(tree.hasRoomFor(box));
        assertFalse(tree.hasRoomFor(new BoundingBox(15, 15, 15, 25, 25, 25)));
        assertTrue(tree.hasRoomFor(new BoundingBox(21, 21, 21, 25, 25, 25)));
    }

    @Test
    @DisplayName("a degenerate query box intersects nothing")
    void degenerateQueryIntersectsNothing() {
        BoxOctree tree = new BoxOctree(region());
        tree.addBox(new BoundingBox(0, 0, 0, 8, 8, 8));
        assertFalse(tree.intersectsAnyBox(4.0D, 4.0D, 4.0D, 4.0D, 8.0D, 8.0D));
        assertTrue(tree.intersectsAnyBox(4.0D, 4.0D, 4.0D, 5.0D, 8.0D, 8.0D));
    }

    @Test
    @DisplayName("the boundary answer comes from the region the tree was built around")
    void boundaryContainment() {
        BoxOctree tree = new BoxOctree(region());
        assertTrue(tree.boundaryContains(0, 0, 0));
        assertTrue(tree.boundaryContains(new BlockPos(REGION_MAX - 1, REGION_MAX - 1, REGION_MAX - 1)));
        assertFalse(tree.boundaryContains(REGION_MAX, 0, 0));
        assertFalse(tree.boundaryContains(REGION_MIN - 1, 0, 0));
    }

    @Test
    @DisplayName("withinAnyBox finds every piece that was inserted, including past subdivisions")
    void withinAnyBoxFindsEveryPiece() {
        for (int seed = 0; seed < 4; seed++) {
            BoxOctree tree = new BoxOctree(region());
            List<Cell> cells = randomCells(new Random(200 + seed), 300, true);
            for (Cell cell : cells) {
                tree.addBox(cell.box());
            }
            for (Cell cell : cells) {
                // A piece hanging over the edge is only indexed where it reaches the region, so its
                // outer corner is genuinely not in the tree. Everything the region covers is.
                assertCornerFound(tree, cell.minCorner(), cell, "min corner");
                assertCornerFound(tree, cell.maxCorner(), cell, "max corner");
                BlockPos inside = cell.interiorOfRegion();
                if (inside != null) {
                    assertTrue(tree.withinAnyBox(inside),
                        "seed " + seed + " interior of " + cell + " at " + inside);
                }
            }
        }
    }

    private static void assertCornerFound(BoxOctree tree, BlockPos corner, Cell cell, String which) {
        if (corner.getX() < REGION_MIN || corner.getY() < REGION_MIN || corner.getZ() < REGION_MIN
            || corner.getX() >= REGION_MAX || corner.getY() >= REGION_MAX || corner.getZ() >= REGION_MAX) {
            return;
        }
        assertTrue(tree.withinAnyBox(corner), "seed cell " + cell + " " + which + " at " + corner);
    }
}