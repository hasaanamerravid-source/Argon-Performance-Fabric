package argon;

import java.util.concurrent.locks.LockSupport;

/**
 * Hybrid frame cadence. Unlimited caps (260 and above, or non-positive) stay vanilla.
 *
 * <p>The split between parking and spinning is not fixed. Park granularity is a property of the
 * kernel and the scheduler, so a window that is too small means the park lands late and the frame
 * goes out late, and a window that is too large means burning a core to hit the same deadline.
 * The window therefore tracks the overshoot: a park that landed late widens it by the amount it
 * missed, and a spin that finished with room to spare gives half that room back.
 */
public final class FrameCadence {
    private static final long ONE_SECOND = 1_000_000_000L;
    private static final long SPIN_FLOOR = 20_000L;
    private static final long SPIN_CEILING = 1_000_000L;

    private static long last = System.nanoTime();
    private static int lastLimit = -1;
    private static long cachedInterval = 0;
    private static long spinWindow = SPIN_CEILING;

    private FrameCadence() {
    }

    public static boolean shouldPace(int fps) {
        return fps > 0 && fps < 260;
    }

    public static void limit(int fps) {
        if (!shouldPace(fps)) {
            last = System.nanoTime();
            lastLimit = fps;
            spinWindow = SPIN_CEILING;
            return;
        }

        long now = System.nanoTime();
        refreshInterval(fps, now);

        long target = last + cachedInterval;
        if (target < now - cachedInterval) {
            target = now;
        }

        waitUntil(target);
        last = target;
    }

    private static void refreshInterval(int fps, long now) {
        if (fps == lastLimit) {
            return;
        }
        cachedInterval = ONE_SECOND / fps;
        last = now;
        lastLimit = fps;
        spinWindow = SPIN_CEILING;
    }

    private static void waitUntil(long target) {
        long time = System.nanoTime();
        if (time >= target) {
            return;
        }
        long spinAt = target - spinWindow;
        while (time < spinAt) {
            LockSupport.parkNanos(spinAt - time);
            time = System.nanoTime();
        }
        if (time >= target) {
            widen(System.nanoTime() - target);
            return;
        }
        long spinStart = time;
        while (time < target) {
            Thread.onSpinWait();
            time = System.nanoTime();
        }
        narrow(spinWindow - (time - spinStart));
    }

    private static void widen(long overshoot) {
        spinWindow = clampSpin(spinWindow + overshoot);
    }

    private static void narrow(long spare) {
        spinWindow = clampSpin(spinWindow - (spare >> 1));
    }

    private static long clampSpin(long window) {
        return Math.min(Math.max(window, SPIN_FLOOR), SPIN_CEILING);
    }
}
