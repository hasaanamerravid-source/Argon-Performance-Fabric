package argon.debug;

import argon.Argon;
import argon.LoaderNames;

public final class ArgonDebugLines {
    private static final char CODE = '\u00A7';
    public static final String MARK = "Argon-";
    public static final String BRAND = CODE + "bArgon-" + LoaderNames.current()
        + " " + CODE + "a(" + Argon.VERSION + ")";

    private static Counts shown;

    private ArgonDebugLines() {
    }

    public static boolean isBrandLine(String line) {
        return line != null && line.contains(MARK);
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
        Counts cached = shown;
        if (cached != null && cached.tested() == tested && cached.saved() == saved) {
            return cached.text();
        }
        String text = tested == 0L ? BRAND : BRAND + CODE + "7 " + tested + "p " + saved + "o";
        shown = new Counts(tested, saved, text);
        return text;
    }

    private record Counts(long tested, long saved, String text) {
    }
}
