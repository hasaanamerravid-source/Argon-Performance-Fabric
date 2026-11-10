package argon.layout;

/**
 * Vanilla stores the full piece box and tests overlap on a 0.25-deflated copy.
 * The octree reads the two shapes straight off the BoundingBox, so the value lives here and no
 * AABB is built for either one.
 */
public final class PieceBoxes {
    public static final double OVERLAP_DEFLATE = 0.25D;

    private PieceBoxes() {
    }
}
