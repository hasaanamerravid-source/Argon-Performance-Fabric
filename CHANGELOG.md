# Changelog

## 0.2.4

Every layout change in this entry was checked against the vanilla source of `JigsawPlacement`, `JigsawBlock`, `BooleanOp`, and `Shapes`, and the octree is verified candidate-for-candidate against vanilla's own free-space algebra.

- `JigsawBlock.canAttach` now keeps vanilla's `target.name() == null` guard. A null child name means the connector matches anything. Vanilla templates never produce one because `JigsawBlockInfo.parse` defaults the name, but a mod that builds a `JigsawBlockInfo` directly does, and the name-first matcher used to reject those
- Removed the rigid-child skip. It skipped a child when the parent connector faced outside the structure box or into a placed piece, on the claim that vanilla would reject it. The connector is only recorded when it sits inside the parent piece, and the parent piece is already in the octree, so the guard was true for every recorded connector and it dropped rigid children that vanilla places. Those candidates run vanilla now
- Removed the tried-element identity list. In 26.3 the wrapped `Lists.newArrayList` is the candidate piece list, which is iterated but never `contains`-checked, so the identity map was written on every add and read never. Deduplicating it was rejected because dropping a repeated candidate changes which pieces are tried and therefore the random sequence
- The octree query is named for the answer it gives. `hasRoomFor` is the accept case, and the overlap wrap stands in for the reject side of `joinIsNotEmpty`, so both agree with vanilla's `!joinIsNotEmpty(free, deflated, ONLY_SECOND)`
- `JigsawPlacement$Placer.tryPlacingChildren` takes `PoolElementStructurePiece`, `MutableObject`, depth, the rigid flag, `LevelHeightAccessor`, `RandomState`, `PoolAliasLookup`, and `LiquidSettings`. The overlap and piece-recording wraps target that descriptor and capture the candidate piece `BoundingBox` as a local
- A child attached inside its own parent piece runs against vanilla's per-piece `sourceFree` shape, which the octree does not model. Those calls are left alone, and pieces placed that way are always inside the parent box, which is in the octree, so the carrier path stays correct without them

### Layout

- Piece overlap uses a box octree. A candidate is tested against nearby pieces on the 0.25-deflated box, and the accept or reject answer matches vanilla AABB checks
- Piece bounds are six doubles in one flat array per leaf, not `AABB` objects, so a scan walks contiguous memory and never dereferences a box. The duplicate check compares those doubles instead of boxing them through `AABB.equals`
- An octant is only created once some piece reaches it. Empty octants cost no object and no boundary test per query, which is the common case when pieces are small against a large structure box
- Inserting a piece and testing one allocate nothing. The `AABB.of` copy and the 0.25 deflate are computed straight off the piece `BoundingBox`
- Octree nodes do not split when a half-extent is under 2 blocks
- Accepted pieces are recorded on `Shapes.joinUnoptimized` (`require = 1`), the 26.3 accept path, and the carrier is returned so the octree keeps the same contents the `VoxelShape` would have carried
- `JigsawBlock.canAttach` uses Argon's name-first matcher. Vanilla reads the front and the top out of two properties per block; both live in `ORIENTATION`, so one lookup per block yields both
- Layout counters are `LongAdder`, not `AtomicLong`, because they are bumped once per candidate piece and worldgen runs that loop across chunk threads

### Frame cadence

- The interval is cached until Max Framerate changes, and the clock advances to the target time so the cap does not drift. A cap change or a stall longer than one frame resets it
- Caps of 260 and above, and non-positive caps, stay on vanilla `FramerateLimiter`
- The stretch that is parked and the stretch that is spun is no longer a fixed 1 ms. Park granularity belongs to the kernel and the scheduler, so a window that is too small means the park lands late and the frame goes out late, and one that is too large burns a core on the same deadline. The window starts at 1 ms, grows by however late a park came back, gives half of any unused stretch back, stays between 20 µs and 1 ms, and resets when the cap changes
- The busy-spin is skipped if the park already woke at or past the target, and a spurious wakeup re-parks instead of spinning through the remaining interval

### Debug line

- F3 left column shows `Argon-Quilt (version)` on Quilt and `Argon-Fabric (version)` on Fabric. Colors stay aqua and green
- F3 no longer stacks the brand line on every `extractLines` batch. Extra brand lines are stripped and only one is written per overlay pass
- 26.3 calls `extractLines` more than once per frame and only the left batch can use the line, so the text is built when a brand slot is actually filled rather than on every call
- The layout counters sit still across most frames, so the line is cached until one of them moves. The text and the counts it was built from are held together so a line can never disagree with its own numbers
- Loader brand is resolved once at class init
- The line is client-only

### Build

- `BitwiseMath` is now `argon.util.Arithmetic`. It is Argon's own integer helper set: ordering goes through `Integer.compare`, and the mask, branchless select, and floor and ceiling division helpers are documented against the overflow that subtract-then-shift runs into
- The `-Xlint:all -Werror` build is clean, and `build.gradle` now passes both to every `JavaCompile`, so a warning is a build failure rather than something to notice later. The source carries no `@SuppressWarnings` and pulls in no `MutableObject`, so the deprecated commons-lang3 getter is out of the picture too
- Source files end with a newline and carry no tabs or trailing whitespace
- Homepage, sources, and issues point at https://github.com/hasaanamerravid-source/Argon-Performance-Fabric

## 0.2.3

- Frame cadence caches the interval until Max Framerate changes, and advances the clock to the target time so the cap does not drift
- A stall longer than one frame still resets the clock. Argon does not try to catch up
- Caps of 260 and above, and non-positive caps, stay on vanilla `FramerateLimiter`
- Tried pool elements are tracked by identity. Equal-but-distinct elements are no longer treated as already tested
- The piece-carrier shape uses a 1x1x1 discrete shape instead of a zero-size shape
- Octree extents round away from zero, duplicate boxes are not inserted twice, and overlap uses one shared test
- Jigsaw name is checked before the orientation lookup. Attach rules are unchanged
- F3 left column shows only Argon-Fabric (version) or Argon-NeoForge (version). Colors stay aqua and green
- Mod menu homepage and issues links point at the main GitHub repository
- Version metadata is 0.2.3
- Jigsaw code is Argon's own. References to Structure Layout Optimizer were removed

## 0.2.2

- Restored the previous frame cadence. It keeps a frame clock, resets on a cap change or a stall, and leaves Max Framerate of 260 and above to vanilla
- Renamed the feature to frame cadence
- Removed the 0.2.1 limiter, which rewrote `lastPaceTime` before the wait and paced every positive cap

## 0.2.1

- Added a hybrid sleep on `FramerateLimiter.limitDisplayFPS`. Replaced in 0.2.2

## 0.2.0

- Removed the jigsaw depth cap, piece-depth cap, and fortress, mineshaft, and stronghold piece budget. Those limits changed vanilla structure size and could omit a stronghold portal room
- Removed `config/argon.json` and the Sodium limit page. Old config files are ignored
- Added a lite jigsaw layout path. Piece intersection uses a box octree, blocked rigid children are skipped, and duplicate pool elements are not retested
- Kept the vanilla random sequence on the skip paths, so structure layout stays seed-compatible
- Did not add pool-weight deduplication, template palette replacement, or processor bounds pruning
- Mod loads on the dedicated server as well as the client. The F3 line and frame cadence are client-only
- Descriptors checked against Minecraft 26.3 `JigsawPlacement` and `JigsawPlacement$Placer`

## 0.1.8

- Fixed a crash the moment a jigsaw structure generated. The `JigsawPlacement.addPieces` mixin targeted the wrong package for `DimensionPadding`
- Added `tools-check-mixin-descriptors.py`
- Sodium page exposed jigsaw depth, piece depth, and piece budget as sliders
- Java 25 toolchain pinned in the build

## 0.1.7

- Compile and depend on Java 25 so stock 26.3 launches can load the jar

## 0.1.6

- Unlimited FPS left vanilla `FramerateLimiter` alone
- `pieceDepth` range 4-50, default 16

## 0.1.5

- Piece depth cap for mineshaft, stronghold, and nether fortress

## 0.1.4

- Jigsaw `maxDepth` cap

## 0.1.2

- Frame cadence on `FramerateLimiter.limitDisplayFPS`

## 0.1.0

- Version reset
