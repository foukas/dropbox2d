package com.foukas.dropbox2d.fx;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pure append-and-evict logic for the ball's motion trail (plan-eng-review
 * Next Step 9). No Gdx dependency -- Point is a plain float pair, not
 * Vector2, so this stays trivially unit-testable. Backed by ArrayDeque
 * (O(1) addFirst/removeLast) rather than an ArrayList -- at the trail's
 * actual size (10-15 elements) the difference is negligible, but it's the
 * explicit-over-clever choice that avoids this shape getting copy-pasted
 * somewhere with a real size later. Newest position at the front (head),
 * oldest at the back (tail).
 */
public final class MotionTrail {

    private MotionTrail() {
    }

    /** Appends newPos to the front of positions, evicting from the back
     * until the deque is at most maxLength long. Mutates positions in
     * place. */
    public static void add(Deque<Point> positions, Point newPos, int maxLength) {
        positions.addFirst(newPos);
        while (positions.size() > maxLength) {
            positions.removeLast();
        }
    }

    public static Deque<Point> newBuffer() {
        return new ArrayDeque<>();
    }

    public record Point(float x, float y) {
    }
}
