package dev.playermodel;

import dev.playermodel.util.SecondaryForces;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecondaryForcesTest {
    @Test void verticalAlignedLeverDoesNotInventHorizontalSwing() {
        Vector3f result = SecondaryForces.movement(new Vector3f(0,4,0),
                new Vector3f(0,8,0), new Vector3f(0,-12,0),1,1,1,1);
        assertEquals(0, result.length(), 1e-6);
    }
    @Test void tiltedLeverReactsToVerticalMovementAndReversesOnAscent() {
        Vector3f lever = new Vector3f(1,4,2);
        Vector3f down = SecondaryForces.movement(lever,new Vector3f(0,8,0),new Vector3f(),1,0,1,1);
        Vector3f up = SecondaryForces.movement(lever,new Vector3f(0,-8,0),new Vector3f(),1,0,1,1);
        assertTrue(down.length()>0);
        assertEquals(0,new Vector3f(down).add(up).length(),1e-6);
        assertEquals(0,down.y,1e-6);
        assertEquals(0,lever.distance(new Vector3f(1,4,2)),1e-6);
    }
    @Test void movementTorqueMustNotOverwriteGravityLever() {
        Vector3f lever = new Vector3f(1,3,2);
        SecondaryForces.movement(lever,new Vector3f(2,5,-3),new Vector3f(),1,0,1,1);
        Vector3f gravity = SecondaryForces.torque(lever,new Vector3f(0,1,0));
        assertEquals(0,gravity.distance(new Vector3f(lever).cross(new Vector3f(0,1,0)).div(lever.length())),1e-6);
        assertEquals(0,lever.distance(new Vector3f(1,3,2)),1e-6);
    }
    @Test void verticalStrengthZeroOnlyRemovesVerticalContribution() {
        Vector3f lever=new Vector3f(1,2,3), speed=new Vector3f(2,8,4);
        Vector3f noVertical=SecondaryForces.movement(lever,speed,new Vector3f(),1,0,0,1);
        Vector3f horizontal=SecondaryForces.movement(lever,new Vector3f(2,0,4),new Vector3f(),1,0,1,1);
        assertEquals(0,noVertical.distance(horizontal),1e-6);
    }
    @Test void viewYawAndPitchHaveIndependentGains() {
        Vector3f force=SecondaryForces.rotation(new Vector3f(2,3,0),new Vector3f(),1,0,2,.5F);
        assertEquals(-1,force.x,1e-6); assertEquals(-6,force.y,1e-6);
    }
    @Test void stoppingViewProducesOppositeInertiaFromAngularDeceleration() {
        Vector3f force=SecondaryForces.rotation(new Vector3f(),new Vector3f(-10,0,0),1,.1F,1,1);
        assertEquals(1,force.x,1e-6);
    }
}
