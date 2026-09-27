# Changelog

## 0.2.2

- Restored the previous frame cadence. It keeps a frame clock, resets on a cap change or a stall, and leaves Max Framerate of 260 and above to vanilla
- Renamed the feature from frame pacer to frame cadence
- Removed the 0.2.1 FramePacer-style limiter, which rewrote `lastPaceTime` before the wait and paced every positive cap

## 0.2.1

- Added a FramePacer-style hybrid sleep on `FramerateLimiter.limitDisplayFPS`. Replaced in 0.2.2

## 0.2.0

- Removed the jigsaw depth cap, piece-depth cap, and fortress, mineshaft, and stronghold piece budget. Those limits changed vanilla structure size and could omit a stronghold portal room
- Removed `config/argon.json` and the Sodium limit page. Old config files are ignored
- Added a lite jigsaw layout optimizer. Piece intersection uses a box octree, blocked rigid children are skipped, and duplicate pool elements are not retested
- Kept the vanilla random sequence on the skip paths, so structure layout stays seed-compatible
- Did not add pool-weight deduplication, template palette replacement, or processor bounds pruning
- Mod loads on the dedicated server as well as the client. The F3 line and frame cadence are client-only
- Descriptors checked against Minecraft 26.3 `JigsawPlacement` and `JigsawPlacement$Placer`

## 0.1.8

- Fixed a crash the moment a jigsaw structure generated. The `JigsawPlacement.addPieces` mixin targeted the wrong package for `DimensionPadding`
- Added `tools-check-mixin-descriptors.py`
- Sodium page exposed jigsaw depth, piece depth and piece budget as sliders
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
