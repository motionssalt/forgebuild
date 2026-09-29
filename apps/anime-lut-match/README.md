# forgebuild-engine

ForgeBuild Engine — reusable Android base: Kotlin + Jetpack Compose, Material 3 Expressive theme, Material Symbols icons, conditional permissions module, adaptive-icon generator, lean Gradle, release-APK GitHub Actions.

## Theme: Material 3 Expressive (the one and only)

The Engine ships exactly **one** theme — **Material 3 Expressive** — via
`com.forgebuild.engine.ui.theme.ForgeBuildTheme`. There is no theme selector;
every generated app runs Material 3 Expressive.

`ForgeBuildTheme` wraps `MaterialExpressiveTheme` with `MotionScheme.expressive()`
and the expressive `Shapes()` defaults; dynamic color (Android 12+) and dark
mode are preserved. Expressive progress/loading lives in
`com.forgebuild.engine.ui.components.EngineProgress`
(`LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`,
`LoadingIndicator` — official components, no hand-rolled approximations).

Dynamic color, dark mode, adaptive icons, and the `FLAG_SECURE` / permissions /
SAF-save modules are all part of the base. No emoji anywhere in generated UI —
Material Symbols via `com.forgebuild.engine.ui.icons.EngineIcons`.
