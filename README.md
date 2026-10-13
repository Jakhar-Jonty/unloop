# Unloop

A minimalist Android home screen that helps you use your phone less, without blocking your life.

Dark, text-only home screen with a NeoPOP-style UI (lime on black), built in Kotlin + Jetpack Compose.

## Features

- **Minimal home** – clock, today's screen time, up to 6 favourite apps as text, and a random quote after every unlock
- **5-second pause** – a breathing countdown and "why are you opening this?" before distracting apps; it gets longer with each open that day
- **Pause everywhere** – the pause also catches apps opened from notifications, Recents or links (Accessibility service; reads which app is open, never screen content)
- **Daily app limits** – a block screen once an app's time is used up for the day
- **Focus mode** – home shrinks to essential apps, with optional Do Not Disturb
- **Schedules** – Work, Wind down and Sleep start Focus mode automatically
- **Strict mode** – lock settings for 1–7 days: they can only get stricter
- **Screen time and weekly reflection** – daily total, unlocks, top apps, 7-day chart, share your week
- **Streak plant** – grows each day you stay under your limit
- **Gestures** – swipe down for notifications, swipe right for camera, double-tap to lock
- **English and Hindi**

## Build

Requires Android Studio (or JDK 17+ and the Android SDK).

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK (currently signed with the debug key)
```

Open the app, set it as the default home app, and allow Usage access when asked.

## Project layout

```
app/src/main/java/com/unloop/launcher/
  data/       apps, preferences, screen-time history, focus, schedules, limits
  service/    accessibility service for "pause everywhere"
  ui/         theme, NeoPOP components, screens
  MainActivity.kt, PauseActivity.kt, LauncherViewModel.kt
```

## Fonts

Bundled fonts are licensed under the [SIL Open Font License 1.1](https://openfontlicense.org):
[Bricolage Grotesque](https://github.com/ateliertriay/bricolage) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono).
