# Argon

Lite jigsaw layout and frame cadence for Minecraft 26.x. Speeds up jigsaw structure layout and paces frames when Max Framerate is below 260. Does not change vanilla structure size or seeds.

Fabric and Quilt. Java 25.

## Links

- Homepage: https://github.com/hasaanamerravid-source/Argon-Performance-Fabric
- Issues: https://github.com/hasaanamerravid-source/Argon-Performance-Fabric/issues
- NeoForge: https://github.com/hasaanamerravid-source/Argon-Performance-NeoForge

## Layout

Argon speeds up jigsaw layout (villages, ancient cities, trail ruins, and modded jigsaw structures) without changing what vanilla places.

It does not cap depth, piece count, or stronghold, mineshaft, or fortress chains. Those 0.1.x limits cut structures short and could leave a stronghold without a portal room. They are gone. `config/argon.json` is no longer read.

Vanilla's free space is a `VoxelShape` that every candidate piece is tested against. Argon carries a box octree in its place: the structure region is the tree boundary and every accepted piece is appended to the tree, so the test is the same question answered by boxes instead of voxels.

- Piece overlap uses a box octree. A candidate is tested against nearby pieces on the 0.25-deflated box. The accept or reject answer matches vanilla.
- Piece bounds are kept as six ints in one flat array per leaf, not as `AABB` objects, so a scan walks half the memory and compares the candidate's own block coordinates. The stored maximum is inclusive, which keeps a piece at `Integer.MAX_VALUE` representable. The 0.25 deflate only decides whether boxes that share a cell touch, and for integer boxes that is exactly the inclusive cell test, so the leaf scan needs no deflated copy.
- A child node is only created once some piece actually reaches it. Empty children cost no object and no boundary test per query. Pieces small against a large structure box are the common case, and most children there stay empty. The child array is sized to the axes that actually split, so a flat region does not reserve or walk slots it cannot fill.
- A node subdivides as soon as the leaf is full instead of after the next insertion, so the larger leaf array that would be copied and then thrown away is never allocated.
- Subdivision splits only the axes that are at least two blocks wide, so a flat or rod-shaped region still gets a tree instead of a single leaf that every query scans linearly.
- Vanilla builds a `VoxelShape` for the region and for every candidate before it asks whether the candidate fits, then throws the candidate shape away. Argon defers those shapes: a carrier holds the box, the octree answers the question, and the vanilla shape is only built for the inside-parent attach path, which runs against vanilla's per-piece free space. The seed's two shapes are deferred the same way.
- The octree allocates nothing to insert or test a piece, so the only objects on the candidate path are the small carriers that stand in for the shapes vanilla would have built.
- The layout counters are `LongAdder`, not `AtomicLong`, because they are bumped once per candidate piece and worldgen runs that loop across chunk threads.
- Jigsaw name, target, facing, and joint use one orientation lookup instead of two, because both live in `ORIENTATION`. The name is checked first, and a null child name still matches anything, the way vanilla reads it.

What Argon does not do to the layout:

- Pool-weight deduplication, template palette replacement, and processor bounds pruning are not included. Those can change layout or break mods that edit template lists.
- A rigid child is not skipped. An earlier build skipped one when the parent connector faced outside the structure box or into a placed piece, on the claim that vanilla would reject it too. The connector is only recorded when it sits inside the parent piece, and the parent piece is already in the octree, so the guard was true for every recorded connector and it dropped rigid children vanilla places. Those candidates run vanilla.
- Duplicate pool elements are not deduplicated. In 26.3 the candidate list is iterated but never `contains`-checked, so there was nothing to deduplicate, and dropping a repeat would change which pieces are tried and therefore the random sequence.
- A child attached inside its own parent piece is left to vanilla. That path tests against a per-piece shape the octree does not model, and pieces placed that way are always inside the parent box, which the octree already has.

## Frame cadence

When Max Framerate is below 260, Argon replaces `FramerateLimiter.limitDisplayFPS`. It keeps a frame clock, parks until the last stretch, then spins until the slot. The interval is cached until the cap changes, and the clock advances to the target time so it does not drift. A cap change or a stall longer than one frame resets the clock so it does not try to catch up. The interval is rounded to the nearest nanosecond rather than truncated, and the wait hands its last clock read back to the jitter estimate, so a paced frame reads the clock one fewer time.

The stretch that is parked and the stretch that is spun is not fixed. Park granularity belongs to the kernel and the scheduler, so a window that is too small means the park lands late and the frame goes out late, and one that is too large burns a core on the same deadline. The window starts at 1 ms, grows by however late a park came back, and gives half of any unused stretch back. It stays between 20 µs and 1 ms, and resets when the cap changes.

260 and above, and non-positive values, stay vanilla. This does not raise FPS. Do not combine VSync with an FPS cap. Do not install another limiter mixin beside Argon.

## Debug line

The F3 left column shows one `Argon-Fabric (version)` or `Argon-Quilt (version)` line. Colors stay aqua for the name and green for the version. The line is client-only. Extra copies from 26.3's per-batch `extractLines` calls are dropped.

26.3 calls `extractLines` more than once per frame and only the left-side batch can use the line, so the text is built when a brand slot is actually filled rather than on every call. The layout counters usually sit still across frames, so the text is also cached until one of them moves. The text and the counts it was built from are held together, so a line can never disagree with its own numbers.

## Sides

Layout mixins run wherever chunks generate, including a dedicated server. Frame cadence and the F3 line are client-only.

## Install

Drop `argon-0.3.1.jar` into the mods folder of a Fabric or Quilt instance for Minecraft 26.3. Java 25 is required. Fabric Loader 0.19.5 or newer is required on Fabric. Quilt Loader reads the same `fabric.mod.json`, so there is no second metadata file.

No config file. Max Framerate in video settings is the only switch.

## Build

JDK 25 on `PATH` or `JAVA_HOME`. Gradle 9.8.0 via the wrapper.

```sh
chmod +x gradlew
./gradlew build
```

Jar: `build/libs/argon-0.3.1.jar`
