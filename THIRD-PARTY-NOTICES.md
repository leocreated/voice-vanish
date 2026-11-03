# Third-party notices

The custom mod's source and its dependencies are separate works. Preserve the
upstream notices when distributing the nested speech libraries or model files.

- **sherpa-onnx 2.0.1**, Xiaomi Corporation and contributors: Apache-2.0.
  [Upstream](https://github.com/k2-fsa/sherpa-onnx/tree/v2.0.1),
  [license text](distribution/APACHE-2.0.txt).
- **English streaming Zipformer, 2023-06-26**, k2-fsa / csukuangfj / Zengwei:
  Apache-2.0 according to the [model card](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26).
  Model weights are downloaded separately. The vocabulary helper exports the
  supplied SentencePiece vocabulary without changing the model weights.
- **ONNX Runtime**, Microsoft Corporation: MIT. Included by the upstream native
  JAR. See [license](distribution/ONNXRUNTIME-LICENSE.txt) and
  [dependency notices](distribution/ONNXRUNTIME-NOTICES.txt).
- **WhisperJNI 1.7.1**, Miguel Alvarez Diez / GiviMAD and contributors: Apache-2.0.
  [Upstream](https://github.com/GiviMAD/whisper-jni).
- **whisper.cpp**, Georgi Gerganov and contributors: MIT.
  [License text](distribution/WHISPER-CPP-LICENSE.txt).
- **Whisper model weights**, OpenAI and conversion contributors: MIT.
  [License text](distribution/WHISPER-LICENSE.txt). The current prototype uses
  the separately downloaded `ggml-small.en-q5_1.bin`.

The versioned notices in `distribution/` describe the exact earlier and current
speech dependencies, artifact hashes and model selection. Minecraft is not
included. Fabric, Essential and Flashback remain their publishers' projects;
obtain their binaries directly from those publishers.
