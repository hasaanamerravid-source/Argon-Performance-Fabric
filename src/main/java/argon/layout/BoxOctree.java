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
 * <p>Two things keep the hot path cheap. Leaf boxes are held as six ints in one flat array rather
 * than as AABB objects or doubles, so a scan walks half the memory and completes in integer
 * registers. And a child is only created once some box actually reaches it, so the empty children a
 * subdivision would otherwise reserve cost neither an object nor a boundary test per query.
 * Subdivision splits only the axes that are at least two blocks wide, so a flat or rod-shaped
 * region still gets a tree instead of a single leaf that every query scans linearly.
 *
 * <p>Piece boxes are integer block bounds. The stored maximum is inclusive, which keeps a piece at
 * {@link Integer#MAX_VALUE} representable, and lets the query compare the candidate's own ints
 * against the stored ones. The quarter-block deflate vanilla applies only matters between boxes
 * that share a cell, and for integer bounds that is exactly the inclusive test: every deflate
 * strictly between zero and one block gives the same answer.
 */
public final class BoxOctree {
    private static final int SUBDIVIDE_AT = 16;
    private static final int MAXIMUM_DEPTH = 5;
    private static final int INITIAL_LEAF = 4;
    private static final int STRIDE = 6;

    private final double minX, minY, minZ, maxX, maxY, maxZ;
    private final int sizeX, sizeY, sizeZ;
    private final int depth;
    private int[] inner;
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
        this.inner = new int[INITIAL_LEAF * STRIDE];
    }

    private static int roundAwayFromZero(double value) {
        return value >= 0.0D ? (int) Math.ceil(value) : (int) Math.floor(value);
    }

    private static boolean spans(double aMin, double aMax, double bMin, double bMax) {
        return aMin < bMax && aMax > bMin;
    }

    /** Whether this node's half-open boundary overlaps a half-open box. */
    private boolean overlaps(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        return spans(minX, maxX, oMinX, oMaxX)
            && spans(minY, maxY, oMinY, oMaxY)
            && spans(minZ, maxZ, oMinZ, oMaxZ);
    }

    /**
     * Whether this node's half-open boundary shares a cell with an inclusive block box. The
     * maximum is inclusive, so its half-open edge is {@code max + 1}; the addition is done in
     * double so a box at {@link Integer#MAX_VALUE} does not wrap.
     */
    private boolean reaches(int oMinX, int oMinY, int oMinZ, int oMaxX, int oMaxY, int oMaxZ) {
        return minX < oMaxX + 1.0D && maxX > oMinX
            && minY < oMaxY + 1.0D && maxY > oMinY
            && minZ < oMaxZ + 1.0D && maxZ > oMinZ;
    }

    private boolean canSubdivide() {
        // Subdivide before the leaf fills the array it would grow next. Otherwise the seventeenth
        // box doubles a leaf that is about to be replaced by children and throws the copy away.
        return children == null
            && depth < MAXIMUM_DEPTH
            && innerCount >= SUBDIVIDE_AT
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
        // One slot per branching axis combination, not a fixed eight: a one- or two-axis split
        // does not reserve slots it can never fill or walk them on every query.
        children = new BoxOctree[childCount];
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
     * Piece bounds as the overlap test wants them. The six values are the piece's own ints, with
     * the maximum inclusive, so nothing is converted and the coordinate limit never has to be
     * widened.
     */
    public void addBox(@NonNull BoundingBox box) {
        addBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    private void addBox(int oMinX, int oMinY, int oMinZ, int oMaxX, int oMaxY, int oMaxZ) {
        // A box that never reaches this node cannot be found through it: a query only descends
        // into a child whose boundary it reaches, and a subdivision would push the box into no
        // child at all. Refusing it here keeps the answer the same whether or not the node has
        // subdivided yet, instead of making the result depend on how full the tree happens to be.
        if (!reaches(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
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

    private boolean childReaches(int child, int oMinX, int oMinY, int oMinZ, int oMaxX, int oMaxY, int oMaxZ) {
        int base = child * STRIDE;
        return childBounds[base] < oMaxX + 1.0D && childBounds[base + 3] > oMinX
            && childBounds[base + 1] < oMaxY + 1.0D && childBounds[base + 4] > oMinY
            && childBounds[base + 2] < oMaxZ + 1.0D && childBounds[base + 5] > oMinZ;
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

    private boolean alreadyHas(int oMinX, int oMinY, int oMinZ, int oMaxX, int oMaxY, int oMaxZ) {
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
        int[] grown = new int[inner.length * 2];
        System.arraycopy(inner, 0, grown, 0, innerCount * STRIDE);
        inner = grown;
    }

    /**
     * Whether the candidate has room: its 0.25-deflated box lands inside the free region and misses
     * every piece already in the tree. This is the accept case, so callers that stand in for a
     * reject predicate negate it.
     *
     * <p>The deflated candidate is tested against the region, then the candidate's own ints are
     * tested against the stored ones. The deflate only shrinks each face by a quarter of a block,
     * and both boxes are integer aligned, so two boxes share a cell exactly when their inclusive
     * ranges overlap. That removes the deflated query doubles and the six additions that built
     * them from the leaf scan.
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
            return !intersectsAnyPiece(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        }
        return false;
    }

    /** The inclusive-block form of the query, used for every piece test the layout makes. */
    private boolean intersectsAnyPiece(int oMinX, int oMinY, int oMinZ, int oMaxX, int oMaxY, int oMaxZ) {
        if (children != null) {
            for (int child = 0; child < childCount; child++) {
                BoxOctree node = children[child];
                if (node != null
                    && node.reaches(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)
                    && node.intersectsAnyPiece(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < innerCount; i++) {
            int base = i * STRIDE;
            if (oMinX <= inner[base + 3] && oMaxX >= inner[base]
                && oMinY <= inner[base + 4] && oMaxY >= inner[base + 1]
                && oMinZ <= inner[base + 5] && oMaxZ >= inner[base + 2]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a half-open double box overlaps any stored piece. The layout itself uses
     * {@link #hasRoomFor(BoundingBox)}; this is the general form and keeps the tree answerable for
     * callers and tests that carry their own coordinates.
     */
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
                && node.overlaps(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)
                && node.intersectsAnyBox(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
                return true;
            }
        }
        return false;
    }

    private boolean leavesIntersect(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        for (int i = 0; i < innerCount; i++) {
            int base = i * STRIDE;
            if (oMinX < inner[base + 3] + 1.0D && oMaxX > inner[base]
                && oMinY < inner[base + 4] + 1.0D && oMaxY > inner[base + 1]
                && oMinZ < inner[base + 5] + 1.0D && oMaxZ > inner[base + 2]) {
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
            if (x >= inner[base] && x <= inner[base + 3]
                && y >= inner[base + 1] && y <= inner[base + 4]
                && z >= inner[base + 2] && z <= inner[base + 5]) {
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
