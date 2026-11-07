# AGENTS.md

Guide for coding agents working on Adonde. Read `README.md` for the game's rules and the Google Maps limits.

## The user and the device

- A family game for one TV: a Google TV Streamer (`kirkwood`, Android 14, 1920×1080 UI, 32-bit userspace,
  modest GPU) in a family home in Portugal, played with the remote's D-pad. Sideloaded, never on Google Play.
- The user writes in English. **The game follows the TV's language**: English (`res/values`, the fallback),
  Portuguese from Portugal (`values-pt`, also used for Brazilian), Spanish (`values-es`) and French
  (`values-fr`). The family's TV is in Portuguese: "Ronda 1 de 5", "Manter premido para adivinhar",
  "Voltar ao início". The user picks the Portuguese wording; don't rephrase it unasked. A new string goes in
  all four files, under `shared/src/main/res/`; a new language also goes in `localeFilters` in
  `app/build.gradle.kts` and `mobile/build.gradle.kts`. The phone's own strings are in a block at the end
  of each file; their Portuguese hasn't been checked by the user yet.
- Number marks come from the `decimal_mark` / `group_mark` strings, not the locale, so a German TV gets
  English text with English numbers. An empty player name means the default "Player N" in the TV's language.
- To see another language without changing the device's:
  `adb shell cmd locale set-app-locales com.jjmluz.adonde --locales fr-FR` (`--locales ""` resets it).
- There is also a phone and tablet app (`mobile`), played by touch and passed from player to player. It
  is on the user's Pixel 8 Pro (`192.168.1.131`, wireless debugging: the port changes, see `adb devices`),
  and has been tried on the `Phone_37` emulator (a Pixel 8-sized Android 37 phone, which can also be resized to a tablet with `adb shell wm size 2560x1600` and
  `wm density 320`; reset both afterwards).
- There is also an iPhone app (`ios/`, SwiftUI), on the `ios` branch: **don't merge it into `main` until the user
  says so**. iPad comes next; Apple TV was dropped, because Google's Maps SDK doesn't run on it.
- The home screen is the user's own launcher (`com.jj.launcher`, `~/projects/jj-launcher`). Adonde is on its
  third row, with the banner from `res/drawable-nodpi/banner.png`.
- The TV is in use by the family. Anything you do on it is visible.

## Build, install, run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # shell Java is too old
ANDROID_SERIAL=192.168.1.94:5555 ./gradlew testDebugUnitTest installRelease
ANDROID_SERIAL=emulator-5556 ./gradlew :mobile:installRelease                # the phone app
```

- Four modules: `core` (the rules in plain Kotlin Multiplatform: JVM for Android, plus iOS), `shared` (an
  Android library with the state, maps, text, assets and font, which re-exports `core`), `app` (the TV) and
  `mobile` (phones and tablets, minSdk 31). Both apps are `com.jjmluz.adonde`, so the Maps key covers them,
  and installing one replaces the other. **`mobile/build.gradle.kts` skips its install tasks unless they're
  named with `:mobile:`**, so the plain `installRelease` above only ever installs the TV app. Keep it that
  way: the phone app over the TV app would leave the family without the game.

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

The iPhone app (needs Xcode; `ios/Config/Secrets.xcconfig` holds `MAPS_API_KEY`, a separate key restricted to
iOS apps with the bundle id `com.jjmluz.adonde`; **never print or commit it**):

```bash
cd ios && xcodebuild -project Adonde.xcodeproj -scheme Adonde -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' -derivedDataPath build build
```

- A build phase runs `./gradlew :core:embedAndSignAppleFrameworkForXcode` to build `core` as the static
  `AdondeCore` framework, using Android Studio's JDK when `JAVA_HOME` isn't set. Simulator builds are Apple
  silicon only (`EXCLUDED_ARCHS` x86_64): `core` has no `iosX64` target.
- `ios/Adonde/Localizable.xcstrings` is generated: change the Android `strings.xml` files, then run
  `python3 -I tools/build_ios_strings.py`. Keys are the Android names; arrays become `name_0`, `name_1`, ...
- The project uses a synchronized folder: new files in `ios/Adonde/` join the app without editing the project.
  `places.csv`, `globe.csv` and `outfit.ttf` are referenced from `shared/`, not copied.
- The Kotlin tests also run on the iOS simulator: `./gradlew :core:iosSimulatorArm64Test`. Kotlin/Native
  rejects test names with commas and some other punctuation.

## Code map

`core/src/commonMain/kotlin/com/jjmluz/adonde/` (package `com.jjmluz.adonde`; no JVM-only APIs such as `Math` or
`String.format`, because it's also built for iOS as the `AdondeCore` framework):

| File | Role |
|---|---|
| `Game.kt` | The rules: players, rounds, turn order, distances (`distanceKm`, `radians`), `formatKm`, `withUniqueAvatars`, `OkButton` (tap vs hold). |
| `Places.kt` | `PlacePicker`: picks a town from `assets/places.csv` and a point near it to search for a panorama. |
| `GlobeDots.kt` | `parseGlobe`: the globe's dots from `assets/globe.csv`. |

`shared/src/main/kotlin/com/jjmluz/adonde/` (same package, resources in `com.jjmluz.adonde.shared.R`):

| File | Role |
|---|---|
| `GameViewModel.kt` | The game's state and flow, open for the TV to extend (`Screen`: SPLASH, SETUP, EXPLORE, GUESS, REVEAL, FINAL; pause). Actions such as `changeRounds`, `startGuessing`, `placeGuess`, `next`. Saves the setup to SharedPreferences only when a game starts. |
| `Maps.kt` | `StreetViewPanoramaView` and `MapView` inside `AndroidView`: panorama search and filtering, the guess map with a fixed pin, the reveal map (re-fits when it changes size), pin bitmaps. `gestures` turns touch on; a `controls` slot lets the TV drive the camera. |
| `Theme.kt` | The "Noite" palette, the logo, `NameDialog`, `Avatar`, `ScoreRow`, `Label`, `Message`, `nameOf`, `km`. |
| `Globe.kt` | The spinning dot globe on the splash and search screens, drawn on a `Canvas` each frame. |

`app/src/main/kotlin/com/jjmluz/adonde/` (the TV):

| File | Role |
|---|---|
| `TvViewModel.kt` | What each key does: `Control` (MOVE, ZOOM), the setup's selected row, holding OK, the arrows held. |
| `MainActivity.kt` | Single activity; sends keys from `dispatchKeyEvent` to the view model. |
| `Controls.kt` | `TvPanorama` and `TvGuessMap`: the D-pad turning the panorama and moving the map, frame by frame. |
| `Screens.kt` | The TV's screens, `ControlHint` with the round tab, `DpadIcon`, `BackIcon`, the pause overlay. |

`mobile/src/main/kotlin/com/jjmluz/adonde/mobile/` (phones and tablets): `MainActivity.kt` (edge to edge, system
bars hidden, `configChanges` so turning the device doesn't reload the panorama) and `Screens.kt` (every screen,
in portrait and landscape; Back via `BackHandler`).

`ios/Adonde/` (the iPhone, a port of `mobile`): `GameModel.swift` (the twin of `GameViewModel`, saving the setup to
`UserDefaults` under the Android keys), `Screens.swift`, `Maps.swift` (`GMSPanoramaView` and `GMSMapView` in
`UIViewRepresentable`s), `Theme.swift` (palette, logo, `tr` for strings, `km`, pins), `Globe.swift`.

Tests: `core/src/commonTest/kotlin/com/jjmluz/adonde/GameTest.kt` (`kotlin.test`) covers everything in `core`. They run
on the JVM through `:core:jvmTest`, which `testDebugUnitTest` also runs. Put new rules in `Game.kt` with a test rather
than in the view models, which can't be unit-tested.

Assets and tools:
- `shared/src/main/assets/places.csv` ← `tools/build_places.py` (GeoNames `cities15000`).
- `shared/src/main/assets/globe.csv` ← `tools/build_globe.py path/to/land-110m.json shared/src/main/assets/globe.csv` (world-atlas).
- `app/src/main/res/drawable-nodpi/banner.png` and `icon.png` ← `tools/make_art.swift app/src/main/res/drawable-nodpi`
  (uses `shared/src/main/res/font/outfit.ttf`); a second argument, `ios/Adonde/Assets.xcassets/AppIcon.appiconset`,
  also draws the iOS icon. The phone's adaptive icon is `mobile/src/main/res/drawable/ic_launcher_pin.xml`.
- Run Python on downloaded data with `python3 -I`, from outside the download's folder. Ask before downloading.

## Design (agreed with the user)

- "Noite" palette: background `#0B1220`, cards `#1C2A44`, accent yellow `#FFC83D`, scrim `#E60B1220`.
- Logo "ADONDE?" in Outfit weight 800 with a yellow map pin as the O. Outfit is only for the logo.
- On the playing screens everything sits in one pill at the bottom: D-pad icons + 14 sp hints, with
  "Ronda X de Y" in a tab hanging below it. The D-pad icon highlights what each control does.
- Zoom mode (short OK press) only changes up/down: left/right keep turning or moving the map, so both screens
  work the same. In zoom mode the pill has a yellow outline and a + / − scale shows at the right edge.
- Hints use icons of the remote's buttons instead of words: `DpadIcon(centre = true)` for OK, `BackIcon` for
  Back.
- For bigger visual changes, the user likes mockups first.
- On the phone: the panorama and the map take the touch gestures themselves (drag, pinch). **Guess** and
  **Confirm** are taps rather than holds. The phone has no gesture or rename hints: the user took them out. A pause button sits top-left; the buttons stay above the bottom
  edge so Google's notices show. Overlays that must stop touches reaching the map use `blockTouches()`.

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
- If the emulator always shows "no place found", it has resumed from a stale snapshot: its clock is days
  behind (`adb shell date`) and hostname lookups hang. Cold-boot it:
  `$SDK/emulator/emulator -avd GoogleTV_34 -no-snapshot`.
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

The TV app after the split into modules was checked on the TV by the user (7 November 2026).

- The phone app on a real phone or tablet: pinching (adb can't pinch), the feel of the gestures, and the
  reveal board on a tablet with several columns.

- On the TV: the reveal and final screens, the "no internet" screen (disconnect the TV), and the duplicate
  avatar fix.
- The iPhone app on a real iPhone (needs signing with the user's Apple ID), and its pinch and drag feel.
- The mockups (a claude.ai design artifact) still show the old hold messages.
- The translations on the reveal and final screens.
