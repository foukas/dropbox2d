package com.foukas.dropbox2d.generation;

/** NORMAL platforms are solid and permanent, matching Approach A. WEAK
 * platforms break under sufficient impact (see DestructiblePlatform).
 * Weak segments always flank the row's gap, never replace it -- the gap
 * itself is never platform material, so a weak segment breaking can only
 * ever open an *additional* passage, never remove the only reachable
 * landing spot. That's what resolves the design doc's "destructible
 * platform reachability risk" open question by construction rather than
 * needing a runtime minimum-safe-platform guarantee.
 *
 * MOVING platforms (plan-eng-review, moving-platforms design doc,
 * 2026-08-06) patrol back and forth via a kinematic Box2D body, mutually
 * exclusive with WEAK for this slice -- see GameplayScreen's
 * rollPlatformType() for the roll order and MovingPlatformReachability for
 * the gap-fairness math a moving flanking platform requires.
 *
 * SEESAW platforms (seesaw design doc, plan-eng-review 2026-08-20) are a
 * dynamic plank pinned to a static fulcrum by a revolute joint -- this
 * codebase's first Box2D joint. Mutually exclusive with MOVING and WEAK
 * for this slice; rolled after both (see rollPlatformType()). */
public enum PlatformType {
    NORMAL,
    WEAK,
    MOVING,
    SEESAW
}
