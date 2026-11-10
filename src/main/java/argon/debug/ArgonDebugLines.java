package argon.debug;

import argon.Argon;
import argon.FrameCadence;
import argon.LoaderNames;

public final class ArgonDebugLines {
    private static final char CODE = '\u00A7';
    public static final String BRAND = CODE + "bArgon-" + LoaderNames.current()
        + " " + CODE + "a(" + Argon.VERSION + ")";

    private static Counts shown;

    private ArgonDebugLines() {
    }

    /**
     * Only Argon's own line counts, and only when the line starts with the exact brand text. The
     * matcher rewrites and deletes lines, so matching on a loose substring would let a line from
     * another mod that merely mentions the name be swallowed as well.
     */
    public static boolean isBrandLine(String line) {
        return line != null && line.startsWith(BRAND);
    }

    /**
     * The F3 line, rebuilt only when a counter actually moved. 26.3 runs the extract path more than
     * once per frame and the layout counters sit still for most of them, so caching on the pair
     * keeps the concatenation and the two counter reads off the steady-state frame. The text and
     * the counts it was built from are kept in one immutable holder so a reader can never see a
     * line that disagrees with its own numbers.
     */
    public static String current() {
        long tested = ArgonProfiler.PIECES_TESTED.sum();
        long saved = ArgonProfiler.OCTREE_CHECKS_SAVED.sum();
        boolean pacing = FrameCadence.isPacing();
        long jitter = pacing ? FrameCadence.jitterMicros() : 0L;
        long spin = pacing ? FrameCadence.spinMicros() : 0L;
        Counts cached = shown;
        if (cached != null && cached.tested() == tested && cached.saved() == saved
            && cached.pacing() == pacing && cached.jitter() == jitter && cached.spin() == spin) {
            return cached.text();
        }
        StringBuilder text = new StringBuilder(BRAND);
        if (tested != 0L) {
            text.append(CODE).append("7 ").append(tested).append("p ").append(saved).append('o');
        }
        if (pacing) {
            text.append(CODE).append("8 | ").append(CODE).append("7jitter ").append(jitter)
                .append("us spin ").append(spin).append("us");
        }
        String built = text.toString();
        shown = new Counts(tested, saved, pacing, jitter, spin, built);
        return built;
    }

    private record Counts(long tested, long saved, boolean pacing, long jitter, long spin, String text) {
    }
}
