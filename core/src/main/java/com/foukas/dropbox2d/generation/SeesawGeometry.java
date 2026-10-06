package com.foukas.dropbox2d.generation;

/** Flanking-span-fit check for a SEESAW side (seesaw design doc, plan-eng-
 * review 2026-08-20, Next Step 5) -- mirrors
 * MovingPlatformReachability.fitsSplitBodyGeometry()'s shape and reason for
 * existing. A flanking span is only guaranteed to be at least 0.5 world
 * units (GameplayScreen.spawnNextRow()'s gapStart random range), which is
 * not automatically enough for a seesaw: the plank's full 2L length sits
 * between the gap edge and the wall, and the filler must still extend past
 * the plank's wall-side end by at least minFillerWidth. spawnNextRow()'s
 * retry loop rejects and rerolls on failure -- never clamp or build broken
 * geometry.
 *
 * Deliberately NOT a filler-clearance check against L*sin(thetaMax): the
 * plank and filler share a groupIndex filter so the plank's rotation never
 * collides with its own filler (design doc Premise 3, round-2 correction).
 * This only caps 2L against the available span. */
public final class SeesawGeometry {

    private SeesawGeometry() {
    }

    /** True if a flanking span of the given size fits a plank of
     * 2 * plankHalfLength plus at least minFillerWidth of filler between
     * the plank's wall-side end and the wall. */
    public static boolean fitsSeesawGeometry(float flankingSpan, float plankHalfLength, float minFillerWidth) {
        return flankingSpan >= 2f * plankHalfLength + minFillerWidth;
    }
}
