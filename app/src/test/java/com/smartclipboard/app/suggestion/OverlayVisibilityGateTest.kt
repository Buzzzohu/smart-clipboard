package com.smartclipboard.app.suggestion

import org.junit.Assert.*
import org.junit.Test

class OverlayVisibilityGateTest {
    @Test fun reopeningWaitsForFinalPositionAndClosingHidesOnFirstMovement() {
        val gate = OverlayVisibilityGate()
        assertFalse(gate.ready(2200, 0))
        assertFalse(gate.ready(1800, 40))
        assertFalse(gate.ready(1482, 80))
        assertFalse(gate.ready(1482, 200))
        assertTrue(gate.ready(1482, 240))
        assertTrue(gate.ready(1482, 280))
        assertFalse(gate.ready(1700, 300))
        assertFalse(gate.ready(2200, 340))
    }

    @Test fun newPanelCannotReusePreviousSessionStability() {
        val old = OverlayVisibilityGate()
        old.ready(1482, 0)
        assertTrue(old.ready(1482, 200))
        assertFalse(OverlayVisibilityGate().ready(1482, 220))
    }
}
