package argon.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

/**
 * Nearby-only piece bounds checks for jigsaw layout.
 *
 * <p>The tree is built around one region and answers for that region. A box is stored in every
 * child node it reaches, and a child only exists once some box reaches it, so a box that never
 * reaches the region is not kept at all. A query therefore reports overlap exactly when the query
 * reaches the region, the stored box reaches it, and the two share a cell. Boxes that both sit
 * outside the region can overlap each other and are still not reported, which is the point: the
 * layout only ever asks about pieces inside the region, and a box that cannot reach it can never
 * overlap one that can.
 *
 * <p>Two things keep the hot path cheap. Leaf boxes are held as six doubles in one flat array
 * rather than as AABB objects, so a scan walks contiguous memory and never dereferences a box.
 * And a child is only created once some box actually reaches it, so the empty children a
 * subdivision would otherwise reserve cost neither an object nor a boundary test per query.
 * Subdivision splits only the axes that are at least two blocks wide, so a flat or rod-shaped
 * region still gets a tree instead of a single leaf that every query scans linearly.
 */
public final class BoxOctree {
    private static final int SUBDIVIDE_THRESHOLD = 16;
    private static final int MAXIMUM_DEPTH = 5;
    private static final int INITIAL_LEAF = 4;
    private static final int STRIDE = 6;
    private static final int MAX_CHILDREN = 8;

    private final double minX, minY, minZ, maxX, maxY, maxZ;
    private final int sizeX, sizeY, sizeZ;
    private final int depth;
    private double[] inner;
    private int innerCount;
    private BoxOctree[] children;
    private int childCount;
    private double[] childBounds;

    public BoxOctree(@NonNull AABB axisAlignedBB) {
        // The root is depth zero, so MAXIMUM_DEPTH is the number of subdivision levels that are
        // actually available rather than one fewer.
        this(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ,
            axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ, -1);
    }

    private BoxOctree(double minX, double minY, double minZ,
                      double maxX, double maxY, double maxZ, int parentDepth) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.sizeX = roundAwayFromZero(maxX - minX);
        this.sizeY = roundAwayFromZero(maxY - minY);
        this.sizeZ = roundAwayFromZero(maxZ - minZ);
        this.depth = parentDepth + 1;
        this.inner = new double[INITIAL_LEAF * STRIDE];
    }

    private static int roundAwayFromZero(double value) {
        return value >= 0.0D ? (int) Math.ceil(value) : (int) Math.floor(value);
    }

    private static boolean spans(double aMin, double aMax, double bMin, double bMax) {
        return aMin < bMax && aMax > bMin;
    }

    private boolean intersectsBoundary(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        return spans(minX, maxX, oMinX, oMaxX)
            && spans(minY, maxY, oMinY, oMaxY)
            && spans(minZ, maxZ, oMinZ, oMaxZ);
    }

    private boolean canSubdivide() {
        return children == null
            && depth < MAXIMUM_DEPTH
            && innerCount > SUBDIVIDE_THRESHOLD
            // A thin region can still be split along the axes that have room. Requiring all three
            // axes to be at least two blocks used to leave a flat structure with a single leaf and
            // a linear scan over every piece.
            && (sizeX >= 2 || sizeY >= 2 || sizeZ >= 2);
    }

    private void subdivide() {
        if (children != null) {
            throw new UnsupportedOperationException("argon tried to subdivide a box octree that already has children");
        }
        int halfX = sizeX / 2;
        int halfY = sizeY / 2;
        int halfZ = sizeZ / 2;
        boolean splitX = sizeX >= 2;
        boolean splitY = sizeY >= 2;
        boolean splitZ = sizeZ >= 2;
        int stepsX = splitX ? 2 : 1;
        int stepsY = splitY ? 2 : 1;
        int stepsZ = splitZ ? 2 : 1;
        childCount = stepsX * stepsY * stepsZ;
        children = new BoxOctree[MAX_CHILDREN];
        childBounds = new double[childCount * STRIDE];
        int child = 0;
        for (int x = 0; x < stepsX; x++) {
            double x0 = splitX && x != 0 ? minX + halfX : minX;
            double x1 = splitX && x == 0 ? minX + halfX : maxX;
            for (int y = 0; y < stepsY; y++) {
                double y0 = splitY && y != 0 ? minY + halfY : minY;
                double y1 = splitY && y == 0 ? minY + halfY : maxY;
                for (int z = 0; z < stepsZ; z++) {
                    double z0 = splitZ && z != 0 ? minZ + halfZ : minZ;
                    double z1 = splitZ && z == 0 ? minZ + halfZ : maxZ;
                    int base = child * STRIDE;
                    childBounds[base] = x0;
                    childBounds[base + 1] = y0;
                    childBounds[base + 2] = z0;
                    childBounds[base + 3] = x1;
                    childBounds[base + 4] = y1;
                    childBounds[base + 5] = z1;
                    child++;
                }
            }
        }
        pushLeavesToChildren();
    }

    private void pushLeavesToChildren() {
        for (int i = 0; i < innerCount * STRIDE; i += STRIDE) {
            for (int child = 0; child < childCount; child++) {
                if (childReaches(child, inner[i], inner[i + 1], inner[i + 2], inner[i + 3], inner[i + 4], inner[i + 5])) {
                    child(child).addBox(inner[i], inner[i + 1], inner[i + 2], inner[i + 3], inner[i + 4], inner[i + 5]);
                }
            }
        }
        inner = null;
        innerCount = 0;
    }

    /**
     * Piece bounds as the overlap test wants them. Reads the six values straight out of the
     * BoundingBox, so nothing is allocated for the box that lands in the tree.
     */
    public void addBox(@NonNull BoundingBox box) {
        // Widening before the +1 matters at Integer.MAX_VALUE: an int addition wraps negative there
        // and the box is then rejected as if it did not reach the region at all.
        addBox(box.minX(), box.minY(), box.minZ(),
            box.maxX() + 1.0D, box.maxY() + 1.0D, box.maxZ() + 1.0D);
    }

    private void addBox(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        // A box that never reaches this node cannot be found through it: a query only descends
        // into a child whose boundary it reaches, and a subdivision would push the box into no
        // child at all. Refusing it here keeps the answer the same whether or not the node has
        // subdivided yet, instead of making the result depend on how full the tree happens to be.
        if (!intersectsBoundary(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
            return;
        }
        if (canSubdivide()) {
            subdivide();
        }
        if (children != null) {
            for (int child = 0; child < childCount; child++) {
                if (childReaches(child, oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
                    child(child).addBox(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ);
                }
            }
            return;
        }
        if (alreadyHas(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
            return;
        }
        ensureLeafCapacity();
        int base = innerCount * STRIDE;
        inner[base] = oMinX;
        inner[base + 1] = oMinY;
        inner[base + 2] = oMinZ;
        inner[base + 3] = oMaxX;
        inner[base + 4] = oMaxY;
        inner[base + 5] = oMaxZ;
        innerCount++;
    }

    private boolean childReaches(int child, double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        int base = child * STRIDE;
        return spans(childBounds[base], childBounds[base + 3], oMinX, oMaxX)
            && spans(childBounds[base + 1], childBounds[base + 4], oMinY, oMaxY)
            && spans(childBounds[base + 2], childBounds[base + 5], oMinZ, oMaxZ);
    }

    private BoxOctree child(int child) {
        BoxOctree node = children[child];
        if (node != null) {
            return node;
        }
        int base = child * STRIDE;
        node = new BoxOctree(
            childBounds[base], childBounds[base + 1], childBounds[base + 2],
            childBounds[base + 3], childBounds[base + 4], childBounds[base + 5],
            depth
        );
        children[child] = node;
        return node;
    }

    private boolean alreadyHas(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        for (int i = 0; i < innerCount; i++) {
            int base = i * STRIDE;
            if (inner[base] == oMinX && inner[base + 1] == oMinY && inner[base + 2] == oMinZ
                && inner[base + 3] == oMaxX && inner[base + 4] == oMaxY && inner[base + 5] == oMaxZ) {
                return true;
            }
        }
        return false;
    }

    private void ensureLeafCapacity() {
        if ((innerCount + 1) * STRIDE <= inner.length) {
            return;
        }
        double[] grown = new double[inner.length * 2];
        System.arraycopy(inner, 0, grown, 0, innerCount * STRIDE);
        inner = grown;
    }

    /**
     * Whether the 0.25-deflated piece box has room: it lands inside the free region and misses
     * every piece already in the tree. This is the accept case, so callers that stand in for a
     * reject predicate negate it. Takes the BoundingBox so the deflated query box never has to be
     * built as an AABB.
     */
    public boolean hasRoomFor(@NonNull BoundingBox box) {
        double deflate = PieceBoxes.OVERLAP_DEFLATE;
        double qMinX = box.minX() + deflate;
        double qMinY = box.minY() + deflate;
        double qMinZ = box.minZ() + deflate;
        double qMaxX = box.maxX() + 1.0D - deflate;
        double qMaxY = box.maxY() + 1.0D - deflate;
        double qMaxZ = box.maxZ() + 1.0D - deflate;
        if (minX <= qMinX && maxX >= qMaxX
            && minY <= qMinY && maxY >= qMaxY
            && minZ <= qMinZ && maxZ >= qMaxZ) {
            return !intersectsAnyBox(qMinX, qMinY, qMinZ, qMaxX, qMaxY, qMaxZ);
        }
        return false;
    }

    public boolean intersectsAnyBox(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        if (oMaxX <= oMinX || oMaxY <= oMinY || oMaxZ <= oMinZ) {
            return false;
        }
        if (children != null) {
            return childrenIntersect(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ);
        }
        return leavesIntersect(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ);
    }

    private boolean childrenIntersect(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        for (int child = 0; child < childCount; child++) {
            BoxOctree node = children[child];
            if (node != null
                && node.intersectsBoundary(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)
                && node.intersectsAnyBox(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
                return true;
            }
        }
        return false;
    }

    private boolean leavesIntersect(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        for (int i = 0; i < innerCount; i++) {
            int base = i * STRIDE;
            if (spans(inner[base], inner[base + 3], oMinX, oMaxX)
                && spans(inner[base + 1], inner[base + 4], oMinY, oMaxY)
                && spans(inner[base + 2], inner[base + 5], oMinZ, oMaxZ)) {
                return true;
            }
        }
        return false;
    }

    public boolean boundaryContains(int x, int y, int z) {
        return x >= minX && x < maxX
            && y >= minY && y < maxY
            && z >= minZ && z < maxZ;
    }

    public boolean boundaryContains(@NonNull BlockPos position) {
        return boundaryContains(position.getX(), position.getY(), position.getZ());
    }

    public boolean withinAnyBox(@NonNull BlockPos position) {
        return withinAnyBox(position.getX(), position.getY(), position.getZ());
    }

    private boolean withinAnyBox(int x, int y, int z) {
        if (children != null) {
            for (int child = 0; child < childCount; child++) {
                BoxOctree node = children[child];
                if (node != null && node.boundaryContains(x, y, z) && node.withinAnyBox(x, y, z)) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < innerCount; i++) {
            int base = i * STRIDE;
            if (x >= inner[base] && x < inner[base + 3]
                && y >= inner[base + 1] && y < inner[base + 4]
                && z >= inner[base + 2] && z < inner[base + 5]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deepest node currently in the tree. Package-private so the tests can prove that a region
     * which is only one block wide on one axis still subdivides instead of staying a single leaf.
     */
    int depthReached() {
        int deepest = depth;
        if (children != null) {
            for (int child = 0; child < childCount; child++) {
                BoxOctree node = children[child];
                if (node != null) {
                    deepest = Math.max(deepest, node.depthReached());
                }
            }
        }
        return deepest;
    }
}
