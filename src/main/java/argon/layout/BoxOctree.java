package argon.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

/**
 * Nearby-only piece bounds checks for jigsaw layout.
 *
 * <p>The tree is built around one region and answers for that region. A box is stored in every
 * octant it reaches, and an octant only exists once some box reaches it, so a box that never
 * reaches the region is not kept at all. A query therefore reports overlap exactly when the query
 * reaches the region, the stored box reaches it, and the two share a cell. Boxes that both sit
 * outside the region can overlap each other and are still not reported, which is the point: the
 * layout only ever asks about pieces inside the region, and a box that cannot reach it can never
 * overlap one that can.
 *
 * <p>Two things keep the hot path cheap. Leaf boxes are held as six doubles in one flat array
 * rather than as AABB objects, so a scan walks contiguous memory and never dereferences a box.
 * And an octant is only created once some box actually reaches it, so the empty octants a
 * subdivision would otherwise reserve cost neither an object nor a boundary test per query.
 */
public final class BoxOctree {
    private static final int SUBDIVIDE_THRESHOLD = 16;
    private static final int MAXIMUM_DEPTH = 5;
    private static final int INITIAL_LEAF = 4;
    private static final int STRIDE = 6;
    private static final int OCTANTS = 8;

    private final double minX, minY, minZ, maxX, maxY, maxZ;
    private final int sizeX, sizeY, sizeZ;
    private final int depth;
    private double[] inner;
    private int innerCount;
    private BoxOctree[] children;
    private double[] octantBounds;

    public BoxOctree(@NonNull AABB axisAlignedBB) {
        this(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ,
            axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ, 0);
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
            && sizeX >= 2
            && sizeY >= 2
            && sizeZ >= 2;
    }

    private void subdivide() {
        if (children != null) {
            throw new UnsupportedOperationException("argon tried to subdivide a box octree that already has children");
        }
        int halfX = sizeX / 2;
        int halfY = sizeY / 2;
        int halfZ = sizeZ / 2;
        children = new BoxOctree[OCTANTS];
        octantBounds = new double[OCTANTS * STRIDE];
        for (int i = 0; i < OCTANTS; i++) {
            double x0 = (i & 1) == 0 ? minX : minX + halfX;
            double x1 = (i & 1) == 0 ? minX + halfX : maxX;
            double y0 = (i & 2) == 0 ? minY : minY + halfY;
            double y1 = (i & 2) == 0 ? minY + halfY : maxY;
            double z0 = (i & 4) == 0 ? minZ : minZ + halfZ;
            double z1 = (i & 4) == 0 ? minZ + halfZ : maxZ;
            int base = i * STRIDE;
            octantBounds[base] = x0;
            octantBounds[base + 1] = y0;
            octantBounds[base + 2] = z0;
            octantBounds[base + 3] = x1;
            octantBounds[base + 4] = y1;
            octantBounds[base + 5] = z1;
        }
        pushLeavesToOctants();
    }

    private void pushLeavesToOctants() {
        for (int i = 0; i < innerCount * STRIDE; i += STRIDE) {
            for (int octant = 0; octant < OCTANTS; octant++) {
                if (octantReaches(octant, inner[i], inner[i + 1], inner[i + 2], inner[i + 3], inner[i + 4], inner[i + 5])) {
                    octant(octant).addBox(inner[i], inner[i + 1], inner[i + 2], inner[i + 3], inner[i + 4], inner[i + 5]);
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
        addBox(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }

    private void addBox(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        // A box that never reaches this node cannot be found through it: a query only descends
        // into a child whose boundary it reaches, and a subdivision would push the box into no
        // octant at all. Refusing it here keeps the answer the same whether or not the node has
        // subdivided yet, instead of making the result depend on how full the tree happens to be.
        if (!intersectsBoundary(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
            return;
        }
        if (canSubdivide()) {
            subdivide();
        }
        if (children != null) {
            for (int octant = 0; octant < OCTANTS; octant++) {
                if (octantReaches(octant, oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
                    octant(octant).addBox(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ);
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

    private boolean octantReaches(int octant, double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        int base = octant * STRIDE;
        return spans(octantBounds[base], octantBounds[base + 3], oMinX, oMaxX)
            && spans(octantBounds[base + 1], octantBounds[base + 4], oMinY, oMaxY)
            && spans(octantBounds[base + 2], octantBounds[base + 5], oMinZ, oMaxZ);
    }

    private BoxOctree octant(int octant) {
        BoxOctree child = children[octant];
        if (child != null) {
            return child;
        }
        int base = octant * STRIDE;
        child = new BoxOctree(
            octantBounds[base], octantBounds[base + 1], octantBounds[base + 2],
            octantBounds[base + 3], octantBounds[base + 4], octantBounds[base + 5],
            depth
        );
        children[octant] = child;
        return child;
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
        double qMaxX = box.maxX() + 1 - deflate;
        double qMaxY = box.maxY() + 1 - deflate;
        double qMaxZ = box.maxZ() + 1 - deflate;
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
            return octantsIntersect(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ);
        }
        return leavesIntersect(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ);
    }

    private boolean octantsIntersect(double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY, double oMaxZ) {
        for (int octant = 0; octant < OCTANTS; octant++) {
            BoxOctree child = children[octant];
            if (child != null
                && child.intersectsBoundary(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)
                && child.intersectsAnyBox(oMinX, oMinY, oMinZ, oMaxX, oMaxY, oMaxZ)) {
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
            for (int octant = 0; octant < OCTANTS; octant++) {
                BoxOctree child = children[octant];
                if (child != null && child.boundaryContains(x, y, z) && child.withinAnyBox(x, y, z)) {
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
}
