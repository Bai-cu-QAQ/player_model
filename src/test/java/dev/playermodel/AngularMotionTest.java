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
    @Test void quaternionSignDoesNotProduceFullTurnImpulse() {
        Quaternionf q=new Quaternionf().rotationY(.5F);
        assertEquals(0,AngularMotion.angularVelocity(q,new Quaternionf(q).mul(-1F),.016F).length(),1e-5);
    }
}
