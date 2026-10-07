package com.foukas.dropbox2d.generation;

/** Pure geometry for SEESAW and DOUBLE_DOOR sides.
 *
 * Trapdoor seesaw (design doc docs/designs/seesaw-trapdoor.md, eng review
 * 2026-10-07): the side is wall filler | hole | lip, with a keeled plank
 * centered on the hole. clearanceAt()/passesBall()/minPassAngle() answer
 * "does a tilted plank open a ball-sized gap past the lip corner", and
 * fitsTrapdoorGeometry() is the flanking-span check spawnNextRow()'s retry
 * loop uses -- reject and reroll, never clamp or build broken geometry
 * (same posture as MovingPlatformReachability.fitsSplitBodyGeometry()).
 *
 * Double-door trapdoor (docs/designs/double-door-trapdoor.md): the hole is
 * covered by two flaps hinged at its edges, each held shut against a level
 * joint limit by a counterweight behind its hinge. doubleClearanceAt() /
 * doublePassesBall() / doubleMinPassAngle() are the two-flap clearance
 * checks; holdExcess() / holdThreshold() / tipOpens() are the static lever
 * math behind "land near a hinge and it holds, near the seam and it opens".
 * The layout is the same filler | hole | lip, so fitsTrapdoorGeometry()
 * serves both door types. */
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

    /** Horizontal gap between the two flap tips when both flaps of a
     * double door hang open by angleRadians. Each flap is holeWidth/2 long,
     * hinged at a hole edge at mid-thickness; its innermost point is the
     * upper tip corner at L*cos(theta) + (t/2)*sin(theta) from the hinge, so
     * the gap is W*(1 - cos(theta)) - t*sin(theta) (design doc Constraints;
     * at 90 degrees it is W - t). */
    public static float doubleClearanceAt(float holeWidth, float angleRadians, float thickness) {
        return holeWidth * (1f - (float) Math.cos(angleRadians))
                - thickness * (float) Math.sin(angleRadians);
    }

    /** True if both flaps open to angleRadians leave room for a ball of
     * ballDiameter plus margin. */
    public static boolean doublePassesBall(float holeWidth, float angleRadians, float thickness,
                                           float ballDiameter, float margin) {
        return doubleClearanceAt(holeWidth, angleRadians, thickness) >= ballDiameter + margin;
    }

    /** theta_pass(W) for a double door: the smallest flap angle in
     * [0, pi/2] at which doublePassesBall() holds, or NaN if even vertical
     * flaps can't pass the ball. */
    public static float doubleMinPassAngle(float holeWidth, float thickness, float ballDiameter, float margin) {
        return smallestPassingAngle(a -> doublePassesBall(holeWidth, a, thickness, ballDiameter, margin));
    }

    /** E: a flap's net closing moment at level (per unit gravity) -- the
     * counterweight's moment behind the hinge minus the flap's own moment,
     * whose center sits at holeWidth/4 from the hinge. E > 0 is required
     * for the flap to rest shut against its level limit. */
    public static float holdExcess(float counterweightMass, float counterweightArm,
                                   float flapMass, float holeWidth) {
        return counterweightMass * counterweightArm - flapMass * holeWidth / 4f;
    }

    /** x*: the distance from the hinge beyond which a ball resting on the
     * flap outweighs E and opens it (statically). Closer than x*, the door
     * holds the ball. */
    public static float holdThreshold(float holdExcess, float ballMass) {
        return holdExcess / ballMass;
    }

    /** Static tip-opens invariant: a ball resting tipMargin short of the
     * flap's tip must open it, so a ball reaching the seam always forces
     * the second flap open. Necessary, not sufficient -- the drop sweep's
     * one-flap-open entries prove the dynamic case. */
    public static boolean tipOpens(float holdExcess, float ballMass, float holeWidth, float tipMargin) {
        return holdExcess < ballMass * (holeWidth / 2f - tipMargin);
    }

    /** True if a flanking span fits wall filler + hole + gap-side lip at
     * their minimum widths, for either door type. The hole's position
     * within any slack is the caller's choice (random, eng review D10). */
    public static boolean fitsTrapdoorGeometry(float flankingSpan, float holeWidth,
                                               float minFillerWidth, float minLipWidth) {
        return flankingSpan >= minFillerWidth + holeWidth + minLipWidth;
    }

    private interface AnglePredicate {
        boolean passes(float angleRadians);
    }

    /** Bisection for the smallest angle in [0, pi/2] that passes, assuming
     * the passing angles form one interval ending at pi/2 (true for both
     * clearance functions: each is below the ball size near 0 and increases
     * to its maximum at pi/2 once it starts passing). 40 steps resolve well
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
