package argon.debug;

import java.util.concurrent.atomic.LongAdder;

/**
 * Layout counters.
 *
 * <p>LongAdder rather than AtomicLong because these are incremented once per candidate piece test,
 * and worldgen runs that loop across chunk threads where an atomic compare-and-set on a shared
 * cell costs more than the counter itself.
 */
public final class ArgonProfiler {
    public static final LongAdder PIECES_TESTED = new LongAdder();
    public static final LongAdder OCTREE_CHECKS_SAVED = new LongAdder();
    public static final LongAdder TOTAL_LAYOUT_TIME_NS = new LongAdder();

    private ArgonProfiler() {}

    public static void reset() {
        PIECES_TESTED.reset();
        OCTREE_CHECKS_SAVED.reset();
        TOTAL_LAYOUT_TIME_NS.reset();
    }
}
