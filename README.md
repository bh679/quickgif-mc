# Quick GIF

A client-only NeoForge 1.21.1 mod: press **F7** to start recording a GIF of what's on your screen,
press **F7** again to save it. Shaders, HUD and all. Press F1 first for a clean shot.

- Saved to `gifs/` next to `screenshots/`. Click the name in chat to open it.
- Stops on its own before the file passes Discord's 10 MB limit, or after 20 seconds.
- Encoded in pure Java on a background thread. No ffmpeg, no external programs.
- Rebind the key in Controls → Quick GIF. Size, frame rate and caps are in `config/quickgif-client.toml`.

Works on any server; servers don't need it installed.

## Build

```bash
./gradlew build      # build/libs/quickgif-neoforge-<version>.jar
./gradlew test
./gradlew runClient
```

Licence: [PolyForm Shield 1.0.0](LICENSE).
