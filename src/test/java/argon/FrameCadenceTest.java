package argon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FrameCadenceTest {
    @Test
    @DisplayName("only a positive cap below 260 is paced")
    void shouldPaceBoundaries() {
        assertFalse(FrameCadence.shouldPace(0));
        assertFalse(FrameCadence.shouldPace(-1));
        assertFalse(FrameCadence.shouldPace(1_000));
        assertFalse(FrameCadence.shouldPace(Integer.MAX_VALUE));
        assertTrue(FrameCadence.shouldPace(1));
        assertTrue(FrameCadence.shouldPace(2));
        assertTrue(FrameCadence.shouldPace(60));
        assertTrue(FrameCadence.shouldPace(144));
        assertTrue(FrameCadence.shouldPace(259));
        assertFalse(FrameCadence.shouldPace(260));
        assertFalse(FrameCadence.shouldPace(261));
    }

    @Test
    @DisplayName("a paced cap waits roughly one frame and does not run early")
    void pacedCapWaitsAboutOneFrame() {
        FrameCadence.release(Integer.MAX_VALUE);
        int fps = 100;
        long interval = TimeUnit.SECONDS.toNanos(1) / fps;

        FrameCadence.limit(fps);
        long start = System.nanoTime();
        FrameCadence.limit(fps);
        long waited = System.nanoTime() - start;

        assertTrue(waited > 0L, "a capped frame must not go out early, waited " + waited);
        assertTrue(waited < interval * 4L,
            "a capped frame must not stall either, waited " + waited + " for an interval of " + interval);
    }

    @Test
    @DisplayName("a cap change paces on the new interval instead of waiting out the old one")
    void capChangeSwitchesInterval() {
        FrameCadence.release(Integer.MAX_VALUE);
        FrameCadence.limit(60);

        long before = System.nanoTime();
        FrameCadence.limit(240);
        long spent = System.nanoTime() - before;

        // Vanilla aims at the previous frame time plus the interval, so the first frame on a new
        // cap waits about one frame at the new rate. What must not happen is carrying the old,
        // longer interval across the change.
        long oldInterval = TimeUnit.SECONDS.toNanos(1L) / 60L;
        assertTrue(spent < oldInterval,
            "the first frame on a new cap must not wait out the old interval, spent " + spent);
    }

    @Test
    @DisplayName("a stall longer than a frame is not caught up on")
    void stallIsNotCaughtUp() {
        FrameCadence.release(Integer.MAX_VALUE);
        FrameCadence.limit(60);
        try {
            Thread.sleep(30L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }

        long before = System.nanoTime();
        FrameCadence.limit(60);
        long spent = System.nanoTime() - before;
        assertTrue(spent < TimeUnit.MILLISECONDS.toNanos(5L),
            "Argon must not try to make up a stall, spent " + spent);
    }

    @Test
    @DisplayName("releasing the cap and taking it back paces from the first frame again")
    void releaseHandsTheClockOver() {
        FrameCadence.release(Integer.MAX_VALUE);
        FrameCadence.limit(30);

        // Several frames of the old cap. A clock left over from it would now sit far in the past,
        // and the next frame would compare against it and skip the wait.
        FrameCadence.release(260);
        burn(TimeUnit.SECONDS.toNanos(1L) / 30L * 3L);

        long before = System.nanoTime();
        FrameCadence.limit(30);
        long firstFrame = System.nanoTime() - before;
        assertTrue(firstFrame > 0L,
            "the first frame back under the same cap must be paced again, waited " + firstFrame);
    }

    private static void burn(long nanos) {
        long start = System.nanoTime();
        while (System.nanoTime() - start < nanos) {
            Thread.onSpinWait();
        }
    }

    @Test
    @DisplayName("jitter telemetry is only reported while Argon paces, and is never negative")
    void jitterTelemetry() {
        FrameCadence.release(Integer.MAX_VALUE);
        assertFalse(FrameCadence.isPacing());
        assertTrue(FrameCadence.jitterMicros() == 0L);

        FrameCadence.limit(200);
        FrameCadence.limit(200);
        assertTrue(FrameCadence.isPacing());
        assertTrue(FrameCadence.jitterMicros() >= 0L);
        assertTrue(FrameCadence.spinMicros() >= 20L && FrameCadence.spinMicros() <= 1_000L);

        FrameCadence.release(Integer.MAX_VALUE);
        assertFalse(FrameCadence.isPacing());
    }
}
