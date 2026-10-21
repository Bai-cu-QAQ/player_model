package dev.playermodel.runtime;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactSpringTest {
    @Test void convergesWithoutOvershootAndReleasesSmoothly() {
        ContactSpring s = new ContactSpring();
        Vector3f target = new Vector3f(0.5F, 0, 0);
        float previous = 0;
        for (int i=0;i<120;i++) {
            s.advance(target, 1F/120, 35);
            assertTrue(s.position.x >= previous - 1e-6F);
            assertTrue(s.position.x <= 0.50001F);
            previous = s.position.x;
        }
        assertEquals(0.5F, s.position.x, 1e-4F);
        s.advance(new Vector3f(), 1F/120, 14);
        assertTrue(s.position.x > 0.45F, "release must not snap to rest");
        for (int i=0;i<240;i++) s.advance(new Vector3f(), 1F/120,14);
        assertEquals(0F,s.position.x,1e-4F);
    }
    @Test void exactStepIsFrameRateIndependent() {
        ContactSpring a=new ContactSpring(), b=new ContactSpring();
        Vector3f t=new Vector3f(0.3F,-0.2F,0.1F);
        for(int i=0;i<30;i++) a.advance(t,1F/30,25);
        for(int i=0;i<144;i++) b.advance(t,1F/144,25);
        assertTrue(a.position.distance(b.position)<1e-5F);
        assertTrue(a.velocity.distance(b.velocity)<1e-5F);
    }
    @Test void regularizationPreventsJointExplosion() {
        assertTrue(ContactSpring.correction(new Vector3f(), new Vector3f(0,0,1),1).isFinite());
        Vector3f c=ContactSpring.correction(new Vector3f(0,2,0),new Vector3f(0,0,1),0.2F);
        assertTrue(c.x>0 && c.y==0 && c.z==0);
    }
    @Test void invalidTimeDoesNotCorruptState() {
        ContactSpring s=new ContactSpring();
        s.advance(new Vector3f(1,0,0),Float.NaN,35);
        assertEquals(0F,s.position.length());
    }
}
