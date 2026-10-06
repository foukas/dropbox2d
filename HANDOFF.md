# Session Handoff — dropbox2d

Written 2026-09-15 (updated 2026-10-06 when the seesaw shipped) to let a different logged-in user/account pick up this project without
re-deriving context. Nothing here is guessable from the code alone — it's operational
knowledge, standing decisions, and state that only existed in a prior chat session.

## Where things stand right now

dropbox2d is a libGDX/Box2D arcade game (`GameplayScreen`/`GameplayRenderer`/`GameOverScreen`,
Box2D physics via `ContactDispatcher`, a `PowerUpManager` registry, `PlayerProgress`/`SkinTier`
depth-based progression). Shipped, playtested, committed, pushed features, each with its own
design doc under `~/.gstack/projects/Test/` (see slug warning below):

- Original prototype + neon/synthwave visual redesign (`foukas-main-design-20260727-113753.md`,
  `...-20260729...md`)
- Biome-based depth progression (`foukas-main-design-20260803-143417.md`)
- Moving platforms (`foukas-main-design-20260805-095358.md`)
- Rampage power-up (`foukas-main-design-20260806-141746.md`, marked `SHIPPED`)
- Seesaw bare mechanic (`foukas-main-design-20260820-150834.md`, marked `SHIPPED` 2026-10-06)
  — the codebase's first Box2D joint (revolute) and first collision filter
  (`SEESAW_NO_COLLIDE_GROUP`, a reserved, world-global `groupIndex`). All 10 Next Steps
  landed in commits `4359400..bdcf638`, playtested on desktop and the Galaxy A56.

**Nothing is in progress.** Next candidates live in `TODOS.md` — including a new "Seesaw
follow-ups" item (angular-velocity launch, passage integration, constant tuning). Ask
whoever is driving what to pick up next; don't assume.

## Gstack slug mismatch (do not lose time rediscovering this)

This repo's gstack tooling computes two DIFFERENT project slugs depending on which script
runs: some paths resolve to `~/.gstack/projects/foukas-dropbox2d/`, others to
`~/.gstack/projects/Test/`. **The real, current design docs live under `Test/`, not
`foukas-dropbox2d/`.** Every design-doc lookup this session hit this mismatch. If a skill's
own "Design Doc Check" bash script reports "No design doc found" or points at the wrong slug,
manually check `~/.gstack/projects/Test/*.md` before believing that.

## Environment facts

- Windows 10, PowerShell primary shell; git-bash/POSIX sh also used for gstack skill scripts.
- `jq` is **not installed** — any gstack skill step that writes a JSONL artifact (e.g.
  `/plan-eng-review`'s Implementation Tasks JSONL) is expected to skip with a warning. This is
  normal, not a bug to fix.
- `open` (for launching URLs/files) is **not available** — gstack skill steps that try to
  `open` something are expected to fail gracefully. Also normal.
- Codex CLI is **not installed** — every `/office-hours` and `/plan-eng-review` "outside
  voice" cross-model pass this session fell back to a Claude subagent instead. Expected.

## Android playtest device

The real physical playtest phone is a **Samsung Galaxy A56, ADB serial `RZCY20R862T`**.
`adb devices -l` may also show `A66T020C10100002` (a T20 device — NOT the playtest phone) and
sometimes a Genymotion/AVD emulator (`emulator-5554`) — don't confuse these.

Deploy and launch:
```bash
ANDROID_SERIAL=RZCY20R862T ./gradlew :android:installDebug
adb -s RZCY20R862T shell monkey -p com.foukas.dropbox2d -c android.intent.category.LAUNCHER 1
```

A desktop build can be self-verified via a PowerShell `EnumWindows` + `GetWindowRect` +
`Graphics.CopyFromScreen` technique targeting the specific window by title (never
full-screen). This was used once, then the user explicitly said not to bother with
screenshot self-verification going forward unless there's a specific reason — prefer handing
off to the user for a real playtest over self-verifying visually.

## Established per-feature workflow (followed for every shipped feature so far)

One Next-Steps item at a time: implement → compile/run the full test suite → build (desktop,
and Android when relevant) → the user plays it on desktop and/or the physical Galaxy A56 →
user says "commit it" → user says "push it". Each step is its own commit, referencing the
design doc and any eng-review findings that shaped it. Direct playtest bug reports from the
user are folded into the SAME implementation loop (fix → retest → rebuild → redeploy), not
treated as a new design cycle, unless the finding reveals a genuinely new, unscoped design
question.

**Do not assume standing autonomous commit/push permission carries over to a new user/account
— confirm it explicitly before committing or pushing without per-step confirmation.**

## Standing decisions — do not silently revisit these

- **Desktop vs. mobile ball-color discrepancy:** the desktop build shows a whitish/pale ball
  by default (a `DIAMOND`-tier `SkinTier` unlock) while mobile shows purple — this is because
  an earlier testing session's own gameplay inflated the desktop save file's `bestDepth` to
  ~343m as a side effect of manual verification, not a bug in the game. The user was told this
  and explicitly said: **leave it as-is, revisit "later as the game improves in features."**
  Do not "fix" this without it being raised again by the user.
- **Seesaw + rampage interaction:** deliberately deferred (not scoped into the seesaw bare
  mechanic). The plank is never breakable; its filler IS rampage-breakable (explicit user
  call, 2026-10-06). Tracked as its own item in `TODOS.md` ("Seesaw + rampage interaction
  (deferred)") with the reasoning already written out there — read it before re-deciding this.

## What to read, in order, to fully resume

1. This file.
2. `TODOS.md` (repo root) — current backlog, most recently updated when the seesaw shipped.
3. `git log --oneline -20` in the repo — recent commit history for what's actually landed.
   Design docs for any shipped feature are under `~/.gstack/projects/Test/` (see slug warning).
