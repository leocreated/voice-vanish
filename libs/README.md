# Vendored speech libraries

These are the unmodified official **sherpa-onnx 2.0.1** release JARs. They allow
the build's flat-directory dependency resolver to use the exact tested artifacts.

| File | SHA256 |
| --- | --- |
| `sherpa-onnx-jvm-2.0.1.jar` | `8320e2457b6e38dd624fb619d3552e11ef78ffeb394622056126b16f2fd99d12` |
| `sherpa-onnx-native-lib-win-x64-2.0.1.jar` | `536f1c09025996a780b22f972b2e56e0e8b2064e1ceb534456ea7eba0a6ae4cd` |

Downloads:

- [JVM library](https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-jvm-2.0.1.jar)
- [Windows x64 native library](https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-native-lib-win-x64-2.0.1.jar)
- [Upstream release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1)

sherpa-onnx is Apache-2.0 licensed. ONNX Runtime is included by the native
distribution under its own MIT license and notices. Full license texts and
attribution are under `distribution/`. Fabric Loom adds nested-JAR loading
metadata during the production build; the upstream classes and native binaries
are not modified.
