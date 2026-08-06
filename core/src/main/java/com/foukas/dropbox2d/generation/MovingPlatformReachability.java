package com.foukas.dropbox2d.generation;

/** Amplitude-shrink transform (moving-platforms design doc, plan-eng-review
 * 2026-08-06) -- extends GapReachabilityValidator's own conservative-
 * worst-case philosophy rather than modifying it. GapReachabilityValidator
 * assumes a gap's edges are fixed across the timeToFall window it checks; a
 * MOVING flanking platform violates that directly, since its gap-facing
 * edge can be anywhere within its patrol amplitude at the moment the ball
 * falls. shrinkForAmplitude() validates against the narrowest gap the
 * platform's patrol could ever produce (0 amplitude for a non-MOVING side)
 * -- callers pass the result straight into
 * GapReachabilityValidator.isReachable() instead of the raw spawn-time
 * gapStart/gapWidth.
 *
 * fitsSplitBodyGeometry() is a separate, smaller check (plan-eng-review
 * Code Quality finding): a MOVING side's flanking span is only guaranteed
 * to be at least 0.5 world units (GameplayScreen.spawnNextRow()'s gapStart
 * random range), which is not automatically enough to fit a static filler,
 * a fixed-width kinematic piece, and the patrol amplitude. The retry loop
 * must check this per attempt and reroll on failure, the same way it
 * already rerolls on reachability failure -- never clamp or degrade the
 * geometry silently. */
public final class MovingPlatformReachability {

    private MovingPlatformReachability() {
    }

    /** The narrowest gap this row's patrol amplitudes could ever produce.
     * leftAmplitude/rightAmplitude are 0 for a side that isn't MOVING, in
     * which case this returns gapStart/gapWidth unchanged -- identical to
     * today's un-shrunk isReachable() call. */
    public static AdjustedGap shrinkForAmplitude(float gapStart, float gapWidth, float leftAmplitude, float rightAmplitude) {
        float adjustedStart = gapStart + leftAmplitude;
        float adjustedWidth = gapWidth - leftAmplitude - rightAmplitude;
        return new AdjustedGap(adjustedStart, adjustedWidth);
    }

    /** True if a flanking span of the given size can fit a static filler,
     * a fixed-width kinematic piece, and the patrol amplitude the piece's
     * rest position overlaps the filler by (see the design doc's
     * seam-invariant note for why the overlap is exactly the amplitude,
     * not a separate quantity). */
    public static boolean fitsSplitBodyGeometry(float flankingSpan, float kinematicWidth, float amplitude, float minFillerWidth) {
        return flankingSpan >= kinematicWidth + amplitude + minFillerWidth;
    }

    /** gapStart/gapWidth to validate via GapReachabilityValidator.isReachable()
     * in place of the raw spawn-time values -- see this class's doc. */
    public record AdjustedGap(float gapStart, float gapWidth) {
    }
}
