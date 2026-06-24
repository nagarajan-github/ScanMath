# ScanMath 📷➗

A modern Android app that reads a handwritten or printed calculation through the
camera and instantly shows the answer. Built with **Kotlin + Jetpack Compose**,
**CameraX**, and **Google ML Kit** on-device text recognition — no internet, no
API keys, no cost.

## What it does

1. Asks for camera permission (with a friendly rationale screen).
2. Shows a live camera preview with a framing guide.
3. On tap, captures a photo, runs on-device OCR, finds the calculation, and
   evaluates it.
4. **Or** pick an existing photo from your gallery (📷 gallery button, lower
   left) — no extra permission needed thanks to the Android photo picker.
5. Shows the result in a bottom sheet.

It understands two layouts from your sample image:

| Input on paper        | Detected expression  | Result |
|-----------------------|----------------------|--------|
| `10 + 30 + 40`        | `10 + 30 + 40`       | `80`   |
| `100` / `+25` / `+45` / `-20` (stacked) | `100 + 25 + 45 - 20` | `150`  |
| `100` / `25` / `45` / `20` (plain column, no symbols) | `100 + 25 + 45 + 20` | `190`  |
| `10 20 30` (no symbols) | `10 + 20 + 30`     | `60`   |

**No-symbol rule:** when two numbers sit next to each other with no operator
between them, they are added. So a plain column or a row of numbers is summed.

Two problems separated by an `(or)` marker are solved independently, and two
self-contained equations on separate lines stay separate.

It also handles `× ÷ − +`, parentheses, decimals, `%`, powers (`^`) and operator
precedence (e.g. `2 + 3 * 4 = 14`).

## Project structure

```
app/src/main/
├── AndroidManifest.xml                 Camera permission + ML Kit model hint
├── java/com/scanmath/app/
│   ├── MainActivity.kt                 Compose entry point
│   ├── math/
│   │   ├── ExpressionEvaluator.kt      Recursive-descent arithmetic evaluator
│   │   └── MathParser.kt               OCR text → clean expressions → results
│   ├── ocr/
│   │   └── MathTextRecognizer.kt       ML Kit Latin OCR (coroutine wrapper)
│   └── ui/
│       ├── ScanScreen.kt               Permission flow + camera UI + results
│       ├── CameraController.kt         CameraX preview + capture
│       ├── Extensions.kt               Small helpers
│       └── theme/Theme.kt              Material 3 theme (dynamic color)
└── res/                                Icons, strings, theme
```

## Build & run

**Requirements:** Android Studio (Koala or newer), a device/emulator on
**Android 7.0 (API 24)** or higher with a camera.

1. Open the project folder in Android Studio (`File ▸ Open`).
2. Let Gradle sync (it downloads CameraX, Compose, and ML Kit automatically).
3. Connect a device or start an emulator and press **Run** ▶.

> The first scan may take a moment while Play Services downloads the ~few-MB OCR
> model. After that it works fully offline.

### Get the APK

There are three ways to produce `app-debug.apk`:

**A. Android Studio (easiest)**
Open the project, then `Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)`.
When it finishes, click **locate** in the notification to find the APK.

**B. Command line** (needs JDK 17 + Android SDK installed locally)
```bash
gradle wrapper --gradle-version 8.9   # one time, if gradlew is missing
./gradlew assembleDebug               # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                # build + install on a connected device
```

**C. GitHub Actions — build in the cloud, no local setup**
Push this project to a GitHub repo. The included
`.github/workflows/build-apk.yml` runs automatically (or trigger it from the
**Actions** tab). When the run finishes, download the APK from the run's
**Artifacts** section (`ScanMath-debug-apk`).

> A *debug* APK installs directly on a phone with "Install unknown apps"
> enabled. For Play Store distribution you'd build a signed *release* AAB.

## How recognition works

`MathTextRecognizer` feeds the captured bitmap to ML Kit. The returned text is
passed to `MathParser`, which:

- normalizes OCR glyphs (`× x · → *`, `÷ → /`, `− – → -`, drops thousands commas);
- repairs misread digits (e.g. the letter `O → 0`, `l → 1`, `S → 5`) so numbers
  aren't lost;
- stitches stacked/column numbers into one expression, inserting `+` between any
  two adjacent numbers that have no operator between them;
- keeps self-contained equations separate and splits on blank lines / `(or)`;
- evaluates each with `ExpressionEvaluator` and formats whole numbers cleanly.

The capture uses CameraX's highest-resolution strategy, and the result sheet
shows the exact recognized text so you can see if anything was misread.

## Tips for best accuracy

- Good, even lighting and a steady hand.
- Fill the frame with just the calculation.
- Clear digits work best; very messy handwriting may misread.

## Tech

Kotlin 1.9 · Jetpack Compose (Material 3) · CameraX 1.3 ·
ML Kit Text Recognition 16 · min SDK 24 · target SDK 34
