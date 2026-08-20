# TODOs

## Audio/SFX design pass
**What:** Menu music, button clicks, and gameplay sounds (impact, gap-pass chime, combo stinger, platform-break crunch).
**Why:** The original design doc (2026-07-27) flagged audio as "entirely unscoped" and "typically as load-bearing as visual 'juice' for arcade game feel." The neon/synthwave visual redesign (2026-07-29) explicitly excludes it too — no audio exists anywhere in the codebase.
**Pros:** Closes a real gap in game feel; the neon/synthwave direction gives a clear audio identity to aim for (synth stingers, arcade beeps) rather than starting from nothing.
**Cons:** A third design doc's worth of decisions (scope: minimal vs. full, licensing/sourcing sound assets, libGDX audio API integration).
**Context:** Run via `/office-hours` when picked up — same pattern as the visual redesign. No prerequisite work exists; can start independently of the neon redesign once that ships.
**Depends on / blocked by:** None — fully independent.

## Content variety follow-up (power-ups, platform types) — biome, moving-platform, and rampage slices shipped
**What:** More power-ups (lighter/bouncier ball, gap-magnet) and more platform/hazard types (seesaws, one-way platforms, spikes). Biome-based depth progression shipped 2026-08-04 (design doc `foukas-main-design-20260803-143417.md`); moving platforms shipped 2026-08-06 (design doc `foukas-main-design-20260805-095358.md`); the rampage power-up (all platforms breakable for its duration, escalating spectacle FX, rampage-local counter) shipped 2026-08-06 (design doc `foukas-main-design-20260806-141746.md`) — none of the three are part of this deferred item anymore.
**Why:** Explicitly deferred during the 2026-07-29 office-hours session — the user chose "a real art style" over "more content variety" as the top priority. Biomes, moving platforms, then rampage were picked up as successive wedges; other power-ups and the remaining hazard types (seesaws, one-way, spikes) are what's left deferred.
**Pros:** `PowerUpManager`'s registry pattern (`core/powerups/`) was explicitly built to make adding power-ups cheap (`register()` + one new class, no architecture changes needed) — confirmed genuinely low-friction: rampage reused `AbstractDensityPowerUp`'s shared lifecycle directly.
**Cons:** Remaining platform/hazard types are a bigger lift than power-ups — each needs its own Box2D body behavior, not just a registry entry. Moving platforms' own reachability/fairness derivation (amplitude-shrink transform, split-body geometry) is a template for how involved this gets.
**Context:** Run via `/office-hours` when picked up. The wedge-comparison item below is now resolved — see its updated text for what building rampage actually revealed.
**Depends on / blocked by:** None strictly.

## Validate the content-variety wedge choice before picking the next piece — RESOLVED (partially, by direct experience)
**What:** The biome-progression design doc (2026-08-03) asserted it was the "fastest, highest-impact wedge" without showing the comparison; the moving-platforms design doc (2026-08-05) reopened the same unresolved comparison a second time, again choosing the harder option over rampage. This session (2026-08-06) finally picked rampage — the specific option this item kept flagging as the cheap alternative. **What actually happened once built:** rampage was NOT the "zero-physics-re-derivation, cheap" feature the earlier framing implied. `/plan-eng-review` found 8 real findings (a physics-safety failsafe needing two correction rounds, a `MovingPlatformManager` bug resurrected by the wider scope, a scoring-model conflict, a power-up exclusivity gap) — comparable in depth to the moving-platforms review, not a quick registry-entry job. A formal side-by-side comparison against the *other* remaining candidates (other power-ups, seesaws/one-way/spikes) still never happened — but the specific repeated pattern this item tracked (rampage assumed cheap, picked last, never validated) is now closed by direct build experience: it wasn't actually cheap, and now there's real data instead of an assumption for the next comparison.
**Why:** Kept for the historical record — if a future planning pass wants "what did each content-variety piece actually cost," this item + the three shipped design docs are the source data.
**Pros:** N/A — informational now, not an open action item.
**Cons:** None.
**Context:** A real comparison across the *remaining* candidates (other power-ups, seesaws/one-way/spikes) still hasn't been done — if that matters before the next pick, it's a fresh planning pass, not a continuation of this item.
**Depends on / blocked by:** None — closed.

## Moving+weak combo platform (Approach C, deferred from moving-platforms doc)
**What:** A platform that's both `MOVING` and `WEAK` — drifts, and breaks if the ball lands on it, forcing a ride-it-or-bail decision. Falls out of existing `PlatformType` values plus a velocity flag once the base moving-platform mechanic (design doc `foukas-main-design-20260805-095358.md`) exists — no third system needed.
**Why:** Explicitly deferred in that design doc's Approaches Considered (Approach C) and Open Questions — closer to Icy Tower's combinatorial-hazard feel than a single new type, but bundling it into the initial wedge would re-widen the narrow-vs-wide tension the doc's Premise 3 deliberately resolved toward "one type first."
**Pros:** Cheap once Approach B's foundation exists (reuses `DestructiblePlatform`'s break logic and Approach B's kinematic-body mechanism directly).
**Cons:** Widens scope back toward variety; better as a fast follow-up than bundled into the base slice.
**Context:** Revisit only after the base moving-platform mechanic (Approach B) ships and has been playtested — evaluating the combo before the base mechanic exists isn't meaningful.
**Depends on / blocked by:** The moving-platforms design doc shipping first.

## Investigate moving-platform friction dragging the ball near a gap edge
**What:** A moving platform's friction (0.6, unchanged from static platforms — `GameplayScreen.createPlatformSegment()`) could drag a ball resting near a gap edge laterally, in a way the gap-reachability math doesn't model. Flagged by the outside-voice review during `/plan-eng-review` on the moving-platforms design doc (2026-08-06) — not raised in any of that doc's 3 spec-review rounds.
**Why:** `GapReachabilityValidator`'s amplitude-shrink transform only bounds the ball's worst-case fall trajectory through the gap; it says nothing about lateral drag from standing on a moving surface near an edge.
**Pros:** Cheap to check — first pass is just playtest observation during the moving-platform feature's own Step 10 playtest, no new code needed unless it's actually a problem.
**Cons:** Speculative — may be imperceptible, or could even read as an intentional "nudge" rather than a bug.
**Context:** Revisit during the moving-platform feature's Step 10 playtest pass. If the ball visibly gets dragged in a way that feels like a bug rather than a feature, that's the trigger to act.
**Depends on / blocked by:** The moving-platforms design doc shipping and being playtested first — can't be meaningfully evaluated before the mechanic exists.
