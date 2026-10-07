package com.foukas.dropbox2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.foukas.dropbox2d.generation.SeesawGeometry;
import com.foukas.dropbox2d.physics.SeesawFactory;
import com.foukas.dropbox2d.physics.SeesawFactory.DoorParams;
import com.foukas.dropbox2d.progression.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless drop test for the trapdoor seesaw (docs/designs/seesaw-trapdoor.md,
 * eng review 2026-10-07: T0, D2, D5, D6, D8, D9). Drops and rolls the real
 * ball (GameplayScreen.createBallBody/applySteer) onto real doors
 * (SeesawFactory.buildDoor) and classifies every run as passed, wedged,
 * held, bounced out or unclassified.
 *
 * focusedGuardForShippedDoor() runs in the normal suite against the
 * constants the game ships (GameplayScreen.SEESAW_DOOR). fullSweep() is
 * tagged "sweep" -- run it with ./gradlew :core:sweepTest -- and writes
 * build/reports/seesaw-sweep.csv, the table the shipped constants were
 * picked from. Lives in the root package so it can reach GameplayScreen's
 * package-private ball statics (D2). */
class SeesawDropTest {

    private static final float STEP = 1f / 60f;
    private static final float ROW_Y = 0f;
    private static final float THICKNESS = GameplayScreen.PLATFORM_THICKNESS;
    private static final float BALL_RADIUS = GameplayScreen.BALL_RADIUS;
    private static final float BALL_DIAMETER = 2f * BALL_RADIUS;
    private static final float MARGIN = 0.1f;
    private static final float WALL_THICKNESS = 0.3f;
    private static final float GRAVITY = -25f;

    // Thresholds (design doc Next Step 2 + eng review T0).
    private static final float T_PASS = 1.0f;
    private static final float SETTLE_WINDOW = 1.5f;
    private static final float SETTLE_ANGLE = (float) Math.toRadians(5);
    private static final float STALL_SPEED = 0.2f;
    private static final float STALL_WINDOW = 0.5f;
    private static final float CLASSIFY_AFTER_CONTACT = 3.0f;
    private static final float PRE_CONTACT_LIMIT = 2.0f;
    private static final float AIRBORNE_GAP = 0.1f;
    private static final float CENTER_NUDGE = 0.01f; // D5
    private static final float RELEASE_WINDOW = 1.5f;

    @BeforeAll
    static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    // ---------------------------------------------------------------- model

    enum Outcome { PASSED, WEDGED, HELD, BOUNCED, UNCLASSIFIED }

    enum Steer { NONE, TOWARD, AGAINST }

    enum EntryKind { DROP, ROLL }

    /** One layout of the flanking span: total span width and hole placement. */
    record Layout(String name, float span, float placement) {
    }

    record Entry(EntryKind kind, float offsetOrStartFromHoleEdge, float speedOrHeight, int direction, boolean fromFiller) {
        String describe() {
            return kind == EntryKind.DROP
                    ? String.format(Locale.ROOT, "drop x=%+.2f h=%.1f", offsetOrStartFromHoleEdge, speedOrHeight)
                    : String.format(Locale.ROOT, "roll from %s v=%.0f", fromFiller ? "filler" : "lip", speedOrHeight);
        }
    }

    record RunResult(Outcome outcome, float tPass, boolean grazed, boolean settled, float tOpen, float rebound,
                     String description) {
    }

    // ------------------------------------------------------------- one run

    /** Bodies the ball can touch, counted per begin/end contact. */
    private static final class Contacts implements ContactListener {
        final Body ball;
        final Body plank;
        final Body filler;
        final Body lip;
        final boolean lowRestitution;
        int plankCount;
        int fillerCount;
        int lipCount;
        int otherCount;

        Contacts(Body ball, Body plank, Body filler, Body lip, boolean lowRestitution) {
            this.ball = ball;
            this.plank = plank;
            this.filler = filler;
            this.lip = lip;
            this.lowRestitution = lowRestitution;
        }

        private Body other(Contact c) {
            Body a = c.getFixtureA().getBody();
            Body b = c.getFixtureB().getBody();
            if (a == ball) return b;
            if (b == ball) return a;
            return null;
        }

        private void count(Body other, int delta) {
            if (other == plank) plankCount += delta;
            else if (other == filler) fillerCount += delta;
            else if (other == lip) lipCount += delta;
            else otherCount += delta;
        }

        @Override public void beginContact(Contact c) {
            Body o = other(c);
            if (o != null) count(o, 1);
        }

        @Override public void endContact(Contact c) {
            Body o = other(c);
            if (o != null) count(o, -1);
        }

        @Override public void preSolve(Contact c, Manifold m) {
            // The plank restitution override the design doc may adopt
            // (Constraints, "Impact dominates"): Box2D mixes restitution as
            // the max, so it can only be lowered per contact here.
            if (lowRestitution && other(c) == plank) {
                c.setRestitution(0.1f);
            }
        }

        @Override public void postSolve(Contact c, ContactImpulse i) {
        }

        int total() {
            return plankCount + fillerCount + lipCount + otherCount;
        }
    }

    static RunResult run(DoorParams p, Layout layout, Entry entry, Steer steer, float damping, boolean lowRestitution) {
        World world = new World(new Vector2(0f, GRAVITY), true);
        try {
            // Left-side flanking span: wall at x = 0, gap edge at x = span.
            BodyDef wallDef = new BodyDef();
            wallDef.type = BodyDef.BodyType.StaticBody;
            wallDef.position.set(-WALL_THICKNESS / 2f, ROW_Y);
            Body wall = world.createBody(wallDef);
            PolygonShape wallShape = new PolygonShape();
            wallShape.setAsBox(WALL_THICKNESS / 2f, 10f);
            wall.createFixture(wallShape, 0f);
            wallShape.dispose();

            SeesawFactory.Door door = SeesawFactory.buildDoor(world, 0f, layout.span(), ROW_Y, p, layout.placement());
            float holeStart = door.holeStart();
            float holeEnd = door.holeEnd();
            float pivotX = (holeStart + holeEnd) / 2f;
            float plankHalf = p.holeWidth() / 2f + p.plankOverlap();
            float restY = ROW_Y + THICKNESS / 2f + BALL_RADIUS;

            Body ball;
            int towardSign;
            if (entry.kind() == EntryKind.DROP) {
                float x = pivotX + entry.offsetOrStartFromHoleEdge();
                if (entry.speedOrHeight() < 0f) {
                    // Fast entry: start just above the plank already moving.
                    ball = GameplayScreen.createBallBody(world, x, restY + 0.05f);
                    ball.setLinearVelocity(0f, entry.speedOrHeight());
                } else {
                    ball = GameplayScreen.createBallBody(world, x, restY + entry.speedOrHeight());
                }
                towardSign = entry.offsetOrStartFromHoleEdge() >= 0f ? 1 : -1;
            } else {
                float startX = entry.fromFiller()
                        ? holeStart - entry.offsetOrStartFromHoleEdge()
                        : holeEnd + entry.offsetOrStartFromHoleEdge();
                ball = GameplayScreen.createBallBody(world, startX, restY + 0.01f);
                ball.setLinearVelocity(entry.direction() * entry.speedOrHeight(), 0f);
                towardSign = entry.direction();
            }
            ball.setLinearDamping(damping);
            float steerValue = steer == Steer.NONE ? 0f : steer == Steer.TOWARD ? towardSign : -towardSign;
            boolean steered = steer != Steer.NONE;

            Contacts contacts = new Contacts(ball, door.plank(), door.filler(), door.lip(), lowRestitution);
            world.setContactListener(contacts);

            float t = 0f;
            float firstPlankContact = Float.NaN;
            float finalLandingStart = Float.NaN;
            float airborne = 0f;
            boolean grazed = false;
            float passTime = Float.NaN;
            float stallSince = 0f;
            float settledSince = Float.NaN;
            float maxY = Float.NEGATIVE_INFINITY;
            boolean wasPlank = false;

            boolean landed = false;
            boolean released = false;
            float releaseUntil = Float.NaN;
            while (true) {
                // Steering starts at the first contact: it models leaning
                // on the door, and steering through a whole drop would move
                // the landing point so far that "offset x" and toward/
                // against stop meaning anything.
                landed = landed || contacts.total() > 0;
                GameplayScreen.applySteer(ball, landed ? steerValue : 0f);
                world.step(STEP, 6, 2);
                t += STEP;

                Vector2 pos = ball.getPosition();
                boolean onPlank = contacts.plankCount > 0;
                if (onPlank && !wasPlank && (Float.isNaN(firstPlankContact) || airborne >= AIRBORNE_GAP)) {
                    finalLandingStart = t;
                }
                if (onPlank && Float.isNaN(firstPlankContact)) {
                    firstPlankContact = t;
                }
                wasPlank = onPlank;
                airborne = contacts.total() == 0 ? airborne + STEP : 0f;
                if (!Float.isNaN(firstPlankContact)) {
                    maxY = Math.max(maxY, pos.y);
                    if (Float.isNaN(passTime) && (contacts.fillerCount > 0 || contacts.lipCount > 0)) {
                        grazed = true;
                    }
                }
                if (ball.getLinearVelocity().len() < STALL_SPEED) {
                    stallSince += STEP;
                } else {
                    stallSince = 0f;
                }

                boolean overHole = pos.x >= holeStart && pos.x <= holeEnd;
                if (Float.isNaN(passTime) && pos.y < ROW_Y - THICKNESS / 2f - BALL_RADIUS && overHole
                        && !Float.isNaN(firstPlankContact)) {
                    passTime = t;
                }

                float angle = Math.abs(door.plank().getAngle());
                if (!Float.isNaN(passTime)) {
                    if (angle < SETTLE_ANGLE) {
                        if (Float.isNaN(settledSince)) settledSince = t;
                    } else {
                        settledSince = Float.NaN;
                    }
                }

                // Termination.
                if (!Float.isNaN(passTime)) {
                    if (t >= passTime + SETTLE_WINDOW + STALL_WINDOW) {
                        boolean settled = !Float.isNaN(settledSince) && settledSince - passTime <= SETTLE_WINDOW;
                        float tPass = passTime - (Float.isNaN(finalLandingStart) ? firstPlankContact : finalLandingStart);
                        return new RunResult(Outcome.PASSED, tPass, grazed, settled,
                                passTime - firstPlankContact, maxY - ROW_Y, entry.describe());
                    }
                    continue;
                }
                if (Float.isNaN(firstPlankContact)) {
                    // Never reached the plank. A drop that misses it (or a
                    // roll that stops short) bounced out; a roll still
                    // moving at the pre-contact limit is unclassified.
                    if (t >= PRE_CONTACT_LIMIT) {
                        // A steered roll that turns away from the door
                        // never reaches it -- that's bounced out, not an
                        // anomaly.
                        Outcome o = entry.kind() == EntryKind.ROLL && stallSince < STALL_WINDOW && !steered
                                ? Outcome.UNCLASSIFIED : Outcome.BOUNCED;
                        return new RunResult(o, Float.NaN, grazed, true, Float.NaN, 0f, entry.describe());
                    }
                    continue;
                }
                // Release check (sweep revision, user-approved 2026-10-07):
                // full steering can pin the ball against a strip
                // indefinitely, which is the player's doing, not the door's.
                // A steered run still undecided at the classify point lets go
                // of the steering for RELEASE_WINDOW and is classified after
                // -- a real wedge stays stuck without any input.
                if (steerValue != 0f && !released && t >= firstPlankContact + CLASSIFY_AFTER_CONTACT) {
                    released = true;
                    steerValue = 0f;
                    releaseUntil = t + RELEASE_WINDOW;
                }
                if (t >= (released ? releaseUntil : firstPlankContact + CLASSIFY_AFTER_CONTACT)) {
                    boolean stalled = stallSince >= STALL_WINDOW;
                    boolean touchesStrip = contacts.fillerCount > 0 || contacts.lipCount > 0;
                    Outcome o;
                    if (stalled && onPlank && touchesStrip && (angle >= SETTLE_ANGLE || overHole)) {
                        o = Outcome.WEDGED; // D6
                    } else if (stalled && onPlank && !touchesStrip && contacts.otherCount == 0) {
                        o = Outcome.HELD;
                    } else if (!overHole) {
                        o = Outcome.BOUNCED;
                    } else if (steered) {
                        // Still on the door after the release window: the
                        // steering pinned it there; counts as held (sweep
                        // revision). A real wedge was caught above.
                        o = Outcome.HELD;
                    } else {
                        o = Outcome.UNCLASSIFIED;
                    }
                    return new RunResult(o, Float.NaN, grazed, true, Float.NaN, maxY - ROW_Y, entry.describe());
                }
            }
        } finally {
            world.dispose(); // native memory: every run owns and frees its World
        }
    }

    // ------------------------------------------------------- run generation

    static List<Layout> layouts(DoorParams p) {
        float minSpan = p.minFillerWidth() + p.holeWidth() + p.minLipWidth();
        float typical = p.holeWidth() + 2.0f;
        return List.of(
                new Layout("min", minSpan, 0.5f),
                new Layout("typical-wall", typical, 0f),
                new Layout("typical-center", typical, 0.5f),
                new Layout("typical-gap", typical, 1f));
    }

    static List<Entry> entries(DoorParams p, Layout layout) {
        float half = p.holeWidth() / 2f + p.plankOverlap();
        List<Float> offsets = new ArrayList<>(List.of(CENTER_NUDGE, 0.2f, -0.2f, 0.4f, -0.4f, 0.6f, -0.6f,
                0.8f, -0.8f, half - 0.2f, -(half - 0.2f)));
        List<Entry> list = new ArrayList<>();
        for (float x : offsets) {
            list.add(new Entry(EntryKind.DROP, x, 2.6f, 0, false));
            list.add(new Entry(EntryKind.DROP, x, 7.8f, 0, false));
            list.add(new Entry(EntryKind.DROP, x, -25f, 0, false));
        }
        // Roll-on only from a strip at least 1.0 wide (D8 as amended).
        float slack = layout.span() - p.minFillerWidth() - p.holeWidth() - p.minLipWidth();
        float filler = p.minFillerWidth() + slack * layout.placement();
        float lip = layout.span() - filler - p.holeWidth();
        for (float v : new float[]{3f, 6f}) {
            if (filler >= 1.0f) list.add(new Entry(EntryKind.ROLL, 0.5f, v, +1, true));
            if (lip >= 1.0f) list.add(new Entry(EntryKind.ROLL, 0.5f, v, -1, false));
        }
        return list;
    }

    static float[] biomeDampings() {
        TreeSet<Float> set = new TreeSet<>();
        for (Biome b : Biome.values()) set.add(b.getLinearDamping());
        float[] out = new float[set.size()];
        int i = 0;
        for (float d : set) out[i++] = d;
        return out;
    }

    // ----------------------------------------------------------- evaluation

    /** Aggregate verdict for one parameter set against the acceptance thresholds. */
    record Verdict(DoorParams params, boolean lowRestitution, int runs, int passed, int wedged, int held,
                   int bounced, int unclassified, List<String> failures, float tOpen, float maxTPass,
                   float maxRebound) {
        boolean qualifies() {
            return failures.isEmpty();
        }
    }

    static Verdict evaluate(DoorParams p, boolean lowRestitution) {
        int runs = 0, passed = 0, wedged = 0, held = 0, bounced = 0, unclassified = 0;
        float maxTPass = 0f, maxRebound = 0f;
        float tOpen = Float.NaN;
        List<String> failures = new ArrayList<>();
        float baseline = biomeDampings()[0];
        for (float damping : biomeDampings()) {
            for (Layout layout : layouts(p)) {
                for (Entry entry : entries(p, layout)) {
                    for (Steer steer : Steer.values()) {
                        RunResult r = run(p, layout, entry, steer, damping, lowRestitution);
                        runs++;
                        String where = String.format(Locale.ROOT, "%s %s steer=%s damp=%.1f",
                                layout.name(), r.description(), steer, damping);
                        switch (r.outcome()) {
                            case PASSED -> passed++;
                            case WEDGED -> wedged++;
                            case HELD -> held++;
                            case BOUNCED -> bounced++;
                            case UNCLASSIFIED -> unclassified++;
                        }
                        if (r.outcome() == Outcome.WEDGED || r.outcome() == Outcome.UNCLASSIFIED) {
                            failures.add(r.outcome() + ": " + where);
                        }
                        // Sweep revision (user-approved 2026-10-07): steered
                        // runs only have to never wedge -- full steering can
                        // legitimately outrun the door or pin the ball.
                        if (r.outcome() == Outcome.HELD && steer == Steer.NONE) {
                            failures.add("HELD without steering: " + where);
                        }
                        if (r.outcome() == Outcome.PASSED) {
                            maxTPass = Math.max(maxTPass, r.tPass());
                            maxRebound = Math.max(maxRebound, r.rebound());
                            if (!r.settled()) failures.add("did not settle: " + where);
                        }
                        boolean mustPass = entry.kind() == EntryKind.DROP
                                && Math.abs(entry.offsetOrStartFromHoleEdge()) >= 0.4f
                                && steer == Steer.NONE;
                        if (mustPass) {
                            if (r.outcome() != Outcome.PASSED) {
                                failures.add("off-center landing did not pass (" + r.outcome() + "): " + where);
                            } else if (r.tPass() > T_PASS) {
                                failures.add(String.format(Locale.ROOT, "slow pass %.2fs: %s", r.tPass(), where));
                            }
                            boolean oneRowNoSteer = entry.speedOrHeight() == 2.6f && steer == Steer.NONE;
                            // Only landings whose whole footprint is over
                            // the hole: a ball that overhangs a strip at
                            // landing really did touch the platform, and
                            // resetting the combo is correct there.
                            boolean footprintOverHole = Math.abs(entry.offsetOrStartFromHoleEdge()) + BALL_RADIUS
                                    <= p.holeWidth() / 2f;
                            if (oneRowNoSteer && footprintOverHole && r.outcome() == Outcome.PASSED && r.grazed()) {
                                failures.add("grazed filler/lip (combo reset): " + where);
                            }
                        }
                        if (damping == baseline && layout.name().equals("typical-center")
                                && entry.kind() == EntryKind.DROP && entry.offsetOrStartFromHoleEdge() == CENTER_NUDGE
                                && entry.speedOrHeight() == 2.6f && steer == Steer.NONE) {
                            tOpen = r.outcome() == Outcome.PASSED ? r.tOpen() : Float.POSITIVE_INFINITY;
                        }
                    }
                }
            }
        }
        return new Verdict(p, lowRestitution, runs, passed, wedged, held, bounced, unclassified, failures,
                tOpen, maxTPass, maxRebound);
    }

    // ---------------------------------------------------- the focused guard

    @Test
    void focusedGuardForShippedDoor() {
        Verdict v = evaluate(GameplayScreen.SEESAW_DOOR, GameplayScreen.SEESAW_DOOR_LOW_RESTITUTION);
        String sample = v.failures().stream().limit(15).collect(Collectors.joining("\n  "));
        assertTrue(v.qualifies(), v.failures().size() + " threshold failures over " + v.runs()
                + " runs for the shipped door:\n  " + sample);
    }

    // The expected-fail controls (eng review T0, R3-1): holes too small to
    // pass the ball at any reachable angle must never classify as passed --
    // proves the classifier can see a too-small hole.
    @Test
    void tooSmallHolesNeverPassTheBall() {
        DoorParams shipped = GameplayScreen.SEESAW_DOOR;
        DoorParams[] controls = {
                withGeometry(shipped, 2.0f, (float) Math.toRadians(70)),
                withGeometry(shipped, 1.6f, (float) Math.toRadians(85)),
        };
        for (DoorParams c : controls) {
            for (Layout layout : layouts(c)) {
                for (Entry entry : entries(c, layout)) {
                    if (entry.kind() != EntryKind.DROP) continue;
                    RunResult r = run(c, layout, entry, Steer.NONE, biomeDampings()[0],
                            GameplayScreen.SEESAW_DOOR_LOW_RESTITUTION);
                    assertTrue(r.outcome() != Outcome.PASSED, "control W=" + c.holeWidth()
                            + " passed the ball: " + layout.name() + " " + r.description());
                }
            }
        }
    }

    @Test
    void holePlacementRespectsMinimumStripsOnBothSides() {
        DoorParams p = GameplayScreen.SEESAW_DOOR;
        float span = p.holeWidth() + 2.0f;
        for (float placement : new float[]{0f, 0.5f, 1f}) {
            for (boolean gapToTheRight : new boolean[]{true, false}) {
                World world = new World(new Vector2(0f, GRAVITY), true);
                try {
                    float wallX = gapToTheRight ? 0f : span;
                    float gapEdgeX = gapToTheRight ? span : 0f;
                    SeesawFactory.Door d = SeesawFactory.buildDoor(world, wallX, gapEdgeX, 0f, p, placement);
                    float fillerWidth = gapToTheRight ? d.holeStart() : span - d.holeEnd();
                    float lipWidth = gapToTheRight ? span - d.holeEnd() : d.holeStart();
                    assertTrue(fillerWidth >= p.minFillerWidth() - 1e-4f, "filler too thin: " + fillerWidth);
                    assertTrue(lipWidth >= p.minLipWidth() - 1e-4f, "lip too thin: " + lipWidth);
                    assertTrue(Math.abs(d.holeEnd() - d.holeStart() - p.holeWidth()) < 1e-4f);
                } finally {
                    world.dispose();
                }
            }
        }
    }

    // Double door basics (double-door eng review T2): with no load each
    // counterweighted flap rests shut against its level limit, and a
    // downward push at the tip swings it open in its signed direction
    // (left flap clockwise/negative, right flap counter-clockwise/positive).
    @Test
    void doubleDoorRestsShutAndOpensDownwardAtTheTips() {
        SeesawFactory.DoubleDoorParams p = new SeesawFactory.DoubleDoorParams(1.55f, (float) Math.toRadians(90),
                0.45f, 0.5f, 0.2f, 5f, 0.5f, THICKNESS, 0.3f, 0.3f, GameplayScreen.SEESAW_NO_COLLIDE_GROUP);
        World world = new World(new Vector2(0f, GRAVITY), true);
        try {
            SeesawFactory.DoubleDoor d = SeesawFactory.buildDoubleDoor(world, 0f, 3.0f, 0f, p, 0.5f);
            for (int i = 0; i < 120; i++) world.step(STEP, 6, 2);
            assertTrue(Math.abs(d.leftFlap().getAngle()) < 0.01f, "left flap should rest shut: " + d.leftFlap().getAngle());
            assertTrue(Math.abs(d.rightFlap().getAngle()) < 0.01f, "right flap should rest shut: " + d.rightFlap().getAngle());

            float seam = (d.holeStart() + d.holeEnd()) / 2f;
            for (int i = 0; i < 30; i++) {
                d.leftFlap().applyForce(0f, -40f, seam - 0.05f, 0f, true);
                d.rightFlap().applyForce(0f, -40f, seam + 0.05f, 0f, true);
                world.step(STEP, 6, 2);
            }
            assertTrue(d.leftFlap().getAngle() < -0.3f, "left flap should open clockwise: " + d.leftFlap().getAngle());
            assertTrue(d.rightFlap().getAngle() > 0.3f, "right flap should open counter-clockwise: " + d.rightFlap().getAngle());
        } finally {
            world.dispose();
        }
    }

    private static DoorParams withGeometry(DoorParams p, float holeWidth, float maxAngle) {
        return new DoorParams(holeWidth, maxAngle, p.keelMass(), p.keelDepth(), p.angularDamping(),
                p.plankDensity(), p.plankOverlap(), p.thickness(), p.minFillerWidth(), p.minLipWidth(),
                p.groupIndex());
    }

    // ------------------------------------------------------- the full sweep

    @Tag("sweep")
    @Test
    void fullSweep() throws IOException {
        DoorParams base = GameplayScreen.SEESAW_DOOR;
        float ballMass = (float) (Math.PI * BALL_RADIUS * BALL_RADIUS); // density 1
        List<DoorParams> cells = new ArrayList<>();
        // Sweep history (design doc ledger S1/S2): W {2.3, 2.5, 2.7} with
        // damping {0.5..8} qualified nothing; the best cells all sat at the
        // widest hole and damping 5, so this pass widens W and centers
        // damping there (user-approved 2026-10-07).
        for (float w : new float[]{2.7f, 2.9f, 3.1f}) {
            float thetaPass = SeesawGeometry.minPassAngle(w, THICKNESS, BALL_DIAMETER, MARGIN);
            for (float deg : new float[]{70f, 75f, 80f, 85f}) {
                float maxAngle = (float) Math.toRadians(deg);
                if (Float.isNaN(thetaPass) || maxAngle < thetaPass + Math.toRadians(5)) continue; // T0
                float half = w / 2f + base.plankOverlap();
                float keelBound = ballMass * half / (float) Math.tan(thetaPass);
                for (float m : new float[]{0.1f, 0.2f, 0.3f}) {
                    for (float k : new float[]{0.3f, 0.6f, 1.0f}) {
                        if (m * k >= keelBound) continue; // statically infeasible
                        // Damping widened to {5, 8} and plank overlap added
                        // as an axis (sweep revision, user-approved
                        // 2026-10-07): settle and lip-graze failures.
                        for (float damp : new float[]{4.0f, 5.0f, 6.0f}) {
                            for (float overlap : new float[]{0f, 0.1f}) {
                                cells.add(new DoorParams(w, maxAngle, m, k, damp, base.plankDensity(),
                                        overlap, base.thickness(), base.minFillerWidth(),
                                        base.minLipWidth(), base.groupIndex()));
                            }
                        }
                    }
                }
            }
        }
        List<Verdict> verdicts = new ArrayList<>();
        for (boolean low : new boolean[]{false, true}) {
            List<Verdict> batch = cells.parallelStream().map(c -> evaluate(c, low)).collect(Collectors.toList());
            verdicts.addAll(batch);
        }

        Path out = Paths.get("build", "reports", "seesaw-sweep.csv");
        Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("holeWidth,maxAngleDeg,keelMass,keelDepth,angularDamping,lowRestitution,runs,passed,"
                    + "wedged,held,bounced,unclassified,failures,tOpen,maxTPass,maxRebound,qualifies,overlap,firstFailure");
            for (Verdict v : verdicts) {
                DoorParams p = v.params();
                w.printf(Locale.ROOT, "%.2f,%.0f,%.2f,%.2f,%.2f,%b,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%b,%.2f,\"%s\"%n",
                        p.holeWidth(), Math.toDegrees(p.maxAngleRadians()), p.keelMass(), p.keelDepth(),
                        p.angularDamping(), v.lowRestitution(), v.runs(), v.passed(), v.wedged(), v.held(),
                        v.bounced(), v.unclassified(), v.failures().size(), v.tOpen(), v.maxTPass(),
                        v.maxRebound(), v.qualifies(), p.plankOverlap(),
                        v.failures().isEmpty() ? "" : v.failures().get(0).replace("\"", "'"));
            }
        }

        // Selection rule (eng review T0/R3-7): as-is restitution first if
        // any as-is cell qualifies; then smallest W, smallest thetaMax,
        // largest T_open, fastest settle (proxied by highest damping).
        boolean anyAsIs = verdicts.stream().anyMatch(v -> v.qualifies() && !v.lowRestitution());
        Optional<Verdict> chosen = verdicts.stream()
                .filter(Verdict::qualifies)
                .filter(v -> !anyAsIs || !v.lowRestitution())
                .min(Comparator.<Verdict>comparingDouble(v -> v.params().holeWidth())
                        .thenComparingDouble(v -> v.params().maxAngleRadians())
                        .thenComparingDouble(v -> -v.tOpen())
                        .thenComparingDouble(v -> -v.params().angularDamping()));
        long qualifying = verdicts.stream().filter(Verdict::qualifies).count();
        System.out.println("SWEEP: " + verdicts.size() + " cells, " + qualifying + " qualify. Table: "
                + out.toAbsolutePath());
        System.out.println("SWEEP CHOSEN: " + chosen.map(v -> String.format(Locale.ROOT,
                "W=%.2f thetaMax=%.0fdeg M=%.2f k=%.2f damping=%.2f overlap=%.2f lowRestitution=%b tOpen=%.3f maxTPass=%.3f",
                v.params().holeWidth(), Math.toDegrees(v.params().maxAngleRadians()), v.params().keelMass(),
                v.params().keelDepth(), v.params().angularDamping(), v.params().plankOverlap(), v.lowRestitution(), v.tOpen(),
                v.maxTPass())).orElse("NONE"));
        assertTrue(chosen.isPresent(), "no door parameter set meets every acceptance threshold; see " + out);
    }
}
