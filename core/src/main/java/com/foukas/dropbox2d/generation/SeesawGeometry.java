package com.foukas.dropbox2d.generation;

/** Pure geometry for a SEESAW side.
 *
 * Trapdoor seesaw (design doc docs/designs/seesaw-trapdoor.md, eng review
 * 2026-10-07): the side is wall filler | hole | lip, with a keeled plank
 * centered on the hole. clearanceAt()/passesBall()/minPassAngle() answer
 * "does a tilted plank open a ball-sized gap past the lip corner", and
 * fitsTrapdoorGeometry() is the flanking-span check spawnNextRow()'s retry
 * loop uses -- reject and reroll, never clamp or build broken geometry
 * (same posture as MovingPlatformReachability.fitsSplitBodyGeometry()).
 *
 * fitsSeesawGeometry() is the shipped flanking seesaw's check (seesaw
 * design doc, 2026-08-20). It stays only until the trapdoor is wired into
 * spawnNextRow(), then goes. */
public final class SeesawGeometry {

    private SeesawGeometry() {
    }

    /** True if a flanking span of the given size fits a plank of
     * 2 * plankHalfLength plus at least minFillerWidth of filler between
     * the plank's wall-side end and the wall. */
    public static boolean fitsSeesawGeometry(float flankingSpan, float plankHalfLength, float minFillerWidth) {
        return flankingSpan >= 2f * plankHalfLength + minFillerWidth;
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
     * holds, or NaN if even a vertical plank can't pass the ball.
     * clearanceAt() is strictly increasing on [0, pi/2], so bisection is
     * exact to well under a hundredth of a degree in 40 steps. */
    public static float minPassAngle(float holeWidth, float thickness, float ballDiameter, float margin) {
        float halfPi = (float) (Math.PI / 2);
        if (!passesBall(holeWidth, halfPi, thickness, ballDiameter, margin)) {
            return Float.NaN;
        }
        float lo = 0f;
        float hi = halfPi;
        for (int i = 0; i < 40; i++) {
            float mid = (lo + hi) / 2f;
            if (passesBall(holeWidth, mid, thickness, ballDiameter, margin)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    /** True if a flanking span fits wall filler + hole + gap-side lip at
     * their minimum widths. The hole's position within any slack is the
     * caller's choice (random, eng review D10). */
    public static boolean fitsTrapdoorGeometry(float flankingSpan, float holeWidth,
                                               float minFillerWidth, float minLipWidth) {
        return flankingSpan >= minFillerWidth + holeWidth + minLipWidth;
    }
}
