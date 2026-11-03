package dev.playermodel;

import dev.playermodel.util.AngularMotion;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AngularMotionTest {
    private static void sameRotation(Quaternionf a, Quaternionf b) {
        assertEquals(1F, Math.abs(a.dot(b)), 1e-5F);
    }
    @Test void mirroredBranchesReceiveSameModelSpaceRotation() {
        Vector3f force = new Vector3f(.35F, .12F, -.06F);
        Quaternionf expected = new Quaternionf().rotationAxis(force.length(), new Vector3f(force).normalize());
        for (float yaw : new float[]{.84F, -.82F}) {
            Quaternionf rest = new Quaternionf().rotationZYX(.12F, yaw, -.55F);
            Quaternionf local = AngularMotion.localOffset(rest, force);
            sameRotation(new Quaternionf(expected).mul(rest), new Quaternionf(rest).mul(local));
        }
    }
    @Test void childCompensatesInheritedRotationWithoutDisablingAxes() {
        Quaternionf restParent = new Quaternionf().rotationZYX(.1F, .7F, -.2F);
        Quaternionf staticChild = new Quaternionf().rotationZYX(.2F, -.8F, .4F);
        Quaternionf restChild = new Quaternionf(restParent).mul(staticChild);
        Quaternionf parent = new Quaternionf(restParent).mul(AngularMotion.localOffset(restParent,
                new Vector3f(.5F, .2F, .1F)));
        Quaternionf desiredChild = new Quaternionf(restChild).mul(AngularMotion.localOffset(restChild,
                new Vector3f(.3F, -.1F, .2F)));
        Quaternionf local = new Quaternionf(parent).invert().mul(desiredChild);
        sameRotation(desiredChild, new Quaternionf(parent).mul(local));
        assertTrue(Math.abs(local.y) > 1e-3F);
        assertTrue(Math.abs(local.z) > 1e-3F);
    }
    @Test void subdivisionChangesAccuracyNotElapsedTime() {
        AngularMotion a = new AngularMotion(), b = new AngularMotion();
        Vector3f target = new Vector3f(.5F, 0, 0);
        for(int i=0;i<60;i++) {
            a.advance(target,8,.9F,1.5F,6,1,1F/60,4);
            b.advance(target,8,.9F,1.5F,6,1,1F/60,20);
        }
        assertTrue(a.angle.distance(b.angle) < .01F);
    }
    @Test void comparableAtThirtyAndOneHundredFortyFourFps() {
        AngularMotion a=new AngularMotion(), b=new AngularMotion();
        Vector3f target=new Vector3f(.5F,-.1F,.2F);
        for(int i=0;i<30;i++) a.advance(target,8,.9F,1.5F,6,1,1F/30,8);
        for(int i=0;i<144;i++) b.advance(target,8,.9F,1.5F,6,1,1F/144,8);
        assertTrue(a.angle.distance(b.angle)<.01F);
    }
    @Test void directionReversalRetainsVelocityInsteadOfSnapping() {
        AngularMotion s=new AngularMotion();
        for(int i=0;i<10;i++) s.advance(new Vector3f(.6F,0,0),8,.8F,1,6,1,1F/120,4);
        float before=s.angle.x;
        s.advance(new Vector3f(-.6F,0,0),8,.8F,1,6,1,1F/120,4);
        assertTrue(Math.abs(s.angle.x-before)<.05F);
    }
    @Test void largeTargetsAndLongRunRemainFiniteAndBounded() {
        AngularMotion s=new AngularMotion();
        for(int i=0;i<10000;i++) {
            s.advance(new Vector3f((i%2==0?1:-1)*100,20,-10),40,.1F,0,25,.8F,.05F,32);
            assertTrue(s.angle.isFinite()); assertTrue(s.velocity.isFinite());
            assertTrue(s.angle.length()<=.80001F);
            assertTrue(s.velocity.length()<=25.0001F);
        }
    }
    @Test void asymmetricAxisLimitsConstrainStateAndOutwardVelocity() {
        for (float sign : new float[]{-1, 1}) {
            AngularMotion s = new AngularMotion();
            Vector3f limits = new Vector3f(.1F, .3F, .6F);
            for (int i = 0; i < 120; i++) {
                s.advance(new Vector3f(sign * 10), 40, .1F, 0, 25, .05F, limits, 1F/60, 8);
                for (int axis = 0; axis < 3; axis++) {
                    assertTrue(Math.abs(s.angle.get(axis)) <= limits.get(axis));
                    if (Math.abs(s.angle.get(axis)) == limits.get(axis))
                        assertTrue(s.velocity.get(axis) * sign <= 0);
                }
            }
            assertEquals(sign * limits.x, s.angle.x, 1e-6F);
            assertEquals(sign * limits.y, s.angle.y, 1e-6F);
            assertEquals(sign * limits.z, s.angle.z, 1e-6F);
            assertEquals(0, s.velocity.length(), 1e-6F);
        }
    }
    @Test void asymmetricAngleRangesKeepBothSidesIndependent() {
        AngularMotion s = new AngularMotion();
        Vector3f min = new Vector3f(-.1F, -.2F, -.3F);
        Vector3f max = new Vector3f(.4F, .05F, .6F);
        for (int i = 0; i < 120; i++) {
            s.advance(new Vector3f(10, -10, 10), 40, .1F, 0, 25, 1,
                    min, max, 1F / 60, 8);
            assertTrue(s.angle.x >= min.x && s.angle.x <= max.x);
            assertTrue(s.angle.y >= min.y && s.angle.y <= max.y);
            assertTrue(s.angle.z >= min.z && s.angle.z <= max.z);
        }
        assertEquals(max.x, s.angle.x, 1e-5F);
        assertEquals(min.y, s.angle.y, 1e-5F);
        assertEquals(max.z, s.angle.z, 1e-5F);
    }

    @Test void zeroAxisLocksPositionAndVelocityWithoutDisablingOtherAxes() {
        AngularMotion s = new AngularMotion();
        s.angle.set(.4F, .2F, -.3F);
        s.velocity.set(-4, 1, 3);
        for (int i = 0; i < 60; i++) {
            s.advance(new Vector3f(10, .3F, -10), 8, .9F, 1, 6, 1,
                    new Vector3f(0, .5F, 0), 1F/60, 8);
            assertEquals(0, s.angle.x); assertEquals(0, s.velocity.x);
            assertEquals(0, s.angle.z); assertEquals(0, s.velocity.z);
        }
        assertTrue(s.angle.y > .2F);
    }
    @Test void inwardVelocityIsRetainedAtBoundsAndReversalMovesImmediately() {
        for (float sign : new float[]{-1, 1}) {
            AngularMotion s = new AngularMotion();
            s.angle.x = sign * .2F;
            s.velocity.x = -sign;
            s.advance(new Vector3f(), 0, 0, 0, 6, 1,
                    new Vector3f(.2F, .3F, .4F), .001F, 1);
            assertEquals(-sign, s.velocity.x);
            assertTrue(Math.abs(s.angle.x) < .2F);
        }
    }
    @Test void omittedAxisLimitsRetainScalarSolverExactly() {
        AngularMotion legacy = new AngularMotion(), optional = new AngularMotion();
        for (int i = 0; i < 500; i++) {
            Vector3f target = new Vector3f(i % 2 == 0 ? 10 : -10, 5, -3);
            legacy.advance(target, 8, .9F, 1.5F, 6, .4F, 1F/60, 8);
            optional.advance(target, 8, .9F, 1.5F, 6, .4F, null, 1F/60, 8);
            assertEquals(legacy.angle, optional.angle);
            assertEquals(legacy.velocity, optional.velocity);
            assertTrue(legacy.angle.length() <= .40001F);
        }
    }
    @Test void quaternionSignDoesNotProduceFullTurnImpulse() {
        Quaternionf q=new Quaternionf().rotationY(.5F);
        assertEquals(0,AngularMotion.angularVelocity(q,new Quaternionf(q).mul(-1F),.016F).length(),1e-5);
    }
}
