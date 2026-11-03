# Build verification

Publication snapshot checked on 2026-11-03 with JDK 21 on Windows x64.

```text
gradlew.bat --no-daemon test build
```

- Production build and remapped production JAR passed.
- 34 unit tests passed, with no failures, errors or skipped tests.
- Test coverage includes block-name matching, aliases, stable speech detection,
  verified mineral names, transcript policy, rate limiting, audio resampling,
  Whisper tuning and persistence.
- Both vendored sherpa-onnx libraries match the official release SHA256 digests.
- Developer test classes and user audio are not included in the production JAR.

Artifact: `voice-vanish-0.1.3-prototype.jar`

SHA256: `747c9d278b96b9cb6b14d1062ad9f07ad1514a92e0a3a7ea2b5da6c937482914`

Interactive client/world/replay checks were performed during prototype
development and their test sources are included. Those checks and live speech
were not rerun for source publication. Three authenticated Essential clients,
three real microphones and extended gameplay still need the players' own
group test. Model files are separate downloads, as explained in the README.
