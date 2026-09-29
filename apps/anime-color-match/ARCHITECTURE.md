# ForgeBuild Engine — Architecture

Reusable Android base (Kotlin + Jetpack Compose) that every generated app
copies into `apps/<slug>/`. Single source of truth for theme, icons,
permissions, data layer, file saves, and screen security.

## Theme: Material 3 Expressive (single system, no selector)

The Engine ships exactly **one** theme system — **Material 3 Expressive** — and
exposes **no** theme selector: every generated app runs `ForgeBuildTheme`.

- `ui/theme/Theme.kt` uses `MaterialExpressiveTheme` +
  `MotionScheme.expressive()` + expressive `Shapes()`. Dependency:
  `androidx.compose.material3:material3:1.5.0-alpha28` — the Expressive PUBLIC
  APIs (expressive theme/motion + wavy indicators) are NOT in stable 1.4.0
  (verified against the artifacts); they exist only on the 1.5.0 alpha train,
  so the Compose stack is pinned to `1.13.0-alpha01` (no BOM, since BOM
  2026.09.00 still pins material3 to 1.4.0). Wavy/loading indicators:
  `ui/components/EngineProgress.kt`. `ForgeBuildTheme` keeps its signature and
  dynamic-color/dark behavior.

(History: an experiment adding two alternate theme systems plus an Engine-level
selector was fully reverted by the `forgebuild-material-only-revert` build —
files, Gradle dependencies, fallback logic, and docs all removed. Trail in
BUILD_STATE.json.)

## Toolchain

compileSdk/targetSdk 37, AGP 9.4.0, Kotlin 2.4.20, Gradle 9.6.0, minSdk 26,
Java 17 bytecode (CI uses JDK 17; AGP 9 runs on it). Compose stack pinned to
`1.13.0-alpha01` (see theme note). `compileSdk = 37` is REQUIRED by material3
1.5.0-alpha28 (`minCompileSdk=37`) — do not lower. Engine-level deps are
limited to the Compose/material3 stack + coroutines (cache-first data layer) —
nothing else.

**AGP 9 built-in Kotlin (STANDING, 2026-09-16 fix).** AGP 9 has Kotlin support
BUILT IN — the legacy `org.jetbrains.kotlin.android` plugin must NOT be
declared in the root build file nor applied in `app/build.gradle.kts`; applying
it hard-fails the build (`"no longer required for Kotlin support since AGP
9.0"`). Kotlin compilation, the `kotlin { compilerOptions { ... } }` DSL, and
the `org.jetbrains.kotlin.plugin.compose` plugin all work without it. NOTE:
this is toolchain-specific — apps generated on the older snapshot (AGP 8.5.2 /
Kotlin 2.0.20: calcom, clipforge-android, forgehouse50, tapcounter) still
REQUIRE the kotlin-android plugin, so this fix is intentionally NOT
back-propagated to them; it applies to engine/ and every app copied from the
current engine (AGP 9.x).

## Structure

```
engine/
  app/src/main/java/com/forgebuild/engine/
    ui/theme/        Theme.kt (Material 3 Expressive — the only theme)
    ui/components/   EngineProgress.kt (expressive wavy/loading)
    ui/icons/        EngineIcons.kt + EngineIconsGenerated.kt (Material Symbols)
    data/            CacheFirstStore.kt (cache-first, background-refresh)
    security/        ScreenSecurity.kt (per-screen FLAG_SECURE)
    files/           SafeSave.kt (explicit permission-scoped saves)
    permissions/     Permissions.kt (conditional permission suite)
  tools/             gen_icons.py, make_adaptive_icon.py, make_keystore.sh
```

## Standing requirements

- Cache-first, background-refresh data pattern for any live-data app.
- Per-screen `FLAG_SECURE` via `ScreenSecurity`.
- Explicit, permission-scoped saves via `SafeSave` (never DownloadManager).
- Conditional permissions module (`PermissionWiring`), all opt-in.
- No emoji anywhere in generated UI — Material Symbols only.
- Dynamic color, dark mode, adaptive icons must keep working.
