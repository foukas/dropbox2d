package com.foukas.dropbox2d.fx;

import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionTrailTest {

    @Test
    void addingToAnEmptyBufferLeavesExactlyOnePosition() {
        Deque<MotionTrail.Point> positions = MotionTrail.newBuffer();
        MotionTrail.add(positions, new MotionTrail.Point(1f, 2f), 10);

        assertEquals(1, positions.size());
        assertEquals(new MotionTrail.Point(1f, 2f), positions.peekFirst());
    }

    @Test
    void belowMaxLengthAppendsWithoutEvicting() {
        Deque<MotionTrail.Point> positions = MotionTrail.newBuffer();
        MotionTrail.add(positions, new MotionTrail.Point(0f, 0f), 5);
        MotionTrail.add(positions, new MotionTrail.Point(1f, 1f), 5);
        MotionTrail.add(positions, new MotionTrail.Point(2f, 2f), 5);

        assertEquals(3, positions.size());
        // Newest at the front.
        assertEquals(new MotionTrail.Point(2f, 2f), positions.peekFirst());
        assertEquals(new MotionTrail.Point(0f, 0f), positions.peekLast());
    }

    @Test
    void atMaxLengthEvictsTheOldestToStayAtMaxLength() {
        Deque<MotionTrail.Point> positions = MotionTrail.newBuffer();
        int maxLength = 3;
        for (int i = 0; i < maxLength; i++) {
            MotionTrail.add(positions, new MotionTrail.Point(i, i), maxLength);
        }
        assertEquals(maxLength, positions.size());

        // One more push should evict position 0, the oldest.
        MotionTrail.add(positions, new MotionTrail.Point(99f, 99f), maxLength);

        assertEquals(maxLength, positions.size());
        assertEquals(new MotionTrail.Point(99f, 99f), positions.peekFirst());
        assertEquals(new MotionTrail.Point(1f, 1f), positions.peekLast());

        Iterator<MotionTrail.Point> it = positions.iterator();
        assertEquals(new MotionTrail.Point(99f, 99f), it.next());
        assertEquals(new MotionTrail.Point(2f, 2f), it.next());
        assertEquals(new MotionTrail.Point(1f, 1f), it.next());
        assertTrue(!it.hasNext());
    }

    @Test
    void maxLengthOneAlwaysKeepsOnlyTheNewestPosition() {
        Deque<MotionTrail.Point> positions = MotionTrail.newBuffer();
        MotionTrail.add(positions, new MotionTrail.Point(0f, 0f), 1);
        MotionTrail.add(positions, new MotionTrail.Point(1f, 1f), 1);
        MotionTrail.add(positions, new MotionTrail.Point(2f, 2f), 1);

        assertEquals(1, positions.size());
        assertEquals(new MotionTrail.Point(2f, 2f), positions.peekFirst());
    }

    @Test
    void manyPushesNeverExceedMaxLength() {
        Deque<MotionTrail.Point> positions = MotionTrail.newBuffer();
        int maxLength = 12;
        for (int i = 0; i < 500; i++) {
            MotionTrail.add(positions, new MotionTrail.Point(i, i), maxLength);
            assertTrue(positions.size() <= maxLength);
        }
        assertEquals(maxLength, positions.size());
    }
}
