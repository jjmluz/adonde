# AGENTS.md

Guide for coding agents working on Adonde. Read `README.md` for the game's rules and the Google Maps limits.

## The user and the device

- A family game for one TV: a Google TV Streamer (`kirkwood`, Android 14, 1920×1080 UI, 32-bit userspace,
  modest GPU) in a family home in Portugal, played with the remote's D-pad. Sideloaded, never on Google Play.
- The user writes in English; **UI text is Portuguese from Portugal** (not Brazilian): "Ronda 1 de 5",
  "Manter premido para adivinhar", "Voltar ao início". The user picks the wording; don't rephrase it unasked.
- The home screen is the user's own launcher (`com.jj.launcher`, `~/projects/jj-launcher`). Adonde is on its
  third row, with the banner from `res/drawable-nodpi/banner.png`.
- The TV is in use by the family. Anything you do on it is visible.

## Build, install, run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # shell Java is too old
ANDROID_SERIAL=192.168.1.94:5555 ./gradlew testDebugUnitTest installRelease
```

- AGP 9.2.1, Gradle 9.4.1, Kotlin 2.4.10, Compose BOM 2026.09.00 (foundation only), play-services-maps 20.1.0,
  minSdk 26, targetSdk 37.
- **Install the release build.** It starts in about 1 s on the TV; the debug build was ~4× slower. Both are
  signed with the debug key, so they install over each other and the Maps key works with either.
- Set `ANDROID_SERIAL`: the `GoogleTV_34` emulator is often connected too.
- If adb can't find the TV: `adb connect 192.168.1.94:5555`. The wireless-debugging port from
  `adb mdns services` changes and goes stale; 5555 is the one that has kept working. If that's refused too,
  ask the user.
- `local.properties` holds `sdk.dir` and `MAPS_API_KEY`, and is git-ignored. **Never print, paste or commit
  the key.** It reaches the manifest through `manifestPlaceholders`.

## Code map (`app/src/main/kotlin/com/jjmluz/adonde/`)

| File | Role |
|---|---|
| `Game.kt` | The rules, with no Android code: players, rounds, turn order, distances (`distanceKm`), `formatKm`, `withUniqueAvatars`, `OkButton` (tap vs hold). |
| `Places.kt` | `PlacePicker`: picks a town from `assets/places.csv` and a point near it to search for a panorama. |
| `GameViewModel.kt` | All game state and what each key does (`Screen`: SPLASH, SETUP, EXPLORE, GUESS, REVEAL, FINAL; `Control`: MOVE, ZOOM; pause). Saves the setup to SharedPreferences only when a game starts. |
| `MainActivity.kt` | Single activity; sends keys from `dispatchKeyEvent` to the view model. |
| `Screens.kt` | Every Compose screen, colours (the "Noite" palette), the logo, `ControlHint` with the round tab, `DpadIcon`, `BackIcon`, the pause overlay. |
| `Maps.kt` | `StreetViewPanoramaView` and `MapView` inside `AndroidView`: panorama search and filtering, the guess map with a fixed pin, the reveal map, pin bitmaps. |
| `Globe.kt` | The spinning dot globe on the splash and search screens, drawn on a `Canvas` each frame. |

Tests: `app/src/test/kotlin/com/jjmluz/adonde/GameTest.kt` covers `Game.kt`, `Places.kt` and the globe parser. Put
new rules in `Game.kt` with a test rather than in the view model, which can't be unit-tested.

Assets and tools:
- `assets/places.csv` ← `tools/build_places.py` (GeoNames `cities15000`).
- `assets/globe.csv` ← `tools/build_globe.py path/to/land-110m.json app/src/main/assets/globe.csv` (world-atlas).
- `res/drawable-nodpi/banner.png` and `icon.png` ← `tools/make_art.swift` (uses `res/font/outfit.ttf`).
- Run Python on downloaded data with `python3 -I`, from outside the download's folder. Ask before downloading.

## Design (agreed with the user)

- "Noite" palette: background `#0B1220`, cards `#1C2A44`, accent yellow `#FFC83D`, scrim `#E60B1220`.
- Logo "ADONDE?" in Outfit weight 800 with a yellow map pin as the O. Outfit is only for the logo.
- On the playing screens everything sits in one pill at the bottom: D-pad icons + 14 sp hints, with
  "Ronda X de Y" in a tab hanging below it. The D-pad icon highlights what each control does (only up/down in
  zoom mode).
- Hints use icons of the remote's buttons instead of words: `DpadIcon(centre = true)` for OK, `BackIcon` for
  Back.
- For bigger visual changes, the user likes mockups first.

## Google Maps rules

- Street View is billed per panorama load (Dynamic Street View): 5,000 free a month. Don't load panoramas in
  loops when testing.
- On Google TV the SDK only has its legacy renderer, which never zooms out past about 168° of longitude.
- Panoramas with 22-character ids are user uploads and are skipped.
- **Keep Google's notices**: the Google logo, copyright and Street View's "Report a problem" link. The user
  asked to hide the link once; Google requires it, so it stays.

## Testing

- **Screenshot before sending keys to the TV** (`adb exec-out screencap -p > shot.png`) and check what's in
  front (`adb shell dumpsys activity activities | grep -m1 topResumedActivity`). If someone is playing,
  stop and ask. A scripted walk once mixed with someone's remote presses and changed their saved setup.
- `input keyevent` is too short to count as a hold. Hold OK with
  `adb shell input keycombination -t 1600 KEYCODE_DPAD_CENTER KEYCODE_F12` (the second key is only there
  because the command needs two). A real remote's key repeats differ, so the user checks holds by hand.
- The emulator (`GoogleTV_34`) is fine for layout, but its network can't be cut, so the "no internet" screen
  can only be tested on the TV.
- Performance: `adb shell am start -W -n com.jjmluz.adonde/.MainActivity` for start time,
  `dumpsys gfxinfo com.jjmluz.adonde` for frames.
- **When the user says "install, don't test", build and install only.**
- Leave the TV as you found it. If a test changes the user's setup (players, names, avatars, rounds), restore
  it and say so.

## Working conventions

- Match the surrounding code: Kotlin, Compose foundation (`BasicText`, `Canvas`; no Material), KDoc on
  non-obvious functions, comments explain *why*. Colours come from the named values in `Screens.kt`.
- Commit after each verified change, with a descriptive message, ending with the co-author line given by the
  harness. Push to `origin` (the user's private GitHub repo) when they ask.

## Still unverified

- On the TV: the reveal and final screens, the "no internet" screen (disconnect the TV), and the duplicate
  avatar fix.
- The mockups (a claude.ai design artifact) still show the old hold messages.
