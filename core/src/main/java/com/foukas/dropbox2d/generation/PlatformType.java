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
 * SEESAW platforms are a weight-triggered TRAPDOOR seesaw
 * (docs/designs/seesaw-trapdoor.md, 2026-10-07; the name is kept from the
 * original flanking seesaw, eng review D4): a keeled plank pinned at its
 * center by a revolute joint covers a hole in the flanking platform. Land
 * off-center and it swings open, dropping the ball through a second way
 * down that keeps the combo; the keel and damping close it again. Always
 * an extra passage, never the only one -- the row's gap is untouched.
 * Mutually exclusive with MOVING and WEAK; rolled after both (see
 * rollPlatformType()). */
public enum PlatformType {
    NORMAL,
    WEAK,
    MOVING,
    SEESAW
}
