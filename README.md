# Jabari Listener

Say "Jabari" and the phone opens Claude ready to talk.

## How it works
1. **ListenerService** listens for the wake word on the phone using Picovoice Porcupine (audio stays on the device).
2. On "Jabari" it frees the mic and opens Claude's assistant screen.
3. **TapHelperService** (an accessibility service) taps Claude's voice button within 10 seconds of the wake word. It does nothing at any other time and only sees the Claude app.
4. After 45 seconds it goes back to listening.

Until the custom `jabari.ppn` file is loaded, the test wake word is **"Jarvis"**.

## Secrets
The Picovoice AccessKey is never stored in this repo. Enter it in the app, or from the PC:
`adb shell am start -n com.whodat.jabari/.MainActivity --es access_key YOUR_KEY`

## Build
GitHub Actions builds `app-debug.apk` on every push (Actions tab, then the latest run, then Artifacts).

## Project record
- **Objective:** hands-free "Jabari" wake word on the TCL ION V (T607DL, Android 13 Go) that opens Claude voice mode.
- **Stack:** Kotlin, Android SDK 34, Porcupine 3.0.3, GitHub Actions.
- **Decisions:** no root; Claude has no voice-mode deep link and assistant sessions can't be started by apps, so an accessibility service taps the voice button.
- **Not yet tested on the device.** Unknowns: Claude's exact voice-button label (check `last_screen.txt` via `adb shell run-as com.whodat.jabari cat files/last_screen.txt`), mic hand-off timing, background-launch permission.
