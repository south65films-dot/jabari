# Jabari Listener

Say "Jabari" and the phone opens Claude ready to talk.

## How it works
1. **ListenerService** listens for "Jabari" or "Hey Jabari" on the phone using sherpa-onnx keyword spotting (free, open source, audio stays on the device).
2. On "Jabari" it frees the mic and opens Claude's assistant screen.
3. **TapHelperService** (an accessibility service) taps Claude's voice button within 10 seconds of the wake word. It does nothing at any other time and only sees the Claude app.
4. After 45 seconds it goes back to listening.

## Wake words
Edit `keywords_raw.txt` (one phrase per line, `@NAME` labels it). The build turns it into model tokens. No account or key needed.

## Build
GitHub Actions downloads the latest sherpa-onnx release, the English keyword model (gigaspeech 3.3M) and builds `app-debug.apk` on every push (Actions tab, then the latest run, then Artifacts).

## Project record
- **Objective:** hands-free "Jabari" wake word on the TCL ION V (T607DL, Android 13 Go) that opens Claude voice mode.
- **Stack:** Kotlin, Android SDK 34, sherpa-onnx keyword spotting, GitHub Actions.
- **Decisions:** switched from Picovoice (needed account approval) to sherpa-onnx (free, no account); no root; Claude has no voice-mode deep link and assistant sessions can't be started by apps, so an accessibility service taps the voice button.
- **Not yet tested on the device.** Unknowns: Claude's exact voice-button label (check `last_screen.txt` via `adb shell run-as com.whodat.jabari cat files/last_screen.txt`), mic hand-off timing, background-launch permission.
