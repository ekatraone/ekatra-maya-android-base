# Maya / Asmaa Android app: pre-event base snapshot

This repository is a **sanitised snapshot of pre-existing code**: the Maya / Asmaa Android app (offline chat with an on-device model through llama.cpp, Room database, Firebase sign-in and analytics, host mode) exactly as it stood at commit `87a7fa3` of the private repository `ekatra-asmaa`, dated 2026-09-30. That is before the Meta Global AI Developer Hackathon event started (2026-10-26).

It is the **base** for the event repository (Ekatra Offline). The event repository contains only the work done during the event and applies it on top of this snapshot with `scripts/apply_overlay.sh`. This repository has a single commit and no history on purpose; its history stays private because it predates the event. License: MIT (Ekatra), see `LICENSE`.

On its own this is the older app, with a model catalog (`ModelConfig.kt`) that lists several non-Meta models. The event work makes Llama 3.2 1B Instruct the first-preference model; see the event repository and its Disclosure section.

## What was removed, and why

| Removed | Why |
|---|---|
| `app/src/maya/google-services.json`, `app/src/asmaa/google-services.json` (replaced by `.example` files) | Firebase project config with project ids and an API key |
| signing keystores and certificates | none are tracked at this commit; `.gitignore` excludes `*.keystore`, `*.jks`, `*.der`, `*.p12`, `*.pem` |
| `mobilellm_socrates_lora/`, `mobilellm_socrates_merged/` | fine-tuned model weights (1.4 GB) and tokenizers |
| `notebooks/`, `scripts/eval*.py`, `scripts/finetune.py`, `scripts/test_*.py`, `scripts/train_overnight.sh`, `scripts/optimize_performance.sh`, `scripts/download_model.sh`, `scripts/setup_and_build.sh` | training, evaluation and model-download helpers that can hold hard-coded tokens and point at non-Meta models |
| `.claude/` (audit trail, milestones, session log) | internal planning notes |
| `docs/` except the privacy policy, context-management and performance guides, and the model test results; `README-offline-app.md`, `firebase/FIREBASE_SETUP.md` | internal plans, security audit, release, store and setup documents |

Kept: the `lib/llama.cpp` submodule as a gitlink at the commit the base pinned (`.gitmodules` added, URL `https://github.com/ggml-org/llama.cpp.git`), and `firebase/firestore.rules`, `firestore.indexes.json` and `remote_config_defaults.json` (no project ids). No learner or customer data was in the tree.

## Build and run

You need JDK 17, the Android SDK and NDK (Android Studio installs them) and an arm64 phone or emulator image (the native engine targets arm64).

```
git clone --recurse-submodules <this repo> && cd <this repo>
# if you cloned without --recurse-submodules:  git submodule update --init lib/llama.cpp
echo "sdk.dir=/path/to/Android/sdk" > local.properties        # gitignored
cp app/src/maya/google-services.json.example app/src/maya/google-services.json
cp app/src/asmaa/google-services.json.example app/src/asmaa/google-services.json
# then replace both with files downloaded from your own Firebase project
# (package org.ekatra.alfred for Maya, org.ekatra.asmaa for Asmaa); the Google Services
# Gradle plugin fails the build without a valid file
./gradlew :app:assembleMayaDebug        # or :app:assembleAsmaaDebug
```

Release builds need your own keystores (see `configurePersonaSigning` in `app/build.gradle.kts`); debug builds do not. The app downloads its model on first run. To add the event work, clone the event repository next to this one and run its `scripts/apply_overlay.sh --android <this directory>`.
