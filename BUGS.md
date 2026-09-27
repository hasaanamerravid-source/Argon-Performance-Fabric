# Notes

- 0.2.2 does not change structure size, loot, or mobs. It only changes how piece overlap is tested, and how a Max Framerate below 260 waits.
- `config/argon.json` from 0.1.x is ignored.
- Run `python3 tools-check-mixin-descriptors.py` after a Minecraft version bump.
- Mixins use `require = 1`. A method rename crashes on boot instead of silently falling back.
- Layout mixins run during world generation, including on a dedicated server. Frame cadence runs only in the client process.
- The F3 line is added to the left column of the debug overlay.
- Do not install another FPS-cap mixin alongside Argon. Both replace `limitDisplayFPS`.
