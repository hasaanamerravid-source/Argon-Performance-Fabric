package argon.util;

/**
 * Integer helpers for jigsaw layout.
 *
 * <p>Comparisons must survive the full {@code int} range. The tempting one-liner
 * {@code (a - b) >>> 31} does not: the subtraction wraps, so {@code MAX_VALUE} reads as smaller
 * than {@code MIN_VALUE} once the true distance exceeds {@code Integer.MAX_VALUE}, which is exactly
 * the case for structure coordinates. Ordering here delegates to {@link Integer#compare}, which is
 * defined for every pair of ints.
 *
 * <p>The mask methods return all-ones or zero so a caller can feed {@link #select} and turn a
 * branch into one bitwise pick. The division helpers exist because block coordinates run negative
 * and {@code /} truncates toward zero instead of rounding down.
 */
public final class Arithmetic {
    private static final int ALL_ONES = -1;
    private static final int ZERO = 0;
    private static final int ONE = 1;

    private Arithmetic() {
    }

    /**
     * Negative when a sorts before b, zero when equal, positive when after, without subtracting
     * the two values.
     */
    public static int order(int a, int b) {
        return Integer.compare(a, b);
    }

    /** Ordering by unsigned value, for packed block state longs where the top bit is a flag. */
    public static int orderUnsigned(int a, int b) {
        return Integer.compareUnsigned(a, b);
    }

    public static boolean lessThan(int a, int b) {
        return a < b;
    }

    public static boolean lessThanOrEqual(int a, int b) {
        return a <= b;
    }

    public static boolean greaterThan(int a, int b) {
        return a > b;
    }

    public static boolean greaterThanOrEqual(int a, int b) {
        return a >= b;
    }

    public static boolean equalTo(int a, int b) {
        return a == b;
    }

    public static boolean notEqualTo(int a, int b) {
        return a != b;
    }

    /** All-ones when a sorts before b, otherwise zero. */
    public static int maskLessThan(int a, int b) {
        return a < b ? ALL_ONES : ZERO;
    }

    /** All-ones when a sorts at or before b, otherwise zero. */
    public static int maskLessThanOrEqual(int a, int b) {
        return a <= b ? ALL_ONES : ZERO;
    }

    /** All-ones when a sorts after b, otherwise zero. */
    public static int maskGreaterThan(int a, int b) {
        return a > b ? ALL_ONES : ZERO;
    }

    /** All-ones when a sorts at or after b, otherwise zero. */
    public static int maskGreaterThanOrEqual(int a, int b) {
        return a >= b ? ALL_ONES : ZERO;
    }

    /** All-ones when a and b are equal, otherwise zero. */
    public static int maskEqualTo(int a, int b) {
        return a == b ? ALL_ONES : ZERO;
    }

    /** All-ones when a and b differ, otherwise zero. */
    public static int maskNotEqualTo(int a, int b) {
        return a != b ? ALL_ONES : ZERO;
    }

    /** Branchless pick driven by a mask from one of the mask methods. */
    public static int select(int mask, int whenSet, int whenClear) {
        return whenClear ^ ((whenClear ^ whenSet) & mask);
    }

    public static int min(int a, int b) {
        return select(maskLessThan(a, b), a, b);
    }

    public static int max(int a, int b) {
        return select(maskGreaterThan(a, b), a, b);
    }

    /** Requires min <= max. */
    public static int clamp(int value, int min, int max) {
        return select(maskLessThan(value, min), min, select(maskGreaterThan(value, max), max, value));
    }

    /** Rounds towards negative infinity. Divisor must not be zero. */
    public static int floorDiv(int a, int b) {
        int quotient = a / b;
        int remainder = a % b;
        return remainder != ZERO && (remainder < ZERO) != (b < ZERO) ? quotient - ONE : quotient;
    }

    /** Rounds towards positive infinity. Divisor must not be zero. */
    public static int ceilDiv(int a, int b) {
        int quotient = a / b;
        int remainder = a % b;
        return remainder != ZERO && (remainder < ZERO) == (b < ZERO) ? quotient + ONE : quotient;
    }

    /** Remainder that keeps the sign of the divisor, matching the floor convention. Divisor must not be zero. */
    public static int floorMod(int a, int b) {
        int remainder = a % b;
        return remainder != ZERO && (remainder < ZERO) != (b < ZERO) ? remainder + b : remainder;
    }
}
