# PROMPT HISTORY — taskflow

## 2026-09-22 — Operator instruction (new app build)
Build "TaskFlow" (working title) — native Android to-do app with AI agent, Kotlin + Jetpack Compose, Material 3 Expressive.
Core: Room-backed Task model (title, fractional-rank priority, optional fixed time, recurrence none/daily/weekly/monthly/yearly, free-text info field, nullable parent for infinitely nestable sub-tasks, completion state, timestamps). Fractional/lexo-rank priority (insert between neighbors, background rebalance), drag-and-drop reorder, always sorted priority-first. Fixed-time tasks: AlarmManager exact alarms + foreground service with honest explanation (no unkillable background promise), due-now pinning + distinct styling, system + in-app alerts. Sub-tasks unlimited nesting, recursive UI. Info field hidden behind eye icon (read-only quick-peek bottom sheet; edited via edit screen). Full edit screen for all fields. AI agent via Gemini Flash Lite, multiple API keys with failover (encrypted on-device storage), mic FAB chat/voice panel, function-calling tools mirroring all task CRUD + relational ops, open-ended instruction inference, confirmations in chat + system notification. Per-type notification toggles (due, overdue, daily agenda, API-keys-failed, agent confirmation, approaching deadline [off], recurring instance [off]). Screens: main list, task detail/edit, AI chat panel, settings (keys, notifications, permissions, theme), onboarding permission flow. M3 Expressive discipline throughout. Non-goals: no device admin, no unkillable-background promises, keys local-only.
Process: slug chosen by AI (chose "taskflow"), engine copied as starting point, one feature per commit/push, release via release.yml app_path=apps/taskflow, tag taskflow-v1, verify release live.

## 2026-09-22 — Revision Pass 2 (Fixes + New Requirements)

# TaskFlow — Revision Pass 2 (Fixes + New Requirements)

This is a follow-up pass on an existing app. Below is the full current intended spec, with fixes and new requirements from testing folded in. Treat this as the target state to build toward — fix what's broken, add what's missing, and do the full UI redo described below.

## Core Data Model
- **Task**: title, priority rank (see Priority System), duration (see Duration — mandatory), fixed time (optional datetime), recurrence rule (optional), info/notes (free text, hidden by default — see Task Info), parent task ID (nullable, for sub-tasks — infinitely nestable), completion state, completed-at timestamp, created/updated timestamps.
- **Recurrence**: none | daily | weekly (specific weekdays) | monthly | yearly. Recurring instances regenerate automatically after completion or at day rollover.

## Duration (NEW — mandatory field)
- Every task must have a duration (how long it will take) set before it can be created — this is a required field, not optional.
- The app tracks total time already allocated for the current day. When creating or editing a task's time/duration, if the remaining time left in the day is less than the task's duration, block creation and tell the user there isn't enough time left in the day.
- Show "time remaining today" prominently on the main screen (e.g. near the top) so the user always knows how much of their day is still unallocated.
- When the AI agent creates a task and the user didn't specify a duration, the agent must estimate and assign a reasonable duration itself — duration is never left blank, whether set by the user or inferred by the AI.

## Priority System
Priority is a background-only ranking mechanism — no numbers, decimals, or ranks are ever shown to the user in the UI. Internally use fractional/lexo-rank ordering so any task can be inserted between any two others without renumbering the whole list; rebalance silently in the background as needed. User-facing interactions:
- Drag-and-drop reordering in the list.
- Voice/text AI command: "put this between Task A and Task B" → agent computes and applies the correct rank.
Task list is sorted by priority rank, highest first — but the number itself stays invisible.

## Fixed-Time Tasks & Reminders
- Tasks can have a fixed time (including recurring fixed times, e.g. daily at 5pm).
- Reminders must actually fire reliably at the exact scheduled time — this is currently broken/missing and needs to work properly, including surviving Doze/battery-saving restrictions through whatever legitimate permission flow the OS provides.
- When a fixed-time task's time is approaching/arrives: task moves to the top of the list regardless of priority rank, with a visually distinct "due now" treatment (color + icon + subtle animation), and stays pinned there until completed or the next day's rollover.
- Trigger both a system notification and an in-app visual alert at the fixed time.

## Persistent Background Notification (FIX — currently missing)
- There must be a persistent, low-priority notification indicating the app is active/running in the background, matching the standard pattern other apps use to stay alive for reliable reminders. This is currently missing and needs to be added back.
- The background-run permission request is currently non-functional — clicking to grant "run in background" does nothing. This needs to actually trigger the OS-level prompt/dialog asking the user to allow the app to run in the background, the way it correctly does in other apps. Fix this flow end to end.

## Time Zone & Clock Awareness (NEW)
- The app must have access to the device's current time and time zone at all times, and use it as the single source of truth for scheduling, "time remaining today," and due/overdue logic.
- The AI agent must also have access to the current date/time/time zone when reasoning about requests (e.g. "what day is it," "how much time is left today," relative scheduling like "this week").

## Sub-tasks
- Any task can have sub-tasks; sub-tasks can have their own sub-tasks (unlimited nesting).
- Tapping into a task shows its sub-tasks list, each with the same feature set (priority, duration, info, optional fixed time, etc.) — recursive UI, not a special-cased single level.

## Task Info Field
- Free-text field per task/sub-task for notes on how the task should be done.
- Hidden from the task list/row by default. Shown via a small "eye" icon on the task — tapping it opens the info in a quick-peek view (e.g. bottom sheet/dialog) rather than displaying the text inline.
- Info is edited only from the task's edit screen, not from the eye-icon peek view.

## Task Type Color-Coding (NEW)
Give each task a distinct visual treatment (color and/or label — whichever reads more cleanly) based on its type, so the kind of task is identifiable at a glance:
- Normal task, no fixed time, not recurring.
- Task with a fixed time, not recurring.
- Recurring task with a fixed time.
- Recurring task without a fixed time.
Keep these visually distinct but consistent with the overall Material color system rather than clashing with it.

## Completed Tasks (NEW/CHANGED)
- Completing a task should not just strike it through in place. It should move out of the active list into a separate "Completed" area/view.
- Completed tasks auto-delete after a configurable retention period (default a sensible short window, e.g. a week or two — expose this in Settings) so the completed list doesn't grow indefinitely.

## Time-Range Views (NEW)
- Beyond "today," the user needs to view tasks by week, month, and year — e.g. via a calendar picker or a segmented view switcher on the main screen.
- Recurring tasks must NOT be expanded/listed as repeated individual entries across these ranges (that would flood the view). Show the recurring task once, appropriately represented as recurring, not duplicated per occurrence.

## Editing
All task properties (title, priority, duration, time, recurrence, info, color/type, parent) editable after creation via a task detail/edit screen.

## AI Agent
- Settings: user can add multiple Gemini API keys; app fails over to the next key automatically if one fails/rate-limits. Uses the latest lightweight Gemini model suited for fast agentic use.
- Microphone button (persistent, easily accessible) is currently non-functional — clicking it does nothing. This needs to be fixed so it actually opens the voice input flow.
- Mic recording state needs a live waveform/audio-spectrum animation while recording (the familiar voice-note-style animation used by WhatsApp and similar chat apps) — not a static or missing indicator.
- Chat/voice interface lets the user speak or type natural language commands.
- Full CRUD + relational commands: create tasks, edit any field, change priority (including relative placement "between X and Y"), add/edit/delete sub-tasks at any depth, mark complete, etc. — the agent must have access to every action a user can do manually.
- The agent must handle open-ended, loosely-specified instructions and infer sensible defaults rather than requiring exact parameters for everything, e.g.:
  - "Set this to only happen on Tuesdays" → maps to weekly recurrence with Tuesday selected.
  - "Create three tasks I want to do this week but I don't care when — scatter them across the week" → creates multiple tasks, spreads them across distinct days/times, assigns reasonable durations and priority ranks, without demanding the user specify every detail.
  - The agent always has current date/time/time zone available for this kind of reasoning.
- After completing an action, the agent responds via:
  1. An in-app confirmation message in the chat interface describing what it did.
  2. A system notification with the same summary.

## Notifications
- **Fixed-time task due** — at-time alert (system + in-app "due now" state), default ON.
- **Overdue task** — a fixed-time task not completed at its due time, default ON.
- **Daily agenda summary** — morning notification listing today's fixed-time tasks and top-priority items, default ON.
- **All Gemini API keys failed** — alert when every fallback key has failed, default ON.
- **AI agent action confirmation** — summary of what the agent just did, default ON.
- **Approaching deadline** — optional lead-time warning before a fixed-time task, default OFF, configurable lead time.
- **Recurring instance generated** — low-priority ping when a new recurring instance is created, default OFF.
- **Persistent "app is running" notification** — always present while the background service is active (see Persistent Background Notification above); this is not user-togglable the way the others are, since it's required for reliability.

All non-persistent notification types toggleable individually in Settings > Notifications.

## Complete UI Redesign (NEW — top priority)
The current UI is not acceptable and needs to be fully reimagined, not incrementally patched:
- Full expressive design language throughout — rounded/organic "squiggly" shapes on buttons and interactive elements, not sharp/flat defaults.
- Rich, fluid motion: spring-based transitions, satisfying micro-animations on task completion, priority changes, the mic recording state, and the "due now" pinning behavior.
- Should look and feel premium and polished — on par with recent well-designed Google first-party apps, taking full advantage of the latest expressive Material design system rather than a generic/default look.
- Every screen and component should be reconsidered as part of this redesign, not just colors/theming — layout, spacing, iconography, and interaction patterns all included.

## Screens (adjust as needed)
1. **Main task list** — priority-sorted, due-now tasks pinned/highlighted, time-remaining-today indicator, time-range view switcher (today/week/month/year), FAB for add + mic.
2. **Task detail/edit** — all fields including duration and color/type, sub-task list, nested navigation.
3. **AI chat/voice panel** — conversation history, mic with live waveform animation, text input.
4. **Completed tasks view** — separate from the active list, shows retention/auto-delete behavior.
5. **Settings** — Gemini API key management, notification toggles, completed-task retention period, background/battery permission setup, theme.
6. **Onboarding/permissions flow** — requests notification, exact-alarm/reminder, and background-run permissions with plain-language explanations, and must actually trigger the real OS prompts (currently broken for background permission).

---

## 2026-09-22 — TaskFlow — Revision Pass 3 (Fixes)

This is a follow-up pass on the existing app (previous revision already implemented). These are new fixes only.

### Voice Input — Send Raw Audio to Gemini (FIX)
- Voice input currently uses the device's on-device text-to-speech/speech-to-text. This is wrong.
- Instead: record the raw audio and send the audio clip directly to Gemini, which natively handles audio transcription/understanding itself — Gemini receives and interprets the audio, not a locally-transcribed text string.
- While recording, show the live waveform/audio-spectrum animation (WhatsApp-voice-note style).
- Once recording ends, show a distinct "sending voice note" state/animation while the audio uploads, before Gemini's response comes back.

### Task Creation Flow (FIX)
- Currently the user must type a task name and create the task before they can set duration, info, or anything else. This is wrong.
- Correct flow: a "+" / add-task icon opens the full task creation sheet/screen up front — title, duration (mandatory), fixed time, recurrence, info, color/type, parent — all set in this one creation step, before the task is actually saved.
- The task is only written/created once the user confirms from this creation screen, with everything already configured — not created bare and edited afterward.
- All fields remain editable later from the task's edit screen as before — this only changes the *creation* flow.

### AI Task Creation Failing (FIX)
- The AI agent is currently failing to create tasks when asked via chat/voice. Diagnose and fix so create-task commands reliably succeed, respecting the mandatory-duration rule (agent infers a duration if the user didn't give one).

### Recurrence Expiration (NEW)
- Recurring tasks need an optional end condition: "repeat every day/week/etc. until <date>." After that date, the recurrence stops generating new instances.
- If no expiration is set, recurrence continues indefinitely as before.

### Unfinished & Expired Task Handling (NEW)
- **Normal (non-recurring, non-fixed-time) task left unfinished at day rollover** → carries over to the next day automatically, staying active.
- **Recurring task** → does not carry over as "unfinished"; it simply follows its own recurrence schedule (today's instance doesn't roll into tomorrow — tomorrow's instance is generated per the recurrence rule as normal).
- **Fixed-time task whose time has passed unfinished** → does not carry over and does not stay pinned. It disappears from the active list and moves to a distinct "Unfinished" view (separate from Completed), where the user can review missed tasks.

### Screens — Addition
- **Unfinished tasks view** — new screen/section, separate from Completed; holds fixed-time tasks whose time passed without completion.

## 2026-09-22 — TaskFlow — Revision Pass 4 (Fixes)

This is a follow-up pass on the existing app (previous revisions already implemented). These are new fixes/requirements only.

### Time-Remaining-Today Not Reducing Correctly (FIX)
- Time remaining today is not being reduced by tasks that should count against it — specifically confirmed broken for recurring tasks and for tasks that carried over into the next day.
- Any task with a duration scheduled/active for a given day must count against that day's remaining time, regardless of whether it's a one-off, recurring instance, or a carried-over task. Fix the calculation so all of these are included, not just plain newly-created tasks.

### Overnight/Cross-Midnight Tasks (NEW)
- Support tasks whose time span crosses midnight — e.g. 8 PM to 3 AM.
- Model this as a single logical task that spans two calendar days, split into two linked segments for day-based views/accounting: the portion before midnight counts against "today," and the portion after midnight counts against "tomorrow" — but it's still one task to the user (one entry, one completion state, one edit), not two separate tasks they have to manage.
- This must work whether the task is a one-off fixed-time task or a recurring fixed-time task.
- The linked segments should not cause bugs in the time-remaining calculation, the "due now" pinning logic, or the day/week/month/year views — each day's view shows the relevant segment of the task, but completing or editing it applies to the task as a whole.

### Unfinished Recurring Tasks Must Also Expire (FIX)
- Recurring tasks that end up in the Unfinished view currently stay there indefinitely. They need the same expiration/auto-delete behavior as the Completed view — remove them after a configurable retention period so the list doesn't grow forever.

### Voice Recording Waveform Animation (FIX — still wrong)
- The current waveform animation does not match the intended reference (WhatsApp voice note recording) and looks non-uniform/unpolished.
- Required behavior: bars should scroll in from one side (right to left) continuously while recording, with each bar's height reflecting the live audio amplitude at the moment it's captured — not a shape that just passively follows/wobbles under the current input. The waveform should look uniform and clean (consistent bar width/spacing/rounding), matching the polish of a real chat app's voice-note recording UI.

### Full UI Reimagining — Previous Attempt Did Not Do This (FIX — critical, redo)
The previous pass did not actually change the UI — it kept the existing look almost entirely, which was not the instruction. This needs to be corrected properly this time:
- Treat this as if only the backend/logic is being kept, and the entire front end is being rebuilt from scratch with no reference to the current UI at all — new layouts, new navigation patterns, new component styles, new screen structure where it improves the app. Nothing about the current visual design should be assumed to carry over.
- Fully adopt Google's latest Material 3 Expressive design system — research and use its actual current components and patterns rather than approximating them: expressive shapes, spring-based motion, and signature interaction details (e.g. buttons/components with organic "squiggly" shape-morphing on press/state-change, satisfying tactile animation on every interactive element).
- Specifically calling out things still missing/wrong that must be corrected in this redo:
  - Buttons and interactive elements must show real expressive press/state animations, not static or default styling.
  - The "time remaining today" indicator must use Material 3's current expressive animated progress indicator component (the newer wavy/animated style), not a plain static progress bar.
  - General loading states throughout the app should use expressive/animated indicators consistent with this design language, not generic spinners.
- The end result should look and feel like a premium, modern Google first-party app — comparable to current Google Calendar/Tasks/Keep — not an incremental reskin of what exists now.

## 2026-09-23 — TaskFlow — Revision Pass 5 (Fixes)

This is a follow-up pass on the existing app (previous revisions already implemented). These are new fixes/requirements only.

### Recurring Fixed-Time Tasks Not Showing Their Time (FIX)
- Recurring tasks that also have a fixed time are not displaying their time in the UI. Fix this so the fixed time is always visible for these tasks, same as it is for non-recurring fixed-time tasks.

### Sub-task Duration Cannot Exceed Parent (NEW constraint)
- The same way total task durations for a day cannot exceed the total time available in that day, a task's direct sub-tasks' durations cannot cumulatively exceed their parent task's own duration.
- This applies at every level of nesting: for any given task, the sum of its immediate children's durations must always be ≤ that task's own duration. Block creation/editing of a sub-task if it would push the cumulative total over the parent's duration.
- This also applies to overnight/cross-midnight tasks (see below) — e.g. if a parent task spans 8 PM to 2 AM (6 hours), its children's durations combined cannot exceed 6 hours.

### Recurring Task Completion Duplication Bug (FIX — critical)
- Confirmed bug: completing (checking off) a recurring task leaves behind a duplicate "normal" (non-recurring) copy of it in its place, missing its children/sub-tasks.
- Restoring the original from task history and completing it again creates yet another duplicate each time — this compounds with repeated use.
- Diagnose and fix the recurring-task completion logic so that completing an instance correctly: marks that instance as completed (moving it to Completed as normal), and generates/advances the next recurring instance properly, without leaving behind any duplicate, non-recurring, children-less copy.

### Voice Recording Send Button Cropped (FIX — minor)
- The send button shown while recording/composing a voice note is visually cropped. Fix its layout/sizing so it displays correctly, uncropped.

### Overnight Tasks Require a Fixed Time (NEW constraint)
- Overnight/cross-midnight tasks are only allowed when the task has a fixed time set (with duration), whether it's a one-off or recurring task.
- A task without a fixed time must never be allowed to span/cross into the next day — this prevents a plain flexible-time task from ambiguously "leaking" overnight just because the day ran out. If a task has no fixed time, its scheduling must stay confined to a single day; block any attempt to set it up as an overnight-spanning task.

### Overnight Task Children Constraint (Clarification of above)
- For tasks that span into the next day (e.g. 8 PM–2 AM, 6 hours total), the same parent/child cumulative duration constraint applies: children's combined duration must not exceed the full span of the overnight task, not just one day's portion of it.

### AI Chat Interface — Reimagine This Screen Specifically (NEW — targeted UI redo)
This is a fix to how UI-redo requests have been scoped: rather than a global instruction, this pass targets one specific screen precisely — the AI chat/voice interface (where the user sends text or voice messages to the AI and sees its responses).
- Treat this screen as a full redesign, not a patch — layout, message bubble/list style, input bar, mic button, and any surrounding chrome should all be reconsidered.
- Fully adopt Material 3 Expressive for this screen: expressive shapes, spring-based motion, satisfying press/state-change animations on the mic button, send button, and message elements; smooth animated transitions for new messages appearing and for state changes (idle → recording → sending → responded).
- Should feel as premium and polished as the rest of the app is intended to be — comparable to a well-designed first-party Google chat-style surface.

# TaskFlow — Revision Pass 6 (Fixes) — 2026-09-23

This is a follow-up pass on the existing app (previous revisions already implemented). These are new fixes/requirements only.

## Main Screen Header Layout (FIX)
- The app name/short description header at the top of the main screen sits too close to the content below it. Fix the spacing/arrangement so the header has proper breathing room and the layout doesn't feel cramped — push content down appropriately rather than crowding it under the header.

## AI Should Not Auto-Assign Fixed Times (FIX)
- The AI agent is currently giving newly created tasks a fixed time automatically, even when the user didn't ask for one. This must stop.
- A task should only get a fixed time if the user explicitly specifies one (directly, or clearly implies one — e.g. "remind me at 3pm"). Otherwise, tasks the AI creates should have no fixed time, same as if the user created it manually without setting one.

## Due-Task Alert State Should Persist Through the Task's Duration (NEW)
- Currently a fixed-time task's "due now" alert state presumably triggers only at the exact start time. Instead: the prominent "due now" alert state should persist for the full duration of the task (from its fixed start time until start time + duration).
- Once that duration has elapsed and the task still isn't completed, it should transition to a different, more subtle "time's passed but not done" visual state — distinct from both the active "due now" alert state and a normal task state — rather than staying in the loud alert treatment indefinitely.

## Recurring Task Card Shading in Dark Mode (FIX)
- Recurring task cards currently use a shade of blue that's too bright/harsh specifically in dark mode (light mode looks fine). Other task-type shadings already look correct in dark mode.
- Fix the recurring task color so it's toned down appropriately for dark mode, consistent with how the other task-type colors are already handled there.

## Task Timer / Play Button (NEW feature)
- Add a play button to tasks that starts a countdown based on the task's duration.
- When the countdown finishes, play a distinct, unique completion sound (like a kitchen timer/oven "ding," not a generic notification tone) to alert the user.
- After the sound plays, present the user two choices: **extend** the task (add more time — minutes/hours, same units used elsewhere in the app) or **complete** the task.
- Extending must also be doable via the AI agent — the user should be able to tell the AI to extend a running/finished-countdown task.
- The user must also be able to ask the AI to extend a task's timer while its countdown is still running (mid-countdown), not only after it finishes.

## Voice Recording Waveform Not Reaching Full Width (FIX)
- The waveform animation that scrolls right-to-left while recording currently stops short of the left edge instead of traveling all the way across. It starts flush at the right edge but doesn't reach the left edge — fix so it travels the full width symmetrically, same as it starts.

## Settings Screen — Reimagine This Screen Specifically (NEW — targeted UI redo)
- Same approach as previous per-screen redesign requests: this pass targets the Settings screen specifically for a full redesign, not a patch.
- Rethink layout, structure/grouping of settings, and visual presentation entirely — the arrangement doesn't need to resemble the current settings screen at all.
- Fully adopt Material 3 Expressive for this screen: expressive shapes, spring-based motion, and satisfying interaction animations on toggles, list items, and any inputs (e.g. API key management, notification toggles).
- Should feel as premium and polished as the rest of the app is intended to be.

### Operator addendum (same session, 2026-09-23): the task timer must also support PAUSE, not just play — the user can pause a running countdown and resume it. Play and pause must also be controllable via the AI agent ("pause the timer on X", "resume/start the timer on X"), same as extend.

---

# TaskFlow — Revision Pass 7 (Fixes) — 2026-09-23

This is a follow-up pass on the existing app (previous revisions already implemented). These are new fixes/requirements only.

## Sub-task Added From Edit Screen Creates a New Root Task Instead (FIX — critical bug)
- When editing a task and adding a sub-task from within that edit screen, the sub-task is being created as a brand-new standalone root task instead of being nested inside the task being edited.
- Fix so sub-tasks added from a task's edit screen are correctly attached as children of that task.

## Pomodoro Feature (NEW)
- Add Pomodoro settings (work-interval length and break-interval length, user-configurable) in Settings.
- When Pomodoro is enabled and the user presses play on a task, the task's countdown runs using the Pomodoro pattern instead of one continuous countdown:
  - Split the task's total duration into work intervals and breaks per the configured lengths, in sequence, fitting as many complete work+break cycles as the task's total duration allows.
    - Example: 25 min work / 5 min break settings on a 30-minute task → exactly one work interval (25 min) fits, followed by its break (5 min); the task is then complete — a second work interval doesn't fit in what's left, so Pomodoro only cycles once for that task.
    - Example: 10 min work / 5 min break settings on a 30-minute task → three full work intervals fit exactly (10+10+10 = 30 min of work), with breaks between them (not after the final one, since the task is done at that point).
  - If the task's total duration is shorter than one full work interval, Pomodoro does not apply to that task at all — it just runs as a normal single countdown.
  - If, after fitting as many full work/break cycles as possible, a remaining leftover portion of the task is too short for another full work interval, that leftover portion just runs as a normal (non-Pomodoro) countdown to finish out the task — no partial/broken Pomodoro segment.
- During a work interval: the task is "running" as normal. During a break interval: the task's own countdown pauses, and resumes automatically when the break ends.
- While a task is playing (whether Pomodoro is enabled for it or not), show a persistent notification with the live countdown.
  - If Pomodoro is enabled for that task, the notification also shows a wavy/animated progress bar (Material 3 expressive style) indicating progress toward the next transition — progress toward the upcoming break while in a work interval, and progress toward resuming work while in a break.

## Alarm/Background Permission Not Actually Granting (FIX)
- The exact-alarm/background-related permission request is not properly working — the app is not correctly obtaining this permission, which is one of the things needed to keep the app reliably active in the background. Diagnose and fix so this permission flow actually completes successfully.

## Main Screen Progress Bars Are Duplicating the Same Metric (FIX)
- There are two time-tracking indicators on the main screen: a circular one and a horizontal/straight one. Currently both show the exact same thing (time left today / percent booked), which is redundant.
- Fix so each shows something distinct:
  - **Circular indicator** → keep as-is: how much of today's time is left, and what percentage of that remaining time is already booked by tasks (this part is already correct — don't change its behavior).
  - **Horizontal/straight indicator** → change to show total daily time (24h) vs. how much of it has been used. "Used" here includes both time that has already elapsed in the day AND time still held by tasks that haven't happened yet (i.e. booked/allocated time counts as "used" on this bar too, not just elapsed time) — so if 3 hours of the day have passed and tasks still hold 4–5 more hours somewhere in the day, the horizontal bar reflects all of that combined against the full 24-hour total.

## Task Time-Extension UI Is Messed Up (FIX)
- The icon used for extending an active task's time, and the extension UI/flow itself, currently look broken/off. Fix the layout and visual presentation of the extend-time icon and its associated UI so it displays correctly and cleanly.

---

# TaskFlow — Revision Pass 8 (Fixes) — 2026-09-27

This is a follow-up pass on the existing app (previous revisions already implemented). These are new fixes/requirements only. For each item below, after implementing, verify and confirm the fix actually works as described rather than assuming it's resolved — these are bugs the user has directly observed, so silent/unverified "fixes" are not acceptable.

## Task Completion Needs a Visible Animation (NEW)
- Currently, checking off a task makes it disappear immediately, which reads as unsettling/like something went wrong rather than "task completed."
- Add a clear completion animation (e.g. a checkmark confirmation, satisfying transition, brief highlight before it leaves the list) so the user visually understands the task was successfully completed and moved to Completed — not that it vanished/broke.

## Background Reliability for In-Progress AI Requests (VERIFY / FIX)
- If the user is sending a voice note or text to the AI and backgrounds/closes the app while that request is still in flight, the app should continue trying to send/complete that request in the background rather than abandoning it.
- Confirm whether this currently works; if not, fix it so in-flight AI requests reliably continue and complete even if the app is backgrounded.

## App Crashes (FIX)
- The app crashes intermittently. Investigate and fix the underlying causes of these crashes (add proper crash logging/reporting if not already present, to help identify root causes going forward).

## AI Should Proactively Write Task Info, Not Just Create the Task (NEW — agent behavior)
- When the user verbally/textually explains details about a task while asking the AI to create it, the AI should not just create the bare task — it should also populate the task's info field with those explained details.
- This should be strongly encouraged default behavior (not strictly mandatory in every case, but the AI should lean toward capturing explanatory detail in info rather than discarding it), since the user often explains reasoning/specifics they'll want to reference later and may not remember to ask for it separately.

## Cannot Create a Normal (No-Fixed-Time) Task for a Future Date (FIX)
- Currently, creating a task for a date other than today seems to require giving it a fixed time — there's no way to create a plain/normal task (no fixed time) scheduled for a future date (e.g. "a task for next Friday" with no specific time).
- Fix so normal tasks (without a fixed time) can be created for any date, not just today.

## AI Should Retry/Wait on High-Usage Errors Instead of Failing Immediately (FIX)
- When the Gemini API is under high load/rate-limited, the app currently surfaces this to the user as a generic failure (e.g. implying a quota/API-key problem), even though the real cause is temporary high usage.
- Fix so the app distinguishes this case and retries (with reasonable backoff) until it eventually gets a response, rather than immediately erroring out to the user. Only surface a real failure message if retries are genuinely exhausted or the actual cause is something other than temporary high load (e.g. an actual invalid/expired key).

## AI Sub-task Creation Sometimes Not Nesting Correctly (FIX)
- When asked to create a task inside another task via the AI, it sometimes fails to actually nest it inside the specified parent. Fix so AI-driven sub-task creation reliably nests under the correct parent task every time.

## Date Filter/View Bug — Task Appearing Under Wrong Date Range (FIX — verify and fix)
- A task created for next month was observed appearing in the "today" view, despite its stored date clearly being next month.
- Confirm whether the day/week/month/year filtering logic is actually broken (this sounds like a genuine bug, not user error) and fix it so tasks only appear under the date range(s) they actually belong to.


---

# TaskFlow — Revision Pass 9 (Fixes) — 2026-09-27

This is a follow-up pass on the existing app (previous revisions already implemented). These are new fixes/requirements only. Verify each fix actually works after implementing, not just that it compiles/appears to work.

## Ability to Clear AI Chat Message History (NEW)
- Add a way to clear the AI chat/message history (e.g. from Settings), so the conversation log doesn't grow indefinitely.

## Crash Logs Cannot Be Copied (FIX)
- The crash diagnostic feature added previously does record crashes correctly, but there's no way to actually copy the crash log text out of the app (e.g. to share for debugging).
- Add a copy button/action to the crash log view so the user can copy the log content.

## AI Cannot Create Tasks for Future Dates — Wrongly Applies Today's Time Budget (FIX — critical)
- When asked to create a task for a future date (e.g. "next week"), the AI is incorrectly trying to fit it against today's remaining time budget instead of the target date's, and struggles/fails as a result.
- Fix so the AI correctly creates tasks for any date the user specifies, checking time availability against that task's actual target date, not always today.
- More broadly: the AI must be able to create and manage every type of task the app supports (fixed-time, non-fixed-time, recurring with/without expiration, sub-tasks at any depth, overnight/cross-midnight tasks, tasks for any future date, etc.) — the AI's capabilities must not be more limited than what the user can do manually in the UI. Audit the AI's tool/function set against the full feature list and close any gaps.


---

# TaskFlow — Revision Pass 10 (Crash Fix) — 2026-09-29

This is a follow-up pass on the existing app (previous revisions already implemented). This is a specific, diagnosed crash fix based on collected crash logs.

## Duplicate LazyColumn/Row Key Crash (FIX — confirmed root cause)
- Two separate crashes have been captured, both with the same root cause:
  `java.lang.IllegalArgumentException: Key "<some number>" was already used. If you are using LazyColumn/Row please make sure you provide a unique key for each item.`
- This means somewhere in the app's list UI (task lists, sub-task lists, or similar LazyColumn/LazyRow usage), the `key` parameter being supplied for list items is not guaranteed unique — likely because it's derived from something that can collide (e.g. a hashcode, a recurring task's shared identity across instances, or an ID that isn't actually unique per rendered row).
- Fix: audit every LazyColumn/LazyRow in the app and ensure each item's `key` is a guaranteed-unique, stable value per rendered item (e.g. a proper unique task/sub-task instance ID) — not a value that can collide between unrelated items or between a recurring task's generated instances.
- Pay particular attention to any list that mixes original tasks with generated recurring instances, or any list rendering both a task and something derived from it (e.g. overnight task segments from the earlier overnight-task feature) — these are the most likely sources of two rows ending up with the same key.
- After fixing, verify by reproducing conditions similar to both crash reports (list re-rendering with recurring/duplicated-looking items) to confirm the crash no longer occurs, not just that the code compiles.
