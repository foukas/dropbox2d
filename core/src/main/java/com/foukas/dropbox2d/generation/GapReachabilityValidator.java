package com.foukas.dropbox2d.generation;

/**
 * Pure, engine-free fairness check for procedurally-generated gaps. Given
 * only the gap's geometry and the ball's known movement constants, can a
 * worst-case-positioned ball reach the gap before falling past this row?
 *
 * Deliberately conservative: assumes the ball starts falling from a dead
 * stop and needs to reach the gap from the world edge farthest from it.
 * Both assumptions make actual reachability strictly easier than what this
 * function requires, so it only ever rejects a gap that's easier than the
 * worst case computed here -- it can produce false negatives against a
 * skilled/lucky player, never false positives that let a truly unreachable
 * gap through.
 */
public final class GapReachabilityValidator {

    private GapReachabilityValidator() {
    }

    public static boolean isReachable(float gapStart, float gapWidth, float worldWidth,
                                       float maxHorizontalSpeed, float timeToFall) {
        if (gapWidth <= 0f || gapStart < 0f || gapWidth > worldWidth || gapStart + gapWidth > worldWidth) {
            return false;
        }
        if (timeToFall <= 0f || maxHorizontalSpeed <= 0f) {
            return false;
        }

        float worstCaseDistance = worstCaseDistanceToGap(gapStart, gapWidth, worldWidth);
        float maxHorizontalTravel = maxHorizontalSpeed * timeToFall;
        return maxHorizontalTravel >= worstCaseDistance;
    }

    /** The farther of the gap's two edges is reachable from whichever world
     * edge is farthest from the gap; the nearer edge is always at least as
     * close. Distance from the worst-case starting edge to the NEAREST gap
     * edge is what actually needs covering. */
    static float worstCaseDistanceToGap(float gapStart, float gapWidth, float worldWidth) {
        float gapEnd = gapStart + gapWidth;
        float distanceFromLeftWall = gapStart;
        float distanceFromRightWall = worldWidth - gapEnd;
        return Math.max(distanceFromLeftWall, distanceFromRightWall);
    }
}
