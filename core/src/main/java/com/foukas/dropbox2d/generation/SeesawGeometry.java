package com.foukas.dropbox2d.generation;

/** Pure geometry for SEESAW sides.
 *
 * Trapdoor seesaw (design doc docs/designs/seesaw-trapdoor.md, eng review
 * 2026-10-07): the side is wall filler | hole | lip, with a keeled plank
 * centered on the hole. clearanceAt()/passesBall()/minPassAngle() answer
 * "does a tilted plank open a ball-sized gap past the lip corner", and
 * fitsTrapdoorGeometry() is the flanking-span check spawnNextRow()'s retry
 * loop uses -- reject and reroll, never clamp or build broken geometry
 * (same posture as MovingPlatformReachability.fitsSplitBodyGeometry()).
 */
public final class SeesawGeometry {

    private SeesawGeometry() {
    }

    /** Perpendicular distance from a lip corner to a plank of the given
     * thickness, centered on a hole of width holeWidth and tilted by
     * angleRadians, minus both half-thicknesses -- i.e. the widest ball
     * that fits between them. Design doc Constraints:
     * (W/2)*sin(theta) - (t/2)*cos(theta) - t/2. */
    public static float clearanceAt(float holeWidth, float angleRadians, float thickness) {
        float halfThickness = thickness / 2f;
        return (holeWidth / 2f) * (float) Math.sin(angleRadians)
                - halfThickness * (float) Math.cos(angleRadians)
                - halfThickness;
    }

    /** True if the plank tilted to angleRadians leaves room for a ball of
     * ballDiameter plus margin. */
    public static boolean passesBall(float holeWidth, float angleRadians, float thickness,
                                     float ballDiameter, float margin) {
        return clearanceAt(holeWidth, angleRadians, thickness) >= ballDiameter + margin;
    }

    /** theta_pass(W): the smallest tilt in [0, pi/2] at which passesBall()
     * holds, or NaN if even a vertical plank can't pass the ball. */
    public static float minPassAngle(float holeWidth, float thickness, float ballDiameter, float margin) {
        return smallestPassingAngle(a -> passesBall(holeWidth, a, thickness, ballDiameter, margin));
    }

    /** True if a flanking span fits wall filler + hole + gap-side lip at
     * their minimum widths. The hole's position
     * within any slack is the caller's choice (random, eng review D10). */
    public static boolean fitsTrapdoorGeometry(float flankingSpan, float holeWidth,
                                               float minFillerWidth, float minLipWidth) {
        return flankingSpan >= minFillerWidth + holeWidth + minLipWidth;
    }

    private interface AnglePredicate {
        boolean passes(float angleRadians);
    }

    /** Bisection for the smallest angle in [0, pi/2] that passes, assuming
     * the passing angles form one interval ending at pi/2 (true for
     * clearanceAt(), which is strictly increasing there). 40 steps resolve well
     * under a hundredth of a degree. */
    private static float smallestPassingAngle(AnglePredicate predicate) {
        float halfPi = (float) (Math.PI / 2);
        if (!predicate.passes(halfPi)) {
            return Float.NaN;
        }
        float lo = 0f;
        float hi = halfPi;
        for (int i = 0; i < 40; i++) {
            float mid = (lo + hi) / 2f;
            if (predicate.passes(mid)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }
}
