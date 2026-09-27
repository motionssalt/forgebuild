# ForgeHouse 50 (Android) — Prompt History

Slug: `forgehouse50` (chosen by builder AI 2026-09-14; no collision with existing apps).

## 2026-09-14 — Operator: ForgeBuild App Build Contract — NEW APP

Full build contract received: native Android client for the already-live ForgeHouse 50 web app
(Cloudflare Pages + Workers + D1 at forgehouse50.pages.dev; source at github.com/vjumbo264/forgehouse50).
Requirements: feature parity (Home/Today, Read with audio + persistent offline scripture/audio storage,
Notes with 5 types, one-attempt no-gate no-timer quiz with FLAG_SECURE + confetti on day completion,
Progress, confidence-weighted Leaderboard + final results with distinct top-3 treatment, Profile with
illustration avatar picker + downloaded-content manager, Admin dashboard), Brevo OTP auth with secure
on-device token storage, cache-first data via Engine CacheFirstStore, daily reading reminder via
WorkManager with completion check, skippable-but-recurring in-app update prompts with newer-version
supersession, Material 3 + Material Symbols only, system light/dark theme, proper adaptive icon from the
web repo's committed logo artwork. Tasks 1-7 as seeded in apps/forgehouse50/BUILD_STATE.json.

## Session 3 (2026-09-14)
Re-sent the full ForgeBuild App Build Contract for the ForgeHouse 50 Android client (unchanged operator instructions; APP_DESCRIPTION, TASKS 1-7, engine helpers, per-step commit/push protocol, signing & releases). Resumed from repository state: tasks 1-4 done, task 5 in progress with 5.1/5.2 implemented, CI release attempts failing.

## Session 4 (2026-09-15) — EXTEND/UPDATE: Combined Site + Android App Fixes

Operator issued the ForgeBuild App Build Contract EXTEND/UPDATE for forgehouse50 (current latest release forgehouse50-v1; next release forgehouse50-v2). Full instruction text below; credential values (GITHUB_PAT, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, TELEGRAM_BOT_TOKEN) were provided in the operator prompt but are REDACTED here per the contract's never-commit-credentials rule:

"""
# ForgeHouse 50 — Combined Site + Android App Fixes

## Scope

This session covers issues found in **both** the live website (`vjumbo264/forgehouse50`) and the Android app (built via ForgeBuild). Some fixes are backend/site-side (surname field, timezone) and apply to both surfaces since the app consumes the same backend; some are Android-only (bundled avatars, icon, persistent notification, screenshot blocking). Read both repositories fully before starting and fix each issue in whichever repo(s) it actually lives in. Persistent-session discipline: BUILD_STATE.json as source of truth, never stop to ask for confirmation, commit and push after every step, verify every fix live/on-device before marking done.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

ISSUE 1 — SURNAME FIELD REQUIRED AT REGISTRATION (SITE + APP): add required surname/last_name. Backend/schema: add `surname` (or `last_name`) column to `profiles` in D1, required for new registrations; existing users (if any remain post-launch-wipe — check first) must not break (backfill placeholder or require set-on-next-login, judgment, documented). API: registration endpoint requires/stores the field, clear validation error if missing. Website: required surname input beside given name. Android app: same required input calling the updated API. Everywhere a user's name is displayed (Profile, Leaderboard, admin participant list, celebration/final-results screen) shows "Given name Surname" or "Given name S." (full surname on Profile/admin; whichever cleaner for tight leaderboard rows).

ISSUE 2 — AVATARS MUST BE BUNDLED IN THE APP, NOT FETCHED: bundle the full illustration avatar asset set (same set committed to the web repo from the earlier avatar-import work) into the Android app's resources/assets at build time — one-time asset copy by the build agent, not runtime fetch; fix the registration-flow bug where the avatar picker isn't displaying at all (diagnose actual cause first); app still sends only `avatar_id` to the backend exactly as the web app does.

ISSUE 3 — "REQUEST FAILED (200)" WHEN LOADING SCRIPTURE/AUDIO IN APP: HTTP 200 means the request succeeded — app-side response-handling bug mislabeling a successful response as failure. Reproduce: call the actual API endpoint the Read screen uses, inspect the real 200 body; find the exact parsing/validation logic throwing (JSON shape mismatch, null-handling, or response-wrapper check); fix the parsing bug AND fix error handling so genuine failures report the real HTTP status code (review this bug pattern across endpoints); verify live on-device: Day 1 scripture and audio load with no error.

ISSUE 4 — REGENERATE THE APP ICON: current icon displays incorrectly on operator's device (likely adaptive-icon layering / corner-masking issue like the web PWA icon fix). Use the same source logo (forgehouse50-logo-source.png, committed in web repo during PWA-conversion session); regenerate Android adaptive icon with correct foreground/background separation, safe-zone padding per adaptive icon spec, no corner artifacts (transparent/properly-filled corners, pixel-level verified); all required densities/formats (adaptive icon XML + foreground/background layers + legacy fallback); verify by installing on real/emulated device and visually confirming the home-screen icon (screenshot, not just resource files existing).

ISSUE 5 — KEEP THE EXISTING THEME AS-IS (NO CHANGE): operator considered then rejected Android system Material dynamic-color theming. Do not change the app's current color theme/design system. Nothing to do.

ISSUE 6 — PERSISTENT FOREGROUND NOTIFICATION TO KEEP THE APP ALIVE FOR REMINDERS: proper Android foreground service with low-priority, silent, minimal persistent notification (app name/icon, no sound/vibration, low importance channel) — Play-Store-compliant pattern; its job: keep process alive so the scheduled daily-reminder logic runs reliably; tapping notification opens app and/or one-time first-run explanation ("This keeps your daily reading reminder working reliably"); respect battery-optimization exemption flow — prompt once during onboarding with clear explanation if required.

ISSUE 7 — VERIFY WEST AFRICA TIME (WAT) USED CONSISTENTLY (SITE + APP): audit per-user reading-day calendar generation (Tue/Fri exclusion, day boundaries), daily quiz day boundary, read-ahead one-day limit boundary, admin Start Programme timestamp + 10-day join window, programme-end calculation, Android daily reminder scheduling — all must agree on WAT (UTC+1) as authoritative for day boundaries, not UTC and not device-local (a traveling user's reading day flips at WAT midnight). Fix any UTC/device-local/unspecified default; verify a specific known day-boundary case on both site and app.

ISSUE 8 — QUIZ UI POLISH (APP): redesign quiz screen consistent with existing unchanged design system: clear question hierarchy, well-spaced MC options with proper selected-state treatment, clear appropriately-weighted submit action, existing type scale/spacing/color tokens, not default unstyled system components.

ISSUE 9 — SCREENSHOT BLOCKING ON THE QUIZ SCREEN IS NOT ACTUALLY WORKING: original spec had FLAG_SECURE on quiz screen but operator successfully screenshotted it. Inspect exactly where FLAG_SECURE is/isn't applied (wrong Window/Activity/Fragment; set too late; false guard); fix so it's genuinely active the entire time the quiz screen is visible and genuinely removed on navigate-away; verify by actually attempting a screenshot on-device/emulator while the quiz is displayed.

ISSUE 10 — MISSING CONFETTI + NO WAY BACK HOME AFTER DAY COMPLETION (APP): confetti animation on completing both reading and the one quiz attempt — verify whether implemented; fix trigger condition/race, or build per original spec (brief, tasteful, celebratory, settles back to calm UI). Add clear prominent "Return Home" (or equivalent) action on the completion screen.

TASK LIST: append `combined_fixes_v1` section (web repo BUILD_STATE.json for site-side tasks, app repo build-state for app-side tasks) with at minimum one task per issue (split where independent commits). Per task: inspect → implement → verify live/on-device with real evidence → commit → push → update build-state → next task, without stopping. Mark complete only when every issue genuinely verified.

ADDENDUM: "Also make the leaderboards immediate not locked to day 3 and multiply the points by 7 so that it will be more encouraging meaning every point allocation is now 7 times more."
"""

## Session 5 (2026-09-15) — EXTEND/UPDATE: Leaderboard Points Fix, Broken Scripture Loading, New Icon

Operator issued the ForgeBuild App Build Contract EXTEND/UPDATE for forgehouse50 (current latest release forgehouse50-v2; next release forgehouse50-v3). Full instruction text below; credential values (GITHUB_PAT, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, TELEGRAM_BOT_TOKEN) were provided in the operator prompt but are REDACTED here per the contract's never-commit-credentials rule:

"""
# ForgeHouse 50 — Leaderboard Points Fix, Broken Scripture Loading, New Icon

## Scope

Three fixes: one leaderboard regression (site + app both consume the same backend, so this fixes both), one broken Bible-reading screen (diagnose whether it's site, app, or both — the operator's report doesn't specify), and an icon swap (app-side; also update the web PWA icon from the same new source if the web app's icon hasn't already been separately updated to match, since both should stay visually in sync).

## Attached file

The operator has attached a new ForgeHouse 50 app icon (`forgehouse50-icon-v3.png` — glossier/more dimensional version of the existing blue rounded-square flame/house/globe/"50" mark, corners already clean). Use this as the new master source image, replacing `forgehouse50-logo-source.png` wherever it's referenced (web PWA icon generation and Android adaptive icon generation both need to be regenerated from this new source). Commit it to the same source-asset location(s) already established in each repo for the icon source.

Use the same persistent-session discipline as every prior contract on these projects: relevant `BUILD_STATE.json`/build-state file as source of truth, never stop to ask for confirmation, commit and push after every step, verify every fix live/on-device with real evidence before marking it done.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

ISSUE 1 — LEADERBOARD MUST SHOW RAW TOTAL POINTS, NOT A PER-DAY AVERAGE: regression against explicit, already-given instructions. The leaderboard's primary "Overall Score" must be **raw cumulative total points** — every point earned from reading, quiz score, streaks, observations, questions, everything, simply added up. Never show or rank by any per-day average, including the confidence-weighted `adjusted_score` damping formula. Clarify actual scope of the damping formula: `adjusted_score = (raw_average × elapsed_days) / (elapsed_days + K)` was introduced specifically to prevent a short hot streak appearing equal to a long sustained streak **when both are still mid-programme and compared on an averaged basis** — a secondary signal at most, never the primary Overall Score. Fix: identify exactly where `adjusted_score` (or any per-day-average calc) is displayed/used as the main "Overall Score" leaderboard ranking/value on both website and Android app (likely one backend fix; verify both surfaces reflect it — app caching may mask the backend fix). Change the primary "Overall Score" value and ranking back to raw cumulative total points (sum of every point source: reading completion, quiz score contribution, streaks, observations, questions, community sharing, weekly targets, full-programme completion) — no division by elapsed days, no damping, for the in-progress leaderboard. If a separate clearly-labeled "damped/fairness-adjusted" view has value as a secondary/optional view, it may be kept but must never be the default or primary Overall Score, and must be clearly labeled as something else entirely; if it adds more confusion than value, remove it. The final one-time end-of-programme ranking snapshot logic (from earlier launch/finale work) already correctly specified raw total points — confirm untouched and still correct. Given app caching, ensure the Android app's cached leaderboard data is invalidated/refetched after this backend fix; verify on a real device after the fix, not just a direct API call. Verify live: check a real user's leaderboard entry and confirm the displayed value matches a manual sum of their actual point-earning history, not a divided/averaged figure.

ISSUE 2 — BIBLE / SCRIPTURE SCREEN STUCK ON LOADING: reading screen stuck showing a loading state, never displays content. Diagnose which surface(s) are affected first — check both website and Android app directly (operator's report doesn't specify which). Reproduce: open Read/Bible screen for a real reading day, observe actual network request(s) and their actual responses (or absence of a response/hung request). May relate coincidentally to Issue 3's icon work, or be a separate regression from recent work — check whether it correlates with the same "Request failed (200)" misdiagnosis pattern fixed in the prior session (a loading screen that never resolves suggests a request that never completes, throws silently and is swallowed without updating any loading-state flag, or is stuck on a promise/callback that never resolves). Find the actual root cause with real evidence (network inspection, logs, or a reproduced hang) rather than guessing. Common causes: unhandled promise rejection that never flips the "loading" flag false in either success or error branch; race where loading flag is set after the fetch already resolved; genuinely failed/hanging request to VerseWell or the backend's scripture-proxying endpoint. Fix the actual cause; ensure the loading state has a proper timeout/error fallback going forward (a future genuine failure shows a clear error message instead of an infinite spinner) — a reasonable defensive addition regardless of root cause. Verify live: load scripture text for at least two different reading days on whichever surface(s) were affected, confirming content actually displays, not just that the spinner disappears.

ISSUE 3 — REPLACE APP ICON WITH NEW ATTACHED VERSION: regenerate the Android adaptive icon (foreground/background layers, safe zone padding, legacy fallback icon) from the newly attached source image, replacing every previously generated icon asset in place, using the same corner-artifact-free generation discipline already established (verify pixel-level, no white/opaque corner artifacts on any density). Also regenerate the website's PWA icon set (192/512 standard + maskable, favicon, apple-touch-icon) from this same new source image, so the web app and Android app use visually matching, currently-correct icons. Verify by actually installing the Android app on a real or emulated device and visually confirming the home-screen icon renders correctly, and by checking the live web app's manifest icons render correctly — real rendered verification, not just confirming the files exist.

TASK LIST: append a `leaderboard_scripture_icon_fix_v1` section (web repo's BUILD_STATE.json and, where app-specific work is involved, the Android app's own build-state file) with one task per issue at minimum, splitting further if an issue spans independent commits (e.g. Issue 1's backend fix vs. Android cache-invalidation vs. verification). For each task: inspect → implement → verify live/on-device with real evidence → commit → push → update the relevant build-state file → move to the next task without stopping to ask for confirmation. Mark the section complete only once all three issues are independently, genuinely verified as fixed — not source-inspected.
"""

---

## 2026-09-15 — Follow-up: scripture/audio STILL unavailable, leaderboard STILL /day (rewire request)

Operator report after forgehouse50-v5 (verbatim, credentials redacted):

> The fix has been applied but the scripture and audio is not still available,
> also the leaderboards is still showing /day.
>
> The fix has been applied but I still don't see scriptures or audio, please
> just rewire that entire function in the app so it will work properly.

Interpretation / scope:
- v5's gating fix stopped the infinite spinner but did NOT restore content:
  the passage/audio LaunchedEffect in ReadScreen.kt was keyed on
  (day, selected, translation) and early-returned while dayLoaded=false. For a
  returning user (translation id already stored, never changes) the effect
  fires once, returns, and never re-fires -> passage + audio never load.
- Operator explicitly asks to REWIRE the whole scripture/audio load function
  in the app so it works properly.
- Leaderboard: app must show RAW TOTAL points, never any /day average. Prefer
  the explicit total_points field and invalidate the stale local cache.

(Credential values from the operator message are intentionally NOT recorded here.)


---

## 2026-09-16 — Leaderboard categories, scripture loading, audio removal, admin points fix, offline Bible downloads (target: forgehouse50-v7)

Operator EXTEND/UPDATE contract (verbatim substance; credential values redacted — never committed):

# ForgeHouse 50 — Leaderboard Categories, Scripture Loading, Audio Removal, Admin Fix, Offline Bible Downloads

## Scope

Seven issues across the website and Android app. The operator has attached a screenshot (`registration-host-error.jpg`) showing the host-resolution error referenced in Issue 6 below — review it directly as part of that issue's investigation. Read both repositories' current state fully before starting — do not assume prior sessions' "verified live" claims in either `BUILD_STATE.json` or the Android app's build-state file are accurate, given this project's history of fixes being reported complete when they visibly weren't. Verify everything yourself, from scratch, with real evidence, before building anything new on top of an assumed-working foundation.

Same persistent-session discipline: build-state file as source of truth, never stop to ask for confirmation, commit and push after every step, verify every fix live/on-device before marking it done — not via source inspection alone.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

ISSUE 1 — LEADERBOARD CATEGORIES ALL SHOW THE SAME POINTS VALUE: Only "Reading Time" shows its own correct value. Every other category (Overall Score, Consistency, Chapters Completed, Observations, Questions) incorrectly displays the same total-points figure. Inspect the leaderboard query/computation per category (likely one shared bug: a query or response-mapping function always returning/displaying the same `points` field regardless of category — check both backend query construction and frontend rendering). Fix each category to genuinely reflect its own metric: Overall Score = raw cumulative total points (per prior-session correction — confirm still correct); Consistency = current/longest streak, not points; Chapters Completed = count of chapters actually completed; Reading Time = already correct, leave as reference; Observations = count of Observation-type notes; Questions = count of Question-type notes. Verify live: for a real test user with known activity, manually calculate each category's expected value and confirm leaderboard UI AND direct API response match for every category.

ISSUE 2 — SCRIPTURE LOADING STILL BROKEN (PRIOR FIX DID NOT WORK): Prior session's fix for the stuck-loading Bible/scripture screen did not resolve it. Do not repeat the prior session's diagnostic conclusion — re-verify from first principles: reproduce the failure live, capture the actual real network request and response (or genuine absence of one) for the scripture-loading call, on both the website and the Android app independently. Given Issue 5 changes how the Android app sources scripture (bundled/downloaded local content), consider fixing this on the current live-fetch architecture for the website (which is not changing architecture) and treat the Android version as potentially moot only AFTER Issue 5's replacement path is confirmed working — not as an excuse to skip it. Fix the actual root cause with real evidence. Verify live: scripture text loads for at least three different reading days, actual content renders, on the website at minimum and on Android if Issue 5's rework isn't yet serving its content.

ISSUE 3 — REMOVE AUDIO BIBLE FEATURE ENTIRELY (SITE + APP): The operator removed audio narration from the upstream VerseWell API entirely (permanent). Website: remove the entire audio player component from the Reading page (seek bar, skip-5s buttons, play/pause, single-chapter download, download-all-for-today, chapter-audio-availability checks), any code calling VerseWell's removed audio endpoints, and audio-related UI copy. Android: remove the same functionality — the media playback/foreground service built for Bible-audio playback, audio-download UI, audio-only permissions (review the foreground-service situation: if the audio service and the daily-reminder-reliability foreground service are separate, keep the reminder one and remove only the audio one; if implemented as the same single service, refactor so reminder reliability survives independently). Remove any `audio_url`/audio-availability fields from API responses/request handling — clean up rather than leaving dead fields. Verify live: no audio player, controls, or audio errors/loading states anywhere on either surface.

ISSUE 4 — ADMIN POINT-EDITING BROKEN, NO SUCCESS FEEDBACK: Admin "award/remove points" no longer actually updates a user's points and provides no loading/success/failure indicator. Reproduce directly: attempt to edit a real test user's points via the admin interface, inspect the actual API call and response, check whether the underlying D1 update is genuinely attempted/succeeding/silently failing. This "used to work, then stopped" pattern suggests regression from a later session's schema/API change (surname field addition, per-user calendar migration, or quiz redesign could have altered a shared user-update code path or API contract) — check for exactly this kind of drift. Fix the actual broken update path. Add proper UI feedback: loading indicator in flight, clear success or error message on resolution. Verify live: award points to a real test user via admin UI, confirm loading-then-success feedback, and independently confirm via direct D1/API check (not just the UI's own message) that the points genuinely changed.

ISSUE 5 — OFFLINE BIBLE TRANSLATIONS: KJV BUNDLED, OTHERS DOWNLOADABLE (ANDROID APP ONLY): Genuine architecture change; does NOT apply to the website (website keeps live-fetch-from-VerseWell unchanged). Goal: Android app works fully offline for reading scripture, zero read-time API dependency for available translations. (a) KJV New Testament ships inside the app: at build time fetch the complete KJV NT text (all books/chapters matching the existing reading-assignment structure — 260 chapters across the 50-day assignment map) from the current API and bundle into app resources/assets in a well-structured local format (small local SQLite DB or structured JSON in assets — optimize for fast lookup by book/chapter); must render correctly with existing footnote/section-intro handling using bundled data; KJV is default/pre-selected on first use, always available, no download prompt. (b) Other translations downloadable per-translation on demand: on the Read screen or a dedicated "Manage Translations" area (easy to find from Read screen), list every API-available translation except KJV, each with visible file size and a Download action; tapping download fetches that translation's complete NT once, stores it in genuine on-device persistent storage (not evictable cache), marks it available for offline reading with no further network dependency; users can download many translations, see which are downloaded, and switch between downloaded ones freely from the existing translation selector entirely offline. (c) Storage management: show total storage used by downloaded translations and allow removing one (freeing storage) — extend the existing "Downloaded content" management area to cover translation downloads only (audio-download management is moot per Issue 3). NO audio download options anywhere in this flow. What NOT to change: website's scripture-fetching architecture (stays live-fetch); the backend/API itself is not removed or structurally changed — the Android app simply stops depending on it at read-time for downloaded translations; the API still must work for the KJV bundle-time fetch, per-translation downloads, and everything else (auth, progress, quiz, leaderboard, notes, admin).

ISSUE 6 — "UNABLE TO RESOLVE HOST" ERROR ON REGISTRATION + COMPRESS BUNDLED AVATARS: Screenshot shows `Unable to resolve host "forgehouse50.pages.dev": No address associated with hostname` at registration where the avatar picker displays (the avatar set in that screenshot is an earlier placeholder set, NOT the illustration-style set already bundled — treat the screenshot as evidence of the network error and the "avatars not displaying instantly" complaint, not as the target avatar set). Diagnose correctly: `Unable to resolve host` is DNS-level — could be no real device connectivity at that moment (environment issue, not app bug), a mistyped hostname in app config (search the Android codebase for every place `forgehouse50.pages.dev` / API base URL is defined and confirm spelling matches the live Cloudflare Pages domain exactly), or a transient Cloudflare availability issue. Determine which through real investigation rather than assuming a code fix is needed — if the hostname is correctly configured and the site is reachable, this may only need better error handling: on network/DNS failure during registration show a clear friendly actionable message (e.g. "Couldn't connect — check your internet connection and try again") instead of a raw low-level exception. ALSO: compress the bundled avatar illustration assets (the real currently-used illustration set) to reduce APK size — PNG recompression or WebP if the asset pipeline supports it cleanly — keeping quality crisp at profile-picture size in picker and on Profile/Leaderboard. Verify the app's total APK size measurably decreases and avatars still render correctly and quickly.

TASK LIST: append a `leaderboard_audio_removal_offline_bible_v1` section (web repo's BUILD_STATE.json and the Android app's build-state file, whichever relevant per task) with at least one task per issue (seven total), splitting further where an issue spans independent commits (Issue 5 should be several: KJV bundling, download-UI, per-translation download-and-store, storage management UI, removal of moot audio-download management). For each task: inspect → implement → verify live/on-device with real evidence → commit → push → update build-state → next task without stopping. Mark complete only once all seven issues are independently, genuinely verified as fixed — including a real offline test for Issue 5 (airplane mode on real/emulated device after downloading a translation; confirm it still reads with zero network).


---

## 2026-09-16 — Testing-phase control, progress/completion bug, scripture bundle format, reader UI restoration (target: forgehouse50-v8)

Operator EXTEND/UPDATE contract (verbatim substance; credential values redacted — never committed):

# ForgeHouse 50 — Testing-Phase Control, Progress/Completion Bug, Scripture Bundle Format, Reader UI Restoration

## Scope

Five issues in the Android app, at least two of which are very likely the same underlying bug wearing different symptoms (see Issue 2). Read the app's current build-state file and the web repo's BUILD_STATE.json fully before starting — do not assume prior sessions' "verified live" claims are accurate. Verify everything yourself, with real evidence, before treating anything as a stable foundation to build on.

Same persistent-session discipline as every prior contract: build-state file as source of truth, never stop to ask for confirmation, commit and push after every step, verify every fix live/on-device before marking it done.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

ISSUE 1 — ADMIN "END TESTING PHASE" CONTROL WITH FULL PROGRESS RESET: App has no concept of a testing phase distinct from the real public programme run — anyone who registers now can read and progress immediately, but the operator has not officially started the programme (separate from and preceding the existing admin "Start Programme" control that governs the 10-day join window and programme-end calculation). App must currently behave as an open testing sandbox, with an explicit admin action to close testing and reset everything to a clean state before "Start Programme" is used. Add a new admin control, clearly distinct from and separate from the existing "Start Programme" button — label like "End Testing Phase & Reset". Until pressed, app behaves exactly as now: open registration, full functionality, for testing. When pressed: delete every non-admin user and all associated data across every relevant table (reading_progress, notes, bookmarks, highlights, user_badges, points, quiz_attempts, sessions, otp_codes, leaderboard_snapshots, downloaded-translation records if tracked server-side, and any other per-user table) — reuse the existing cascading-deletion routine already built for the earlier migration_brevo_v1 test-user cleanup and launch-wipe work rather than writing from scratch. If that exact functionality still exists as a reusable admin action, this issue may simply be "surface/rename that existing capability clearly as an explicit 'End Testing Phase' admin button" — investigate what already exists before writing new deletion code. Do NOT automatically trigger "Start Programme" — ending testing and starting the programme remain two separate deliberate admin actions. Verify live: as admin, press "End Testing Phase & Reset", confirm every non-admin test account and its data is gone, and confirm the admin account and all shared/global data (reading assignments, quiz question bank, programme configuration) remain untouched.

ISSUE 2 — MARK COMPLETE, QUIZ START, AND PROGRESS CHECKMARKS ALL BROKEN (LIKELY ONE ROOT CAUSE): Three symptoms: completed reading days show no checkmark on the Progress screen, user cannot mark a day complete, user cannot even start the quiz. Investigate as ONE connected failure starting from whichever action happens earliest in the user flow (almost certainly mark-complete/quiz-start failing at API or state-management level; missing Progress checkmarks are the downstream consequence of no completion ever being recorded — not two separate bugs). Reproduce the actual failure directly: attempt to complete a real reading day as a test user, inspect the actual API call and its actual response for mark-complete/start-quiz. Given this project's history of schema/API drift between sessions (surname field addition, per-user calendar migration, quiz redesign removing pass/fail gate, offline-Bible-download rework), check specifically whether the Android app's mark-complete/quiz-start request still matches the backend's actual current expected request shape — strong candidate root cause. Once the actual root cause is found (with real evidence, not guesses), fix it and confirm the fix restores all three symptoms simultaneously: quiz startable, day markable-complete, Progress checkmark appears once genuinely completed. Verify live: complete a real reading day end-to-end (read → start quiz → submit quiz → day marked complete) and confirm Progress shows a checkmark for that day.

ISSUE 3 — SCRIPTURE DOWNLOAD FAILING; REPLACE WITH A SINGLE BUNDLE FILE PER TRANSLATION: Downloading a translation (offline-Bible-download feature) is failing with the same kind of API/200-style issue seen before. Operator does NOT want many small JSON files fetched individually. Instead: pre-build a single complete bundle file per translation containing the entire NT (all books, chapters, verses, footnotes, section intros) in one file, generated once (server-side or as a build/prep step run against the current API) rather than assembled on-device. Reasonable format: single gzipped JSON file or single SQLite DB per translation — must be exactly ONE file per translation downloaded in one request and read locally. Host the pre-built bundles somewhere reliably fetchable (committed static assets served by existing Cloudflare Pages/Workers infrastructure, or R2 if already in stack — simple single-URL-per-translation download, NOT a live API endpoint assembling dynamically per request). Update Android download flow to fetch this one bundle per translation (showing real download progress/file size per earlier spec). Diagnose why the current mechanism fails with a 200-style error first — apply the same "a 200 status means success, so something is misinterpreting a successful response" diagnostic discipline from the earlier scripture-loading fix, in case a similar response-handling bug in the download path would otherwise survive the rework unnoticed. Verify live: download at least one non-KJV translation as a real bundle file on a real/emulated device, confirm it completes with no error, and confirm the downloaded translation reads correctly offline (airplane mode test).

ISSUE 4 — RESTORE PER-CHAPTER READING VIEW (NOT ALL CHAPTERS AT ONCE): App reading screen currently shows an entire reading day's assignment as one long block (operator sees multiple chapters at once), which is overwhelming. Must match the pagination behavior already correctly built and verified on the website: ONE chapter at a time, with "Next chapter"/"Previous chapter" controls to move through a multi-chapter reading day — not all of that day's chapters in one continuous view. Inspect the website's existing per-chapter reading-pane implementation (from the earlier reading-page-compaction/layout-fix work) as the reference behavior to replicate natively. Fix the app's Read screen: render only the current chapter, with Previous/Next chapter controls, using the chapter-count/chapter-range data the reading assignment already provides. Applies whether reading from bundled KJV data or a downloaded translation bundle (Issue 3) — pagination is independent of data source. Verify live: open a multi-chapter reading day, confirm only one chapter shows at a time, Next/Previous works and stays within the day's assigned range.

ISSUE 5 — RESTORE FONT-SIZE CONTROLS (MISSING FROM APP): Website Reading page has font-size increase/decrease controls (A-/A+); Android app is missing this entirely. Add equivalent font-size controls to the app Read screen matching website behavior, persisted per-user (on-device at minimum — must persist across app restarts on the same device). Apply to whatever text-rendering component displays chapter content after Issue 4's per-chapter rework. Verify live: increase/decrease font size, text visibly changes, setting persists after closing/reopening the app.

TASK LIST: append a testing_phase_and_reader_fixes_v1 section (Android build-state file, and web repo BUILD_STATE.json for backend-side work e.g. Issue 3 bundle generation) with at least one task per issue, splitting further where an issue spans independent commits (Issue 2 needs a distinct diagnostic task before its fix task). For each task: inspect → implement → verify live/on-device with real evidence → commit → push → update build-state → next task without stopping. Mark complete only once all five issues are independently, genuinely verified fixed — not source-inspected — including Issue 2 resolving all three symptoms together and Issue 3's bundle genuinely working offline.


---

# 2026-09-16 — Material 3 Expressive Overhaul + Targeted Fixes (operator instruction, verbatim)

(Full operator instruction received this session: Part A — full Material 3 Expressive overhaul app-wide: dynamic system-theme color (Material You dynamicColorScheme + static fallback), squiggly/wavy button interaction animation on every button on every screen, floating pill bottom navigation, M3 Expressive loading indicators everywhere a loading state exists, rounded Google font (Google Sans Flex — confirm exact name/availability) app-wide on a sensible M3 type scale, full commitment not partial; auth screens explicitly included (currently stuck on old dark theme). Part B targeted fixes: B1 leaderboard avatars from bundled illustration set per avatar_id on all categories + final results; B2 rebuild day-completion confetti as genuine multi-particle burst lasting a comfortable few seconds without obscuring completion message/Return Home; B3 regenerate adaptive icon (currently blank/invisible) from newest forgehouse50-logo-source.png in live website repo with proper foreground/background/safe-zone + legacy fallback, verified under multiple launcher mask shapes; B4 fix malformed-JSON scripture bundle bug (Unexpected JSON token at offset 154: expected string, got object at path Matthew/1/v[0]/f[0]) — fix bundle-generation footnote flattening, regenerate ALL bundles, add post-generation schema validation, verify live with Matthew 1. Scope: Android-app-only; do not port to web app. Persistent-session discipline: build-state as source of truth, commit+push per step, real rendered screenshot verification mandatory for every Part A task.)


---

## 2026-09-18 — Real Material 3 Expressive Implementation + Multiple Regressions (target: forgehouse50-v11)

Operator EXTEND/UPDATE contract (verbatim substance; credential values redacted — never committed):

# ForgeHouse 50 Android App — Real Material 3 Expressive Implementation + Multiple Regressions

## Scope

Hard correction session. The prior session claimed to implement Material 3 Expressive but did not — it produced manual visual approximations without actually using the real Material 3 Expressive library components and motion system. This session must undo that mistake and implement it correctly, plus fix five additional concrete regressions/bugs found through real device testing.

Before writing any UI code, verify use of actual androidx.compose.material3 Expressive components (real library APIs, at a library version that genuinely ships Expressive — check the actual current version/changelog, don't assume) — not custom Compose modifiers, custom animation code, or manual recreations. If a genuine Expressive component doesn't exist yet for something requested (verify by checking the library's real current API surface), say so honestly in BUILD_STATE.json rather than building a fake substitute — a documented gap is acceptable; a disguised fake is not.

Every task in the prior material3_expressive_overhaul_v1 section is treated as NOT actually done and re-verified/redone properly this session. Every task in this session requires an actual screenshot or real on-device recording as evidence, attached/described in the build-state file's notes for that task, before it can be marked done.

CREDENTIALS: [REDACTED — provided in operator prompt; never committed]

PART A — IMPLEMENT REAL MATERIAL 3 EXPRESSIVE (NOT AN APPROXIMATION): (1) Squiggly/wavy button press animation — real Material 3 Expressive button/interaction component that provides this, not custom-drawn; verify the actual library component exists and provides this specific effect before implementing; apply app-wide; prove with real screen recording/sequential screenshots of a button mid-press with squiggle visible. (2) Morphing button shapes — real library-provided shape-morph behavior on press/state-change, not scale/opacity tween. (3) Wavy/organic loading and progress indicators — replace every loading spinner/progress bar app-wide with the real M3 Expressive loading/progress components (shape-morphing/wavy-style), not standard CircularProgressIndicator/LinearProgressIndicator, not custom-drawn waves. (4) Re-verify floating bottom navigation, dynamic system-color theming, and Google Sans Flex (or correctly-identified equivalent) rounded font with real on-device screenshots. (5) Go through EVERY screen individually (auth/sign-in/registration, Home, Read, Notes, Progress, Leaderboard, Profile, admin, quiz, translation-download, completion/confetti), apply real Expressive components/motion consistently, capture a real screenshot of each screen as evidence in BUILD_STATE.json notes.

PART B — CONFETTI STILL BAD (SECOND ATTEMPT): Prior fix did not work — still flies across the screen too fast to read as celebration. Read the prior implementation first. Required: multiple individual confetti pieces, varied colors, varied simple shapes (small rectangles/circles sufficient), each with independent randomized fall trajectory, rotation, slight horizontal drift — genuine particle-style physics. Total duration ~2.5-4 seconds. Check specifically whether prior attempt's duration/speed parameter was set far too low as likely root cause before assuming a full rewrite. Verify with a real screen recording (not a screenshot alone) that it reads as confetti to a human.

PART C — READING TIME NOT TRACKED (SHOWS ZERO FOR EVERYONE): Leaderboard Reading Time category shows zero for all users — regression (was the one category confirmed working). Reproduce: read a chapter for a real measured duration, inspect the actual API call/payload and the actual stored value in D1. Determine client-side (app never measures/sends reading time — check whether tracking logic even exists in current Read-screen implementation; recent per-chapter pagination / offline bundle rework may have dropped it) vs backend bug. Fix the actual cause; if client tracking was lost, reimplement: track actual foreground time on Read screen per chapter/day, report via existing POST /api/read/time contract (web app implementation is the reference if it still works). Verify live: known read duration -> corresponding non-zero leaderboard value.

PART D — MARK-COMPLETE BUTTON HAS NO LOADING/FEEDBACK STATE: Tapping Mark Complete gives no visual feedback. Add proper loading state: disable button + show loading indicator (real M3 Expressive loading component from Part A) while request in flight; clear success feedback on resolve (transition to completion/confetti screen or distinct success indicator); clear actionable error feedback on failure. Verify live: loading state visible during request, clear feedback on completion.

PART E — FONT WEIGHT TOO LIGHT/THIN APP-WIDE: Verify which font is actually in use right now. Increase default body-text font weight app-wide to visibly less thin (e.g. Light/Regular -> Regular/Medium baseline) while keeping hierarchy. If the font is a variable font (Google Sans Flex is), adjusting the weight axis for body text may suffice. Verify live with before/after screenshot at real device default text size.

PART F — TRANSLATION DOWNLOAD: BROKEN PROGRESS BAR + "NOT GZIP FORMAT" ERROR: (1) Progress bar doesn't update during download — inspect whether download uses incremental byte-progress reporting vs one blocking await with no progress callback; wire genuine incremental progress from network layer to UI, not fake/simulated. (2) "Scripture is not in GZIP format" — fetch the currently-hosted bundle directly, inspect real content/headers, compare to app's decompression expectation; fix whichever side is actually wrong (regenerate/re-host bundle in genuine gzip, or update app expectation to match real format). Verify live: download a translation, progress animates proportionally throughout, no format error, downloaded translation reads correctly.

PART G — LOGIN/SIGN-IN SCREEN PERSISTENTLY BROKEN DARK MODE: Login screen "always in a very broken dark mode" every app open — flagged in prior overhaul session, evidently not fixed. Reproduce on real device in light AND dark system modes with screenshots. Identify actual cause (hardcoded dark color in login composable bypassing theme tokens; theme-provider scoping bug rendering login outside themed subtree; or genuine visual/layout bug) via the actual screenshots. Fix so login correctly follows the same dynamic system-theme logic as the rest of the app, no hardcoded colors, visually correct in both modes. Verify live with both-mode screenshots.

TASK LIST: append material3_expressive_correction_v1 section to Android build-state. One task per screen for Part A (auth, Home, Read, Notes, Progress, Leaderboard, Profile, admin, quiz, translation-download, completion/confetti) plus one per Part B-G. Every task's completion evidence must describe/reference a real screenshot/recording taken this session. Do not trust prior overhaul section's claims. Work through every task without stopping for confirmation. Mark section complete only once every screen genuinely re-verified with real Expressive components and all six Part B-G issues independently, concretely fixed and verified.

---

## 2026-09-18 — EXTEND / UPDATE: ForgeHouse 50 Android App — Post-Testing Polish Round

(Credential values redacted per contract; never committed.)

Six issues found through real device testing after the Material 3 Expressive
correction session. Refinement, not a full correction.

- ISSUE 1 — FONT STILL TOO THIN (SECOND REPORT): verify the real current body
  weight value; move at least one full step up (e.g. Medium -> SemiBold); err
  toward heavier rather than risk a third report.
- ISSUE 2 — DOWNLOAD SPINNER LOW-CONTRAST IN DARK MODE: spinner too dark vs its
  background in dark mode; fix it to use the same dynamic theme accent as every
  other loading indicator; verify light + dark.
- ISSUE 3 — NO "THIS IS YOU" ON THE LEADERBOARD: add a clear, tasteful visual
  distinction (accent tint + "You" chip) for the current user's own row across
  every category and the final-results screen, at any rank.
- ISSUE 4 — FOOTNOTES MISSING FROM READING VIEW: implement single tappable
  marker per verse-with-footnotes; tap expands ONE inline panel with all of that
  verse's footnotes; only one panel open at a time per chapter; no marker when a
  verse has none; Material 3 Expressive styling.
- ISSUE 5 — PREVIOUS/NEXT CHAPTER NEED ICONS, NO SQUIGGLE: chevron icons +
  button-like treatment; explicitly NO expressive squiggle/press-morph on these
  two controls; calmer press state.
- ISSUE 6 — APP ICON FULLY WHITE UNDER THEMED ICONS (MISSING MONOCHROME LAYER):
  get the CURRENT icon source from the live website repo (forgehouse50-icon-v3),
  extract the mark silhouette, add a real <monochrome> layer (not empty/opaque)
  to the adaptive icon XML, sized in the safe zone; leave working foreground/
  background layers untouched; verify with themed icons ON and OFF.
---

## 2026-09-27 — EXTEND / UPDATE: ForgeHouse 50 Android App — WhatsApp/Support Links, Catch-Up Reading, Home-Screen Widget, Gated Completion, Icon Reapplication

(Credential values redacted per contract; never committed.)

- ISSUE 1 — Add a distinct "Join ForgeHouse WhatsApp Group" action (opens
  https://chat.whatsapp.com/KNt6BxuzOWwFZ9p4LL2Dfq) AND a separate "Support
  ForgeHouse Global" action (opens a direct WhatsApp chat to the admin via
  https://wa.me/2349139095481). Never merge the two.
- ISSUE 2 — Catch-up reading: at most 2 days' reading per session, oldest
  outstanding days first, until caught up; no hard cap on missed days; the only
  limit is arithmetic (outstanding > remaining calendar reading days) shown as
  an honest Progress-screen status, never a lockout. Interacts with the existing
  1-day read-ahead: catch-up pacing takes precedence while a backlog exists.
  Each catch-up day keeps its own reading content and its own quiz.
- ISSUE 3 — Real Android home-screen App Widget (Jetpack Glance preferred):
  Day x/50, current assignment book+chapters, a key verse (first verse of the
  range as the default), X/260 progress, state-based primary action
  Start/Continue/Completed(+Reflect) deep-linking into the app, periodic
  refresh via WorkManager. Optional: participants-completed-today count only if
  a backend endpoint already exists (otherwise document as skipped).
- ISSUE 4 — Gate the Finish Reading / mark-complete action until every chapter
  of that day has actually been viewed (per-day inside catch-up sessions);
  visibly disabled with a "viewed k of N chapters" indicator, not hidden.
- ISSUE 5 — Reapply the app icon from the operator-supplied source image:
  full adaptive set (background, foreground, monochrome themed layer per the
  established pattern) plus legacy fallback; verify themed icons on and off.
