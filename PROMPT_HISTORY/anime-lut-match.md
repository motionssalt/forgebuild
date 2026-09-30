# PROMPT HISTORY — anime-lut-match (append-only)

## 2026-09-29 — Operator build prompt (NEW APP)
Build a production-quality Android app for copying/migrating color correction (LUT transfer) from one video edit to another, designed for AMV/video editors. Core: take the color grading from a selected frame of a reference edit and generate a color transformation (Cube LUT and/or HALD LUT) applicable to another video. Support automatic color matching AND manual After Effects-style Levels/Curves. Full workflow: import reference video -> select reference frame -> import target video -> select target frame OR search range -> intelligent frame matching (multi-stage, robust to resolution/aspect/crop/letterbox) -> automatic + manual color match (Levels/Curves per-channel) -> non-destructive effect stacking -> real-time still preview -> proxy video preview -> bake to .cube and/or HALD LUT -> export via Storage Access Framework -> preview on target video. Precision modes (Low/Standard/High) that actually change sampling density. Proper color management (sRGB/Rec.709, gamma, clipping). Android performance architecture (thumbnail/frame indexing, extracted-frame processing, no full-video decode into RAM, background ops, caching). Material 3 via ForgeBuildTheme. Research auto color-match + AE Levels/Curves before implementing. Build the actual working pipeline, not a mockup.

## 2026-09-29 — Build agent note
Slug 'anime-lut-match' chosen. Engine copy used (AGP 9 built-in Kotlin, compileSdk 37, material3 1.5.0-alpha28). Identity set; launcher activity fully-qualified. Signing secrets to be confirmed via read-only Actions API check (never regenerate).

## 2026-09-29 — Operator instruction (EXTEND / UPDATE, after anime-lut-match-v1)
"""
Okay, there are a few issues that I need fixed and some implementations I need added. First of all, I need the support for me to also import images. In case, like, it is images that I have. I want to be able to import not just video for reference and output, but also images for reference and also the other. And also, I need it so that the auto mode won't just happen, but I want to see a live log for auto mode and everything happening. Like, I want to see as everything is happening, like as it is adjusting the colors and everything before the output, you understand? Like something that I can visually see, plus a log that shows me what is happening behind the hood, you understand? And also, I noticed that it crashes for different reasons, but one of the reasons I found is that when I try to delete an effect, it crashes. Also the controls are not functioning. Like when I try to adjust the controls, it refuses to work. Also the app is not working properly. The auto mode is giving me something completely different, like completely spot. Like, the way it's supposed to work is that it analyzes the red and adjusts the two to match, analyzes the green, adjusts the two to match, analyzes the blue, adjusts the two to match. You understand? But it just does it immediately, and it just gives me a very bright, ugly variant of the reference. It's very disgusting. Also, I cannot close and edit. Like once I leave the page and import new resources, it's still going to open the previous settings. I don't like that, you understand?
"""

## 2026-09-30 — Operator instruction (EXTEND / UPDATE, after anime-lut-match-v2)
"""
Problem I'm facing now is that the frame matching is completely broken, it doesn't work as before. And the auto button is disabled and I'm not seeing the reference frame in the edit place.
"""

## 2026-09-30 — Operator instruction (EXTEND / UPDATE, after anime-lut-match-v2)
"""
Problem I'm facing now is that the frame matching is completely broken, it doesn't work as before. And the auto button is disabled and I'm not seeing the reference frame in the edit place.
"""
