# Argon 0.2.2

Lite structure-layout optimizer and frame cadence for Minecraft 26.3. Fabric and NeoForge. Java 25.

## Layout

Argon speeds up jigsaw layout (villages, ancient cities, trail ruins, and modded jigsaw structures) without changing what vanilla places.

It does not cap depth, piece count, or stronghold, mineshaft, or fortress chains. Those 0.1.x limits cut structures short and could leave a stronghold without a portal room. They are gone. `config/argon.json` is no longer read.

- Piece overlap uses a box octree. A new piece is tested against nearby pieces. The accept or reject result matches vanilla AABB checks, including the 0.25 block deflate.
- A rigid child is skipped when the parent jigsaw already faces outside the structure box or into a placed piece. Vanilla would reject that child. The shuffle has already used its random calls.
- A duplicate pool element is not retested after its rotations were tried. The same jigsaw shuffles still run, so the seed stays in step.
- Jigsaw name, target, facing, and joint use one orientation lookup instead of two.

Pool-weight deduplication, template palette replacement, and processor bounds pruning are not included. Those can change layout or break mods that edit template lists.

The box-octree approach is adapted from TelepathicGrunt's Structure Layout Optimizer (MIT).

## Frame cadence

When Max Framerate is below 260, Argon replaces `FramerateLimiter.limitDisplayFPS`. It keeps a frame clock, parks until the last millisecond, then spins until the slot. A cap change or a stall longer than one frame resets the clock so it does not try to catch up.

260 and above, and non-positive values, stay vanilla. This does not raise FPS. Do not combine VSync with an FPS cap. Do not install another limiter mixin beside Argon.

## Sides

Layout mixins run wherever chunks generate, including a dedicated server. Frame cadence and the F3 line are client-only.

## Build

JDK 25 on `PATH` or `JAVA_HOME`. Gradle 9.8.0 via the wrapper.

```sh
chmod +x gradlew
./gradlew build
```

Jar: `build/libs/argon-0.2.2.jar`
