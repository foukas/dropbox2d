# TODOs

## Audio/SFX design pass
**What:** Menu music, button clicks, and gameplay sounds (impact, gap-pass chime, combo stinger, platform-break crunch).
**Why:** The original design doc (2026-07-27) flagged audio as "entirely unscoped" and "typically as load-bearing as visual 'juice' for arcade game feel." The neon/synthwave visual redesign (2026-07-29) explicitly excludes it too — no audio exists anywhere in the codebase.
**Pros:** Closes a real gap in game feel; the neon/synthwave direction gives a clear audio identity to aim for (synth stingers, arcade beeps) rather than starting from nothing.
**Cons:** A third design doc's worth of decisions (scope: minimal vs. full, licensing/sourcing sound assets, libGDX audio API integration).
**Context:** Run via `/office-hours` when picked up — same pattern as the visual redesign. No prerequisite work exists; can start independently of the neon redesign once that ships.
**Depends on / blocked by:** None — fully independent.

## Content variety follow-up (power-ups, platform types) — biome slice shipped
**What:** More power-ups (lighter/bouncier ball, gap-magnet, the "rampage" idea below) and more platform/hazard types (moving platforms, seesaws, one-way platforms, spikes). Biome-based depth progression (background/hazard-mix/ball-damping shifting by depth-band, 2 biomes cycling every 100m) shipped 2026-08-04 (design doc `foukas-main-design-20260803-143417.md`) — no longer part of this deferred item. **New idea (2026-07-31):** a "rampage" power-up — all platforms in range become breakable and the ball smashes through everything, not just weak platforms. Came from stress-testing a bug fix with `WEAK_PLATFORM_CHANCE`/`POWERUP_SPAWN_CHANCE` cranked way up (95%/90%) to reproduce a platform-destruction freeze — the resulting "smash through everything" chaos felt genuinely fun on its own, not just a debug artifact.
**Why:** Explicitly deferred during the 2026-07-29 office-hours session — the user chose "a real art style" over "more content variety" as the top priority. Biomes were picked back up first (2026-08-03) as the fastest wedge and are now done; power-ups and hazard types are what's left deferred.
**Pros:** `PowerUpManager`'s registry pattern (`core/powerups/`) was explicitly built to make adding power-ups cheap (`register()` + one new class, no architecture changes needed) — this is genuinely low-friction work once picked up.
**Cons:** Platform/hazard types are a bigger lift than power-ups — each needs its own Box2D body behavior, not just a registry entry. Needs its own reachability/fairness tuning pass (see the original design doc's Open Questions on gap-reachability risk).
**Context:** Run via `/office-hours` when picked up. See the wedge-comparison item below before picking the next piece — that comparison hasn't actually been done yet.
**Depends on / blocked by:** None strictly, but sequencing after the biome slice ships avoids re-styling new content twice.

## Validate the content-variety wedge choice before picking the next piece
**What:** The biome-progression design doc (2026-08-03) asserted it was the "fastest, highest-impact wedge" among power-ups/platform-types/biomes, but that comparison was never actually shown — `/plan-eng-review`'s outside-voice pass (2026-08-04) caught this. The rampage power-up (see the item above) is already informally confirmed fun, reuses the already-correct `PowerUpManager` registry, and needed zero physics-safety re-derivation — unlike biomes, which needed a full force/damping equilibrium check and a depth-source contradiction fix (`depthScore` vs. lifetime-best) before the plan was implementable. Biomes were kept as the current slice (a deliberate call, not an oversight — see the design doc's "What I noticed about how you think" section for why), but the underlying comparison gap is real and should be closed before choosing what comes after biomes.
**Why:** Prevents re-asserting "fastest wedge" without evidence a second time when picking the next content-variety piece.
**Pros:** Cheap to do — a real cost/impact comparison across the 2-3 remaining candidates (rampage power-up, other power-ups, platform/hazard types) takes one `/office-hours` or planning pass, not new code.
**Cons:** None really — this is a planning-quality fix, not a feature.
**Context:** Run before starting the next content-variety slice, once the biome work ships. Not urgent — the biome slice itself doesn't depend on this.
**Depends on / blocked by:** None — can happen any time before the next content-variety slice is picked, does not block the current biome work.
