package com.foukas.dropbox2d.generation;

/** NORMAL platforms are solid and permanent, matching Approach A. WEAK
 * platforms break under sufficient impact (see DestructiblePlatform).
 * Weak segments always flank the row's gap, never replace it -- the gap
 * itself is never platform material, so a weak segment breaking can only
 * ever open an *additional* passage, never remove the only reachable
 * landing spot. That's what resolves the design doc's "destructible
 * platform reachability risk" open question by construction rather than
 * needing a runtime minimum-safe-platform guarantee. */
public enum PlatformType {
    NORMAL,
    WEAK
}
