# DuoBuddy

A standalone Android TV assistant with local speech, timers, memory, and a choice of AI providers. Install the APK and add your own API key. No Mac or computer is required to run it.

[Download the TV APK](https://github.com/eonurk/DuoBuddy/releases) · [Install and setup](outputs/tv-companion/README.md)

Supports OpenAI GPT-6 Luna and Google Gemini 3.1 Flash-Lite, plus custom model IDs. AI replies need internet access and a provider account. Speech synthesis runs on the TV using bundled sherpa-onnx / Piper.

Source, build scripts, Android app and tests live in [`outputs/tv-companion`](outputs/tv-companion). Older controller and Mac packaging sources remain for reference and are not required by the standalone APK. Private runtime settings, SDKs and build products live in the ignored `work` directory.
