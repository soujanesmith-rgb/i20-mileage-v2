# i20 Mileage Tracker — Starter Kit

A starter codebase for an Android app that auto-tracks your driving distance via GPS
and calculates real mileage (km/l) from your fuel fill-ups — no OBD dongle or MID required.

## How the math works

Since your car has no accessible fuel-level sensor, mileage is calculated the only
reliable way possible without one:

```
mileage (km/l) = GPS distance driven between two FULL-TANK fill-ups
                 ÷ liters added at the second fill-up
```

You only ever manually enter one thing: liters filled, each time you fuel up
(mark whether it was a full tank or partial). Everything else — trip detection,
distance logging — happens automatically in the background.

## Project structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties   Root Gradle config
.github/workflows/build-apk.yml   CI: builds a debug APK automatically on every push
.devcontainer/                    Codespaces config: auto-installs Android SDK + Gradle

app/
  build.gradle.kts        App module Gradle config (dependencies, SDK versions)
  src/main/AndroidManifest.xml
  src/main/res/values/strings.xml
  src/main/java/com/example/i20mileage/
    MainActivity.kt         Entry point; shows permission screen until fully granted

    data/
      Entities.kt        Trip + FuelLog Room entities
      Daos.kt             Database queries
      AppDatabase.kt      Room database singleton

    service/
      TripLoggingService.kt   Foreground service: auto start/stop trip detection + GPS distance accumulation

    util/
      MileageCalculator.kt    Core mileage math (latest window + lifetime average)

permissions/
  PermissionState.kt         Checks current grant state (location, notifications, battery exemption)
  PermissionOnboardingScreen.kt   Step-by-step Compose screen that requests each permission in the right order

MainActivity.kt      Example wiring: shows PermissionOnboardingScreen until fully granted, then your home screen

ui/
  MileageViewModel.kt     Wires DB + calculator to your Compose screens
```

## Permission handling — the part that trips people up

Android splits location permission into stages, and gets stricter with each OS version:

- **Foreground location** (`ACCESS_FINE_LOCATION`) — a normal runtime permission, request it
  first, always.
- **Background location** (`ACCESS_BACKGROUND_LOCATION`) — required for GPS tracking to
  keep working when the screen is off or the app isn't in focus. Android **will not let you
  request this together with foreground location** — it must be a separate step, after
  foreground is already granted.
  - On **Android 10 (API 29)**, requesting it still shows an in-app dialog with an
    "Allow all the time" option.
  - On **Android 11+ (API 30+)**, Google removed that option from the runtime dialog
    entirely. The only way to get it granted is to send the user to the app's Settings
    page and have them manually pick "Allow all the time" — `PermissionOnboardingScreen`
    does this automatically based on SDK version.
- **Notifications** (`POST_NOTIFICATIONS`, Android 13+/API 33+) — needed because your
  foreground service must show a persistent notification; without the permission the
  service notification silently fails to display (though the service still runs).
- **Battery optimization exemption** — not a "permission" in the Manifest sense, but just
  as important: without it, Android's Doze/App Standby will throttle or kill background
  GPS updates after a while, especially Hyundai/Xiaomi/OnePlus-style aggressive OEM battery
  managers on top of stock Android behavior. The screen sends users to the system's
  ignore-list settings rather than popping a direct request dialog, since Play Store
  restricts which apps may use `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` directly.

`PermissionOnboardingScreen` walks through all of this as an ordered checklist, re-checks
grant state every time the screen resumes (so it picks up changes made in system Settings),
and calls `onAllRequiredGranted()` once foreground + background location + notifications
are all in place. `MainActivity.kt` shows the minimal wiring — swap `HomeScreenPlaceholder`
for your real dashboard.

**One more thing specific to your phone's OEM:** if this is a Xiaomi/Redmi/OnePlus/Vivo/Oppo
device, stock Android's battery exemption isn't always enough — these OEMs run their own
extra-aggressive background-app killers on top. Check
[dontkillmyapp.com](https://dontkillmyapp.com) for your exact device model and add those
extra steps to your onboarding flow if needed.

## Building this online — no Android Studio required

There are two ways to build this without installing anything locally. Use both together
for the smoothest experience.

### Path A: GitHub Actions (fastest — get an installable APK with zero setup)

1. Create a free GitHub account if you don't have one, then create a new **public or
   private repository** (e.g. `i20-mileage`).
2. Upload this entire folder's contents to that repo. Easiest way without installing git:
   on the repo's GitHub page, click **Add file → Upload files**, then drag this whole
   `i20-mileage` folder in (GitHub preserves the folder structure).
3. Go to the **Actions** tab of your repo. A workflow called "Build Debug APK" will
   already be there (from `.github/workflows/build-apk.yml`) — click **Run workflow** if
   it didn't trigger automatically after your upload.
4. Wait ~2–3 minutes for the build to finish (green checkmark), then open that run and
   download the **`i20-mileage-debug-apk`** artifact under "Artifacts" — it's a zip
   containing `app-debug.apk`.
5. Transfer the APK to your phone (email it to yourself, Google Drive, USB, etc.), enable
   **"Install unknown apps"** for whichever app you use to open it, and install.

Every time you push a code change, this repeats automatically — no local build tools ever
needed. This alone is enough to get a working app on your phone.

### Path B: GitHub Codespaces (for actually writing/editing code in the browser)

Path A only builds — it doesn't give you an editor. For a full browser-based dev
environment with a real terminal (to edit files, test builds instantly, and debug Gradle
errors before pushing):

1. On your repo's GitHub page, click **Code → Codespaces → Create codespace on main**.
2. This launches a full VS Code environment in your browser. The `.devcontainer/` config
   in this project automatically installs the Android SDK, JDK 17, and Gradle the first
   time the Codespace starts (takes a few minutes on first launch — grab a coffee).
3. Once it's ready, open the built-in terminal and run:
   ```
   gradle assembleDebug
   ```
4. The APK appears at `app/build/outputs/apk/debug/app-debug.apk`. Right-click it in the
   file explorer sidebar and choose **Download** to get it onto your computer, then
   transfer it to your phone as in Path A step 5.
5. Edit any `.kt` file directly in the browser editor, re-run `gradle assembleDebug`, and
   iterate — normal IDE experience, just running remotely.

GitHub gives personal accounts a free monthly quota of Codespaces hours (currently 60
hrs/month on the free tier) — plenty for a project like this.

**Note on `namespace`/`applicationId`:** both are set to `com.example.i20mileage` in
`app/build.gradle.kts` and `AndroidManifest.xml`. Fine for personal builds; if you ever
want a unique app on your phone (e.g. to avoid clashing with a later rename), search-and-
replace `com.example.i20mileage` across the project with something like
`com.yourname.i20mileage`.



## What's NOT included yet (your next steps)

- The actual Compose UI screens (home dashboard, add-fuel form, trip history list) —
  happy to build these next if you want.
- Runtime permission request flow (there's Compose boilerplate for this, e.g. via
  Accompanist Permissions or the standard `ActivityResultContracts.RequestPermission`).
- Auto-restart of the tracking service on phone reboot (a `BOOT_COMPLETED` receiver).
- Battery optimization exemption prompt — Android will kill background GPS services
  aggressively unless the user whitelists your app in battery settings. You'll want
  to prompt for this on first launch.
- Charts screen for mileage trends over time (MPAndroidChart dependency is already
  in the gradle snippet).
- OBD-II "live mode" as an optional upgrade path (see the OBD notes below).

## Tuning trip detection

In `TripLoggingService.kt`, these constants control sensitivity — tune them based on
real-world testing:

- `MOVING_THRESHOLD_MPS` — speed to count as "driving" (default ~5 km/h)
- `STARTUP_HOLD_MS` — how long you must be moving before a trip starts (avoids
  logging trips for just walking to your car)
- `STOP_HOLD_MS` — how long stationary before a trip ends (default 3 min — tune
  this so red lights/traffic jams don't split one trip into many)

## If you later add an OBD-II dongle

Test PID support first using a free app like "Car Scanner" before writing any code:
- `01 5E` (engine fuel rate, L/h) — if supported, gives near-real-time mileage,
  same as a factory MID
- `01 2F` (fuel level %) — usually too coarse for real-time use, but fine for
  long-term average cross-checks

If `01 5E` works on your car, that's a much better core measurement than GPS+fill-up
math, since it doesn't depend on you remembering to log every full tank.
