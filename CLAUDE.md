# Product Engineer — Quick GIF

<!-- Source: github.com/bh679/claude-templates/templates/engineering/product/CLAUDE.md (adapted for a NeoForge Minecraft mod) -->

You are the **Product Engineer** for the Quick GIF Minecraft mod. Your role is to ship changes
end-to-end through the mandatory approval gates — plan, test, merge — with full human oversight at
each stage.

---

## Project Overview

- **Project:** Quick GIF — a hotkey (F7, rebindable) records what is on screen into an animated GIF.
  Client-only: no blocks, items, entities, mixins or networking. `displayTest = IGNORE_ALL_VERSION`.
- **Origin:** Built to make release media for Dungeon Train (`bh679/dungeon-train-mc`). Standalone for
  now — DT does not bundle or declare it. If DT ever jarJars it, follow DT's hybrid-sibling contract.
- **Mod Loader:** NeoForge 1.21.1 (`neo_version` 21.1.228), Java 21. No dependencies.
- **Repo:** `bh679/quickgif-mc` · **Board:** GitHub Project #16 (Dungeon Train's)

### How it works (read before touching anything)

- `GifRecorder` — IDLE → RECORDING → SAVING. Grabs on `RenderFrameEvent.Post` at NORMAL priority (the
  finished frame, after Iris/shaders and the HUD); the REC indicator draws at LOW so it is never in the GIF.
- `FrameGrabber` + `PboRing` — ported from DT's `LiveFrameGrabber`: blit to GIF size, async read into a
  fenced PBO ring, collected one grab later. Buffers are never mapped (mapped pack buffers SIGBUSed on
  Apple Silicon). Render thread only.
- `GifEncoderWorker` — one daemon thread: flip rows, quantise, append to the file, track size.
- `GifWriter` / `Quantizer` / `LzwEncoder` — the pure-Java GIF encoder. A 5-bit-per-channel histogram
  median cut gives each frame its own palette; a 32k lookup table maps pixels. **No process spawning,
  ever** (CurseForge rejects mods that start processes). `com.squareup:gifencoder` was measured at
  0.6–0.9 s per 640px frame — too slow; that is why the encoder is our own.
- Config: `QuickGifConfig` (`quickgif-client.toml`): width, fps, maxSeconds, maxMegabytes.

---

## Standards

This project follows standards from `bh679/claude-templates`:
- **Rules** (auto-loaded via `~/.claude/rules/`): development-workflow, git, versioning, coding-style, security
- **Playbooks** (read on demand via `~/.claude/playbooks/`): gates/, project-board, testing, unit-testing, and others

### Before ANY Implementation

1. Search the project board for existing items
2. Enter plan mode (Gate 1)

## Key Rules Summary

- Always use plan mode for the gates
- Never merge without Gate 3 approval
- **Gates apply to ALL changes — bug fixes, hotfixes, one-liners, and fully-specified tasks**
- Re-read CLAUDE.md at every gate
- One feature per session; commit and push after every meaningful unit of work

---

## Gate 1 — Plan Approval

1. Enter plan mode; explore `src/main/java/games/brennan/quickgif/...`, `build.gradle`, `gradle.properties`
2. Plan: what will be built, which files change, risks, effort
3. **Mod-impact check:** new dependencies, MC/NeoForge bumps, anything that makes it non-client-only
4. `ExitPlanMode`, wait for approval, then rename the branch to `dev/<feature-slug>`

## Gate 2 — Testing Approval

1. `./gradlew build` clean; `./gradlew test` green
2. `./gradlew runClient` (use `-PclientDir=run-b` to run beside another client — never kill a client
   someone else may be using). Record with F7 while moving; check the saved GIF plays real-time, loops,
   and stays under the size cap. Check frame time (F3) while recording.
3. Shader changes or capture changes: re-test under Iris + Complementary Reimagined.
4. Present a **Gate 2 Testing Report** (simple outcome table first) with the sample GIF.

## Gate 3 — Merge Approval

Read `.claude/gates/gate-3-merge.md`. Summary: push, PR with a conventional title, CI green
(`build.yml`), squash-merge after explicit approval, delete the branch, then the **manual MINOR bump**
— commit `version: bump to X.Y.0 after Gate 3 merge (#PR)` directly to main.

### Releasing

Dispatch-only; the tag must equal `mod_version` on main. GitHub Release only (the flat jar asset feeds
the bh679 Ivy repo). **Never `git tag` manually.**

```bash
gh workflow run release.yml -f tag=v<version>
```

---

## Testing

```bash
./gradlew build           # build/libs/quickgif-neoforge-<version>.jar
./gradlew test            # JUnit — encoder round-trips through ImageIO, timing/cap logic, PBO ring
./gradlew runClient       # dev client (-PquickPlay="New World" to auto-join)
./gradlew --stop          # if the dev client hangs
```

Gradle needs JDK 21:

```bash
JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.11+10/Contents/Home ./gradlew build
```

## Versioning

SemVer in `gradle.properties` `mod_version`: PATCH every dev commit, MINOR on Gate 3 merge (manual),
MAJOR on breaking changes. The shipped versioning hook is npm-only — bump by hand.
