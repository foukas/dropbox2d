package com.foukas.dropbox2d.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.MassData;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;

/** Builds static platform segments and the trapdoor seesaw (design doc
 * docs/designs/seesaw-trapdoor.md, eng review 2026-10-07). One builder for
 * both, so the game's rows, its doors and the headless drop test can never
 * disagree about platform material (eng review D3, D11) -- the drop test's
 * never-wedges guarantee only means something if it tests the bodies that
 * ship.
 *
 * Door layout along one flanking span, wall to gap edge:
 *
 *   wall | filler (>= minFiller) | hole (W) | lip (>= minLip) | gap
 *                                 \__plank__/  centered on the hole,
 *                                    overhanging each edge by plankOverlap
 *
 * Door states (pure physics: keel + angular damping, no motor):
 *
 *   LEVEL --ball off-center--> TIPPING --theta >= theta_pass--> OPEN --ball below row--> PASSED
 *     ^                                                                                   |
 *     +----------------- keel moment + angular damping settle it back --------------------+
 *
 * The plank is tagged "seesawPlank" and never breakable; filler and lip are
 * ordinary "platform" segments. Plank, filler and lip share the reserved
 * no-collide group so the swinging plank never jams on its own strips. */
public final class SeesawFactory {

    // Shared platform material (eng review D11): static segments, the
    // moving platform's kinematic piece and the door plank all read these.
    public static final float PLATFORM_FRICTION = 0.6f;
    public static final float PLATFORM_RESTITUTION = 0f;

    public static final String PLANK_TAG = "seesawPlank";

    private SeesawFactory() {
    }

    /** A static box segment from xStart to xEnd, centered vertically on y. */
    public static Body staticSegment(World world, float xStart, float xEnd, float y, float thickness,
                                     String tag, short groupIndex) {
        float width = xEnd - xStart;
        float centerX = xStart + width / 2f;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(centerX, y);
        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2f, thickness / 2f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = PLATFORM_FRICTION;
        fixtureDef.restitution = PLATFORM_RESTITUTION;
        fixtureDef.filter.groupIndex = groupIndex;
        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData(tag);
        shape.dispose();

        return body;
    }

    /** Everything that shapes a door. Values come from the drop sweep, not
     * hand-derivation (design doc Next Step 2). */
    public record DoorParams(float holeWidth, float maxAngleRadians, float keelMass, float keelDepth,
                             float angularDamping, float plankDensity, float plankOverlap,
                             float thickness, float minFillerWidth, float minLipWidth, short groupIndex) {
    }

    public record Door(Body filler, Body lip, Body fulcrum, Body plank, RevoluteJoint joint,
                       float holeStart, float holeEnd) {
    }

    /** Builds a door on the flanking span between wallX and gapEdgeX
     * (either order, like the other flanking-segment builders). placement
     * in [0, 1] slides the hole across the span's slack: 0 puts it right
     * after the minimum filler, 1 right before the minimum lip. The caller
     * must have checked SeesawGeometry.fitsTrapdoorGeometry() first; a span
     * that doesn't fit throws rather than building broken geometry. */
    public static Door buildDoor(World world, float wallX, float gapEdgeX, float y, DoorParams p, float placement) {
        float span = Math.abs(gapEdgeX - wallX);
        float slack = span - p.minFillerWidth() - p.holeWidth() - p.minLipWidth();
        if (slack < -1e-4f) {
            throw new IllegalArgumentException("span " + span + " too narrow for a door with hole " + p.holeWidth());
        }
        slack = Math.max(0f, slack);
        float clampedPlacement = Math.max(0f, Math.min(1f, placement));

        float sign = gapEdgeX > wallX ? 1f : -1f;
        float fillerWidth = p.minFillerWidth() + slack * clampedPlacement;
        float holeNear = wallX + sign * fillerWidth;
        float holeFar = holeNear + sign * p.holeWidth();
        float pivotX = (holeNear + holeFar) / 2f;

        Body filler = staticSegment(world, Math.min(wallX, holeNear), Math.max(wallX, holeNear), y,
                p.thickness(), "platform", p.groupIndex());
        Body lip = staticSegment(world, Math.min(holeFar, gapEdgeX), Math.max(holeFar, gapEdgeX), y,
                p.thickness(), "platform", p.groupIndex());

        BodyDef fulcrumDef = new BodyDef();
        fulcrumDef.type = BodyDef.BodyType.StaticBody;
        fulcrumDef.position.set(pivotX, y);
        Body fulcrum = world.createBody(fulcrumDef);

        BodyDef plankDef = new BodyDef();
        plankDef.type = BodyDef.BodyType.DynamicBody;
        plankDef.position.set(pivotX, y);
        Body plank = world.createBody(plankDef);

        float halfLength = p.holeWidth() / 2f + p.plankOverlap();
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(halfLength, p.thickness() / 2f);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = p.plankDensity();
        fixtureDef.friction = PLATFORM_FRICTION;
        fixtureDef.restitution = PLATFORM_RESTITUTION;
        fixtureDef.filter.groupIndex = p.groupIndex();
        plank.createFixture(fixtureDef).setUserData(PLANK_TAG);
        shape.dispose();

        RevoluteJointDef jointDef = new RevoluteJointDef();
        jointDef.initialize(fulcrum, plank, new Vector2(pivotX, y));
        jointDef.enableLimit = true;
        jointDef.lowerAngle = -p.maxAngleRadians();
        jointDef.upperAngle = p.maxAngleRadians();
        jointDef.enableMotor = false;
        RevoluteJoint joint = (RevoluteJoint) world.createJoint(jointDef);

        addPointMass(plank, p.keelMass(), 0f, -p.keelDepth());
        plank.setAngularDamping(p.angularDamping());

        float holeStart = Math.min(holeNear, holeFar);
        return new Door(filler, lip, fulcrum, plank, joint, holeStart, holeStart + p.holeWidth());
    }

    /** Adds a point mass at (localX, localY) in the body's frame -- the
     * single door's keel below its pivot, or any other off-center weight a
     * future door needs (double-door eng review D7). Honors the body's
     * existing center of mass: new center = (m0*c0 + m*p) / (m0 + m).
     * Box2D's MassData.I is about the body origin, so the point adds
     * m*|p|^2 directly. For the single door the plank's own center is the
     * origin, so this reproduces the original keel math exactly. Must be
     * the last mass-affecting call: any later createFixture() runs
     * resetMassData() and silently drops it. Joint anchors are
     * origin-relative, so calling this after the joint exists is safe. */
    static void addPointMass(Body body, float mass, float localX, float localY) {
        MassData before = body.getMassData();
        float totalMass = before.mass + mass;
        MassData after = new MassData();
        after.mass = totalMass;
        after.center.set((before.mass * before.center.x + mass * localX) / totalMass,
                (before.mass * before.center.y + mass * localY) / totalMass);
        after.I = before.I + mass * localX * localX + mass * localY * localY;
        body.setMassData(after);
    }
}
