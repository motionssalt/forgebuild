# ForgeBuild Material 3 / M3 Expressive Design Discipline

This document is the Engine's **permanent** design-system contract. It records the
official Material Design 3 / Material 3 Expressive discipline that the Engine
implements, so every app generated from `engine/` inherits it automatically.
Research basis and source list: `BUILD_STATE.json` → `forgebuild-material-discipline.step-01`.

> Standing rule (permanent): never hand-roll or approximate an official
> component or design feature that genuinely exists. Use the real library API.

## What the Engine guarantees (per generated app)

`ForgeBuildTheme` (in `engine/.../ui/theme/Theme.kt`) wraps every app in the
official `MaterialExpressiveTheme` with:

| Discipline area | Engine implementation | Official basis |
|---|---|---|
| Dynamic color | `ColorTokens.resolveColorScheme()` → `dynamicLight/DarkColorScheme` (API 31+), fallback `expressiveLightColorScheme` / `darkColorScheme` | m3 color roles; Compose dynamic color |
| Full role set | primary/secondary/tertiary/error (+on-*, +container), 5 surface-container levels, surface bright/dim, inverse*, outline(+variant), fixed roles | m3 color roles |
| Shape | `ShapeTokens.expressive` — scale 4/8/12/16/20/28/32/48dp applied **by component role** via `MaterialTheme.shapes` | m3 shape scale (incl. Expressive increased tiers) |
| Decorative shape | Official `MaterialShapes` 35-shape library + `Morph` for shape-morph — never hand-rolled polygons | m3 Expressive shape library |
| Tonal elevation | `ElevationTokens` — levels 0/1/3/6/8/12dp; `tonalContainerColor()` maps levels to `surfaceContainer*` roles (surface-tint token is deprecated) | m3 elevation tokens |
| Type scale | `TypographyTokens.scale` — full official 15-role scale (display/headline/title/body/label × L/M/S) applied by role | m3 type scale |
| Spacing/layout | `SpacingTokens` — 4dp baseline scale + canonical margins (16dp compact, 24dp medium/expanded) | m3 layout guidance |
| Motion | `MotionScheme.expressive()` applied globally; `MotionTokens` exposes the six official specs (default/fast/slow × spatial/effects) for custom animations | m3 Expressive motion physics |
| Expressive components | `EngineProgress` (wavy linear/circular + morphing `LoadingIndicator`), `EngineExpressive` (`ButtonGroup`, `SplitButton`, `FloatingActionButtonMenu`); Button/FAB/Menu/Slider/TopAppBar/ListItem expressive behavior comes free under the theme | material3 1.5.0-alpha expressive APIs |

## Rules for every generated app

1. **Always** build UI inside `ForgeBuildTheme`. Never bypass it with raw colors,
   raw `dp` corner radii, or per-screen font sizes.
2. Use `MaterialTheme.colorScheme.<role>` (e.g. `primary`, `surfaceContainerHigh`,
   `onSurfaceVariant`) — never hardcoded hex colors.
3. Use `MaterialTheme.typography.<role>` for all text.
4. Use `MaterialTheme.shapes.<tier>` for custom surfaces; use the `MaterialShapes`
   library (or `Morph`) for decorative shapes.
5. Use `SpacingTokens.Spacing.*` / `contentMargin()` for padding and margins —
   the 4dp baseline, not arbitrary literals.
6. Use `ElevationTokens.tonalContainerColor(level)` for custom elevated
   containers; let official components keep their default elevation.
7. Use `MotionTokens` specs for any custom animation so it matches the system
   spring physics instead of ad-hoc easings/durations.
8. Prefer expressive components (`EngineLinearWavyProgress`,
   `EngineCircularWavyProgress`, `EngineLoadingIndicator`, `EngineButtonGroup`,
   `EngineSplitButton`, `EngineFabMenu`) over legacy flat counterparts.

These rules are also enforced as a **permanent prompt rule** in the Dashboard
templates (`site/app.js`), so every build/extend/resume session is instructed to
follow them by default.
