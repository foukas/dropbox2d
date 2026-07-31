# TODOs

## Audio/SFX design pass
**What:** Menu music, button clicks, and gameplay sounds (impact, gap-pass chime, combo stinger, platform-break crunch).
**Why:** The original design doc (2026-07-27) flagged audio as "entirely unscoped" and "typically as load-bearing as visual 'juice' for arcade game feel." The neon/synthwave visual redesign (2026-07-29) explicitly excludes it too — no audio exists anywhere in the codebase.
**Pros:** Closes a real gap in game feel; the neon/synthwave direction gives a clear audio identity to aim for (synth stingers, arcade beeps) rather than starting from nothing.
**Cons:** A third design doc's worth of decisions (scope: minimal vs. full, licensing/sourcing sound assets, libGDX audio API integration).
**Context:** Run via `/office-hours` when picked up — same pattern as the visual redesign. No prerequisite work exists; can start independently of the neon redesign once that ships.
**Depends on / blocked by:** None — fully independent.

## Content variety follow-up (power-ups, platform types, biome progression)
**What:** More power-ups (lighter/bouncier ball, gap-magnet — both named in the original design doc's Open Questions), more platform/hazard types (moving platforms, seesaws, one-way platforms, spikes), and biome-based depth progression (background/platform-style/hazard-mix shifts every N meters, not just skin-tier color swaps). **New idea (2026-07-31):** a "rampage" power-up — all platforms in range become breakable and the ball smashes through everything, not just weak platforms. Came from stress-testing a bug fix with `WEAK_PLATFORM_CHANCE`/`POWERUP_SPAWN_CHANCE` cranked way up (95%/90%) to reproduce a platform-destruction freeze — the resulting "smash through everything" chaos felt genuinely fun on its own, not just a debug artifact.
**Why:** Explicitly deferred during the 2026-07-29 office-hours session — the user chose "a real art style" over "more content variety" as the top priority, then chose "Not yet — visuals first, content later" when offered this exact expansion. Deferred, not rejected.
**Pros:** `PowerUpManager`'s registry pattern (`core/powerups/`) was explicitly built to make adding power-ups cheap (`register()` + one new class, no architecture changes needed) — this is genuinely low-friction work once picked up.
**Cons:** Platform/hazard types are a bigger lift than power-ups — each needs its own Box2D body behavior, not just a registry entry. Needs its own reachability/fairness tuning pass (see the original design doc's Open Questions on gap-reachability risk).
**Context:** Run via `/office-hours` when picked up. Natural to sequence after the neon/synthwave visual redesign ships, so new content lands on the new visual identity rather than the old placeholder shapes.
**Depends on / blocked by:** None strictly, but sequencing after the visual redesign avoids re-styling new content twice.
