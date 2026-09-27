package argon;

import java.util.concurrent.locks.LockSupport;

/** Hybrid frame cadence. Unlimited caps (260 and above, or non-positive) stay vanilla. */
public final class FrameCadence {
    private static final long ONE_MS = 1_000_000L;
    private static long last = System.nanoTime();
    private static int lastLimit = -1;

    private FrameCadence() {
    }

    public static boolean shouldPace(int fps) {
        return fps > 0 && fps < 260;
    }

    public static void limit(int fps) {
        if (!shouldPace(fps)) {
            last = System.nanoTime();
            lastLimit = fps;
            return;
        }
        long now = System.nanoTime();
        if (fps != lastLimit) {
            last = now;
            lastLimit = fps;
        }
        long interval = 1_000_000_000L / fps;
        long target = last + interval;
        if (target < now - interval) {
            target = now;
        }
        long remaining = target - now;
        if (remaining > ONE_MS) {
            LockSupport.parkNanos(remaining - ONE_MS);
        }
        while (System.nanoTime() < target) {
            Thread.onSpinWait();
        }
        last = System.nanoTime();
    }
}
