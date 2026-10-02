package argon.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArithmeticTest {
    private static final int[] EDGES = {
        Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -70000, -2, -1, 0, 1, 2, 70000,
        Integer.MAX_VALUE - 1, Integer.MAX_VALUE
    };

    @Test
    @DisplayName("ordering survives the full int range, including the subtraction overflow")
    void orderAcrossFullRange() {
        for (int a : EDGES) {
            for (int b : EDGES) {
                assertEquals(Integer.compare(a, b), Arithmetic.order(a, b));
                assertEquals(Integer.compareUnsigned(a, b), Arithmetic.orderUnsigned(a, b));
            }
        }
    }

    @Test
    @DisplayName("the masks match the plain comparisons they stand in for")
    void masksMatchComparisons() {
        for (int a : EDGES) {
            for (int b : EDGES) {
                assertEquals(a < b ? -1 : 0, Arithmetic.maskLessThan(a, b));
                assertEquals(a <= b ? -1 : 0, Arithmetic.maskLessThanOrEqual(a, b));
                assertEquals(a > b ? -1 : 0, Arithmetic.maskGreaterThan(a, b));
                assertEquals(a >= b ? -1 : 0, Arithmetic.maskGreaterThanOrEqual(a, b));
                assertEquals(a == b ? -1 : 0, Arithmetic.maskEqualTo(a, b));
                assertEquals(a != b ? -1 : 0, Arithmetic.maskNotEqualTo(a, b));
            }
        }
    }

    @Test
    @DisplayName("select is a pick whichever way the mask is driven")
    void selectPicksEitherSide() {
        for (int a : EDGES) {
            for (int b : EDGES) {
                // An all-ones mask takes the set side, an all-zero mask the clear side, whatever
                // the two values are. The mask decides, not the numbers.
                assertEquals(Math.min(a, b), Arithmetic.select(Arithmetic.maskLessThan(a, b), a, b),
                    "select(min mask, " + a + ", " + b + ")");
                assertEquals(b, Arithmetic.select(0, a, b), "select(zero, " + a + ", " + b + ")");
                assertEquals(a, Arithmetic.select(-1, a, b), "select(all ones, " + a + ", " + b + ")");
            }
        }
    }

    @Test
    @DisplayName("min, max and clamp agree with Math at the edges, sign flips included")
    void minMaxAndClamp() {
        for (int a : EDGES) {
            for (int b : EDGES) {
                int low = Math.min(a, b);
                int high = Math.max(a, b);
                assertEquals(low, Arithmetic.min(a, b));
                assertEquals(high, Arithmetic.max(a, b));
                // Both values are already inside [low, high], so clamp hands each one straight
                // back. Only the value outside the range is the one that moves.
                assertEquals(a, Arithmetic.clamp(a, low, high), "clamp(" + a + ", " + low + ", " + high + ")");
                assertEquals(b, Arithmetic.clamp(b, low, high), "clamp(" + b + ", " + low + ", " + high + ")");
                // One step outside the range on each side, where the step itself still fits in an
                // int. At MIN_VALUE and MAX_VALUE it would wrap and land back inside.
                if (low > Integer.MIN_VALUE) {
                    assertEquals(low, Arithmetic.clamp(low - 1, low, high), "clamp below " + low);
                }
                if (high < Integer.MAX_VALUE) {
                    assertEquals(high, Arithmetic.clamp(high + 1, low, high), "clamp above " + high);
                }
            }
        }
        assertEquals(0, Arithmetic.clamp(-9, 0, 10));
        assertEquals(10, Arithmetic.clamp(99, 0, 10));
        assertEquals(5, Arithmetic.clamp(5, 0, 10));
    }

    @Test
    @DisplayName("the plain comparison helpers read the same as the operators")
    void comparisonHelpers() {
        assertTrue(Arithmetic.lessThan(1, 2));
        assertTrue(Arithmetic.lessThanOrEqual(2, 2));
        assertTrue(Arithmetic.greaterThan(3, 2));
        assertTrue(Arithmetic.greaterThanOrEqual(2, 2));
        assertTrue(Arithmetic.equalTo(2, 2));
        assertTrue(Arithmetic.notEqualTo(1, 2));
        // MIN_VALUE is the smallest int, so it is less than MAX_VALUE and never greater, whatever
        // the distance between them. The pair that overflows a naive subtraction is this one.
        assertTrue(Arithmetic.lessThan(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertFalse(Arithmetic.greaterThan(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertFalse(Arithmetic.lessThan(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertTrue(Arithmetic.greaterThan(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertTrue(Arithmetic.lessThanOrEqual(Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertTrue(Arithmetic.greaterThanOrEqual(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertFalse(Arithmetic.lessThanOrEqual(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertFalse(Arithmetic.lessThan(2, 1));
        assertFalse(Arithmetic.lessThan(2, 2));
        assertFalse(Arithmetic.greaterThan(1, 2));
        assertFalse(Arithmetic.equalTo(1, 2));
        assertFalse(Arithmetic.notEqualTo(2, 2));
    }

    @Test
    @DisplayName("floorDiv, ceilDiv and floorMod agree with Math across the overflow edges")
    void divisionHelpersAgainstMath() {
        int[] divisors = {-1024, -3, -1, 1, 3, 1024};
        for (int value : EDGES) {
            for (int divisor : divisors) {
                assertEquals(Math.floorDiv(value, divisor), Arithmetic.floorDiv(value, divisor),
                    "floorDiv(" + value + ", " + divisor + ")");
                assertEquals(Math.floorMod(value, divisor), Arithmetic.floorMod(value, divisor),
                    "floorMod(" + value + ", " + divisor + ")");
                assertEquals(ceilDiv(value, divisor), Arithmetic.ceilDiv(value, divisor),
                    "ceilDiv(" + value + ", " + divisor + ")");
            }
        }
    }

    @Test
    @DisplayName("the division helpers round the way the doc says, not the way / truncates")
    void divisionRoundingDirection() {
        assertEquals(2, Arithmetic.floorDiv(7, 3));
        assertEquals(-3, Arithmetic.floorDiv(-7, 3));
        assertEquals(-3, Arithmetic.floorDiv(7, -3));
        assertEquals(2, Arithmetic.floorDiv(-7, -3));
        assertEquals(3, Arithmetic.ceilDiv(7, 3));
        assertEquals(-2, Arithmetic.ceilDiv(-7, 3));
        assertEquals(-2, Arithmetic.ceilDiv(7, -3));
        assertEquals(3, Arithmetic.ceilDiv(-7, -3));
        // floorMod keeps the sign of the divisor, which is what makes the floor convention hold
        // on both sides of zero.
        assertEquals(2, Arithmetic.floorMod(-7, 3));
        assertEquals(-2, Arithmetic.floorMod(7, -3));
        assertEquals(1, Arithmetic.floorMod(7, 3));
        assertEquals(-1, Arithmetic.floorMod(-7, -3));
        assertEquals(0, Arithmetic.floorDiv(0, 5));
        assertEquals(0, Arithmetic.ceilDiv(0, 5));
        assertEquals(0, Arithmetic.floorMod(0, 5));
    }

    @Test
    @DisplayName("the divisors that make MIN_VALUE a special case still round correctly")
    void minValueByMinusOne() {
        assertEquals(Integer.MIN_VALUE, Arithmetic.floorDiv(Integer.MIN_VALUE, -1));
        assertEquals(0, Arithmetic.floorMod(Integer.MIN_VALUE, -1));
        assertEquals(-2147483648L, ceilDiv(Integer.MIN_VALUE, -1));
        assertEquals(Integer.MAX_VALUE, Arithmetic.ceilDiv(Integer.MAX_VALUE, 1));
        assertEquals(Integer.MAX_VALUE, Arithmetic.floorDiv(Integer.MAX_VALUE, 1));
    }

    /**
     * Ceiling division as a reference, in long arithmetic. Because floorMod keeps the sign of the
     * divisor, a nonzero remainder is always a fraction above the floor quotient, so the ceiling is
     * one more than the floor whichever way the divisor points.
     */
    private static long ceilDiv(int value, int divisor) {
        long quotient = Math.floorDiv(value, divisor);
        long remainder = Math.floorMod(value, divisor);
        return remainder == 0L ? quotient : quotient + 1L;
    }
}