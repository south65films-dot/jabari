# Jabari Listener

Say "Jabari" and the phone opens Claude ready to talk.

## How it works
1. **ListenerService** listens for "Jabari" or "Hey Jabari" on the phone using sherpa-onnx keyword spotting (free, open source, audio stays on the device).
2. On "Jabari" it frees the mic and opens Claude's assistant screen.
3. **TapHelperService** (an accessibility service) taps Claude's voice button within 10 seconds of the wake word. It does nothing at any other time and only sees the Claude app.
4. After 45 seconds it goes back to listening.

## Voice commands
Every command is a full phrase so nothing collides with a bare "Jabari":
- "Jabari, wake up": open Claude
- "Jabari, record" / "Jabari, stop": audio (.m4a) in Music/Jabari Recordings
- "Jabari, video" / "Jabari, stop": video in Movies/Jabari Videos
- "Jabari, drop a pin": location to Documents/Jabari
- "Jabari, panic" (loud alarm) / "Jabari, silent" (quiet): email location to the configured contacts
- "Jabari, identify": photo into Claude asking what it is
- "Jabari, translate": photo into Claude asking to translate the text
- "Jabari, translate speech": opens Google Translate for live/conversation translation (listener pauses to free the mic)
- "Jabari, stop": end alarm/recording

## Wake words
Edit `keywords_raw.txt` (one phrase per line, `@NAME` labels it). The build turns it into model tokens. No account or key needed.

## Build
GitHub Actions downloads the latest sherpa-onnx release, the English keyword model (gigaspeech 3.3M) and builds `app-debug.apk` on every push (Actions tab, then the latest run, then Artifacts).

## Project record
- **Objective:** hands-free "Jabari" wake word on the TCL ION V (T607DL, Android 13 Go) that opens Claude voice mode.
- **Stack:** Kotlin, Android SDK 34, sherpa-onnx keyword spotting, GitHub Actions.
- **Decisions:** switched from Picovoice (needed account approval) to sherpa-onnx (free, no account); no root; Claude has no voice-mode deep link and assistant sessions can't be started by apps, so an accessibility service taps the voice button.
- **Tested 2026-11-16 on the TCL ION V: works** (wake word opens Claude listening). Install with `jabari_install.bat`. If a Claude update moves the voice button, check `adb shell run-as com.whodat.jabari cat files/last_screen.txt` and update the labels in TapHelperService.
- **Next ideas:** play the intro video on wake; tune sensitivity if it misfires.
