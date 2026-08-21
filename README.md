# Manhwa Translator (Android)

On-device Korean -> English real-time overlay translator for manhwa/comic reader apps.
V1 uses ML Kit (Korean text recognition + on-device translation) behind pluggable
interfaces (`TextDetector`, `KoreanOcr`, `Translator`) so custom ONNX/LiteRT models
can replace them later without touching the rest of the app.

## Requirements
- Android Studio (Koala+) or IntelliJ with Android plugin
- Android SDK 34, min SDK 26
- JDK 17

## Setup
Run `bash setup.sh` from an empty/target project root (safe to re-run; only missing files are written... actually it overwrites tracked files it manages, so commit before re-running if you've hand-edited generated files).

Then copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Environment variables / secrets
None required for V1 (ML Kit models download on-device, no API keys needed).
If you later add cloud fallback translation, put keys in `local.properties` (gitignored)
and read them via `BuildConfig` fields — never commit them.

## Run
See root instructions from the assistant (RUN/TEST commands).
