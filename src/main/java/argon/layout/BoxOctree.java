package argon.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Nearby-only piece bounds checks for jigsaw layout.
 * Intersection results match a list of AABBs; the tree only skips boxes that cannot overlap.
 * Adapted from the MIT-licensed BoxOctree in TelepathicGrunt's Structure Layout Optimizer.
 */
public final class BoxOctree {
    private static final int SUBDIVIDE_THRESHOLD = 10;
    private static final int MAXIMUM_DEPTH = 3;

    private final AABB boundary;
    private final Vec3i size;
    private final int depth;
    private final List<AABB> innerBoxes = new ArrayList<>();
    private final List<BoxOctree> children = new ArrayList<>();

    public BoxOctree(AABB axisAlignedBB) {
        this(axisAlignedBB, 0);
    }

    private BoxOctree(AABB axisAlignedBB, int parentDepth) {
        this.boundary = axisAlignedBB.move(0.0D, 0.0D, 0.0D);
        this.size = new Vec3i(roundAwayFromZero(boundary.getXsize()), roundAwayFromZero(boundary.getYsize()), roundAwayFromZero(boundary.getZsize()));
        this.depth = parentDepth + 1;
    }

    private static int roundAwayFromZero(double value) {
        return value >= 0.0D ? (int) Math.ceil(value) : (int) Math.floor(value);
    }

    private void subdivide() {
        if (!children.isEmpty()) {
            throw new UnsupportedOperationException("argon tried to subdivide a box octree that already has children");
        }
        int halfX = size.getX() / 2;
        int halfY = size.getY() / 2;
        int halfZ = size.getZ() / 2;
        children.add(new BoxOctree(new AABB(boundary.minX, boundary.minY, boundary.minZ, boundary.minX + halfX, boundary.minY + halfY, boundary.minZ + halfZ), depth));
        children.add(new BoxOctree(new AABB(boundary.minX, boundary.minY, boundary.minZ + halfZ, boundary.minX + halfX, boundary.minY + halfY, boundary.maxZ), depth));
        children.add(new BoxOctree(new AABB(boundary.minX + halfX, boundary.minY, boundary.minZ, boundary.maxX, boundary.minY + halfY, boundary.minZ + halfZ), depth));
        children.add(new BoxOctree(new AABB(boundary.minX + halfX, boundary.minY, boundary.minZ + halfZ, boundary.maxX, boundary.minY + halfY, boundary.maxZ), depth));
        children.add(new BoxOctree(new AABB(boundary.minX, boundary.minY + halfY, boundary.minZ, boundary.minX + halfX, boundary.maxY, boundary.minZ + halfZ), depth));
        children.add(new BoxOctree(new AABB(boundary.minX, boundary.minY + halfY, boundary.minZ + halfZ, boundary.minX + halfX, boundary.maxY, boundary.maxZ), depth));
        children.add(new BoxOctree(new AABB(boundary.minX + halfX, boundary.minY + halfY, boundary.minZ, boundary.maxX, boundary.maxY, boundary.minZ + halfZ), depth));
        children.add(new BoxOctree(new AABB(boundary.minX + halfX, boundary.minY + halfY, boundary.minZ + halfZ, boundary.maxX, boundary.maxY, boundary.maxZ), depth));
        for (AABB box : innerBoxes) {
            for (BoxOctree child : children) {
                if (child.boundary.intersects(box)) {
                    child.addBox(box);
                }
            }
        }
        innerBoxes.clear();
    }

    public void addBox(AABB box) {
        if (depth < MAXIMUM_DEPTH && innerBoxes.size() > SUBDIVIDE_THRESHOLD) {
            subdivide();
        }
        if (!children.isEmpty()) {
            for (BoxOctree child : children) {
                if (child.boundary.intersects(box)) {
                    child.addBox(box);
                }
            }
            return;
        }
        for (AABB existing : innerBoxes) {
            if (existing.equals(box)) {
                return;
            }
        }
        innerBoxes.add(box);
    }

    public boolean boundaryEntirelyContains(AABB box) {
        return boundary.contains(box.minX, box.minY, box.minZ) && boundary.contains(box.maxX, box.maxY, box.maxZ);
    }

    public boolean withinBoundsButNotIntersectingChildren(AABB box) {
        return boundaryEntirelyContains(box) && !intersectsAnyBox(box);
    }

    public boolean intersectsAnyBox(AABB box) {
        if (!children.isEmpty()) {
            for (BoxOctree child : children) {
                if (child.boundary.intersects(box) && child.intersectsAnyBox(box)) {
                    return true;
                }
            }
            return false;
        }
        for (AABB inner : innerBoxes) {
            if (inner.intersects(box)) {
                return true;
            }
        }
        return false;
    }

    public boolean boundaryContains(BlockPos position) {
        return boundary.contains(position.getX(), position.getY(), position.getZ());
    }

    public boolean withinAnyBox(BlockPos position) {
        if (!children.isEmpty()) {
            for (BoxOctree child : children) {
                if (child.boundaryContains(position) && child.withinAnyBox(position)) {
                    return true;
                }
            }
            return false;
        }
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        for (AABB inner : innerBoxes) {
            if (inner.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
