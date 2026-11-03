# Voice Vanish

**Minecraft, but saying a block name makes that block disappear.**

A Fabric challenge prototype for three Windows players. Each client recognizes
its own microphone locally. The host persists spoken block bans, removes those
blocks from loaded chunks, and enforces the bans as more chunks load.

Current version: **0.1.3-prototype**. Target versions are **Minecraft 1.21.4**,
**Fabric Loader 0.18.4**, **Fabric API 0.119.4+1.21.4**, **Java 21**, and
**Windows x64** for microphone recognition. This is a separate Minecraft version
from [PermaPack](https://github.com/leocreated/permapack).

## Recognition and world rules

- Block names count anywhere in normal conversation. No wake phrase is required.
- Specific names such as `birch log`, `birch leaves` and `oak planks` are supported.
  `Grass` means grass block, and `redstone` means placed redstone dust.
- Broad `wood`, `logs` and `leaves` are not category-wide bans.
- sherpa-onnx provides streaming English recognition with Minecraft hotwords.
  A local Whisper pass checks andesite, granite, diorite and their variants.
- Microphones are opt-in and stop on disconnect. The mod does not save microphone
  audio or upload it to a speech service. Recognition still makes mistakes.
- Normal gameplay has no ban HUD announcements, added sounds or item drops.
- Air variants, end portal blocks and end portal frames are protected.
- A tick budget spreads removal work across loaded chunks. Future loads and
  subsequent placement of banned blocks are also checked.
- Undo pauses speech and restores journaled blocks only where the position is
  still air. It does not overwrite a newer build or fluid. Unloaded chunks wait
  until they load. Journals also preserve removed block entity data.

Start in a disposable world or a copy of your world. The recovery journal is
not a guarantee of consistency after a forced crash.

## Install

Every player installs the mod JAR and the matching Fabric API in a Fabric instance.
The speech libraries are nested in the built mod; speech **model files are
separate** and must be installed below. Essential and Flashback are optional
companions downloaded from their own publishers. The development companion
targets were Essential 1.5.2 and Flashback 0.39.10.

### Speech models

Paths are relative to the Minecraft instance's game folder. This repository
does not contain model weights, user recordings, worlds or account data.

Download these files from the upstream [English Zipformer model](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/tree/main):

```text
encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx
decoder-epoch-99-avg-1-chunk-16-left-128.onnx
joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx
tokens.txt
bpe.model
```

Put them in `config/voicevanish/models/zipformer-en-2023-06-26/`, then export the
hotword vocabulary with the included helper. It needs Python 3 and no extra packages:

```powershell
python tools/export-bpe-vocab.py "PATH-TO-INSTANCE/config/voicevanish/models/zipformer-en-2023-06-26"
```

This creates `bpe.vocab` beside `bpe.model`. Download
[`ggml-small.en-q5_1.bin`](https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-small.en-q5_1.bin)
and place it at:

```text
config/voicevanish/models/whisper-small.en/ggml-small.en-q5_1.bin
```

The expected SHA256 is
`bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30`.
Custom paths can be set in `config/voicevanish/client.json` after first launch.

### Start a session

The host opens a commands-enabled world and invites the other players. Each
player runs `/vvmic devices`, then `/vvmic device <index>` to select their own
input, or `/vvmic device -1` for the Windows default.

Use `/vvmic test` to inspect recognition without sending bans. Then each player
switches to `/vvmic on`, and the host runs `/vv start` to arm the challenge.
F8 toggles the local microphone. `/vvmic status` reports its readiness.

| Host command | Effect |
| --- | --- |
| `/vv start` | Arm speech-triggered bans. |
| `/vv pause` | Pause speech; existing bans remain enforced. |
| `/vv status` | Inspect world state. |
| `/vv ban minecraft:dirt` | Ban a block directly for a controlled test. |
| `/vv undo` | Pause speech and undo the latest ban using the recovery journal. |
| `/vv reset confirm` | Reset the challenge using the host's explicit reset command. |

`/voicevanish` is the long form of `/vv`. In an integrated world these are host
controls; on a dedicated server they require permission level 4. Replay worlds
never acquire a gameplay controller, and microphone capture is guarded against
Flashback replay mode.

## Build and tests

Use a JDK 21. On Windows:

```powershell
.\gradlew.bat test build
```

The production JAR is `build/libs/voice-vanish-0.1.3-prototype.jar`.
The wrapper pins Gradle 9.5.1 and Fabric Loom 1.17.20. Official sherpa-onnx 2.0.1
JVM and Windows native JARs are vendored under `libs/`; see their
[provenance and hashes](libs/README.md).

`runClientGameTest` starts a disposable client/server test. To include official
Essential and Flashback JARs in development, put them in a separate scratch
directory and run:

```powershell
python tools/prepare-compat.py "PATH-TO-SCRATCH-MODS"
.\gradlew.bat runClientGameTest -PcompatDir="PATH-TO-SCRATCH-MODS"
```

The preparation tool exposes nested libraries only for a development run.
Install the publishers' original JARs for normal play. Tests cover chunk and
dimension handling, C2S messages, placement, drops, persistence, recovery and
portal protection. Replay tests create a `.probe.json` fixture beside a replay;
playback verification uses:

```powershell
.\gradlew.bat runReplayProbe -PcompatDir="PATH-TO-SCRATCH-MODS" -PreplayFixture="PATH-TO-REPLAY.zip.probe.json"
```

Unit tests do not use your microphone. An optional local WAV probe is available
through the `speechSmoke` task and `speechModel`, `speechCheckModel`, `speechWav`
and `speechExpected` project properties. Do not commit personal recordings.

Three authenticated Essential clients and three live microphones still need
the players' own group test. Recognition accuracy depends on the speaker,
microphone, background audio and hardware; no perfect recognition is claimed.

## Third-party components and project license

Dependency and model attribution is in
[`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md), with license texts in
`distribution/`. Third-party licenses apply to their respective components.
A project-level license for the custom Voice Vanish source has not yet been selected.
