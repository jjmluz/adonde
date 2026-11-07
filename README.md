# Adonde

A place-guessing game for Android TV, played with the remote's D-pad by 1 to 6 people on one screen. Inspired by GeoGuessr. For personal use: it's sideloaded, not on Google Play.

Each round:

1. A random Street View location fills the screen, with no time limit. Everyone looks around together.
   The arrows look around, and OK switches between looking and zooming.
2. Holding OK moves on to the guesses, once everyone is ready.
3. Each player in turn moves a full-screen map under a fixed pin. OK switches between moving and zooming,
   and holding OK confirms. A different player goes first each round.
4. The real place is revealed on the map with everyone's pins, along with the round's distances and the
   running totals.

After the last round comes the final ranking. The fewest cumulative kilometres wins.

Back pauses the game. Pressing it again on the pause screen leaves the game.

## Languages

The game follows the TV's language: English, Portuguese (from Portugal, also used on Brazilian Portuguese
TVs), Spanish and French. Any other language falls back to English. The text is in
`app/src/main/res/values*/strings.xml`, with English in `values/`. Distances use the number style of the
language the text is in ("1,234 km" in English, "1 234 km" in Portuguese), and players without a name of
their own are shown as "Player 1", "Jogador 1" and so on.

To add a language, copy `values/strings.xml` to `values-xx/`, translate it, and add the code to
`localeFilters` in `app/build.gradle.kts`.

To try a language without changing the device's own:

```bash
adb shell cmd locale set-app-locales com.jjmluz.adonde --locales fr-FR
```

`--locales ""` goes back to following the device.

## Build and install

Needs Android Studio's JDK (21) and the Android SDK. `local.properties` holds the SDK path and the Maps key,
and stays out of git:

```properties
sdk.dir=/path/to/Android/sdk
MAPS_API_KEY=your_key
```

The key comes from a Google Cloud project with billing and the Maps SDK for Android enabled. Restrict it to
Android apps, with the package `com.jjmluz.adonde` and the SHA-1 of your debug key, and cap Dynamic Street
View in the Cloud console's quotas.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest installRelease
```

Use the release build on the TV: it starts about four times faster than `installDebug`. Both are signed with
the debug key, so one installs over the other and the Maps key works with either.

If several devices are connected, pick one with `ANDROID_SERIAL` (see `adb devices`).

## Layout

- `Game.kt`: the rules, with no Android code. Tested in `GameTest.kt`.
- `Places.kt`: picks where to look for a panorama. Also tested in `GameTest.kt`.
- `GameViewModel.kt`: the game's state, and what the remote's keys do.
- `Screens.kt`: the Compose screens.
- `Maps.kt`: the Street View panorama and the Google maps.

## Testing on an emulator

`adb shell input keyevent` is too quick to count as holding a key. This holds one for as long as you like
(the second key is only there because the command needs two):

```bash
adb shell input keycombination -t 1600 KEYCODE_DPAD_CENTER KEYCODE_F12
```

If the emulator only ever shows the "no place found" screen, it has resumed from a stale snapshot (its clock
is days behind and hostname lookups hang). Cold-boot it with `emulator -avd <name> -no-snapshot`.

## Google Maps limits

- **Street View API:** with an EEA billing address, Google doesn't allow the Static or Tile Street View APIs
  on or next to a map. The panorama in the Maps SDK is what it recommends instead, and it's billed as Dynamic
  Street View: 5,000 free a month, then $14 per 1,000. Map loads in the SDK are free.
- **User uploads:** these turn up even with the outdoor filter, and their credit can name the place, so
  they're skipped by the length of their panorama id. Searches start close to towns, with a small radius, to
  land on Google's own roads: about 3 in 4 do.
- **Map zoom:** on Google TV the SDK only has its older renderer, which never zooms out past about 168° of
  longitude. The whole world can't be on screen at once, so the reveal centres on the answer when the pins
  don't all fit.

## Places

`app/src/main/assets/places.csv` is built by `tools/build_places.py` from
[GeoNames](https://www.geonames.org/)' `cities15000` file (CC BY 4.0), keeping countries with wide Street
View coverage.

`app/src/main/assets/globe.csv`, the dots of the spinning globe, is built by `tools/build_globe.py` from
`land-110m.json` in the [world-atlas](https://github.com/topojson/world-atlas) package, which is
[Natural Earth](https://www.naturalearthdata.com/)'s public-domain land data.

`tools/make_art.swift` draws the launcher banner and icon.

The logo is set in [Outfit](https://github.com/google/fonts/tree/main/ofl/outfit), under the SIL Open Font
License 1.1.
