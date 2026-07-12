package com.saolghra.armor_hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmorHudMathTest {

    @Test
    void durabilityRatio_coversFullHalfAndBroken() {
        assertEquals(1.0f, ArmorHudMath.durabilityRatio(100, 0), 1e-6);   // pristine
        assertEquals(0.5f, ArmorHudMath.durabilityRatio(100, 50), 1e-6);  // half
        assertEquals(0.0f, ArmorHudMath.durabilityRatio(100, 100), 1e-6); // broken
    }

    @Test
    void durabilityRatio_clampsAndGuardsInvalidMax() {
        assertEquals(0.0f, ArmorHudMath.durabilityRatio(100, 150), 1e-6); // over-damaged clamps to 0
        assertEquals(0.0f, ArmorHudMath.durabilityRatio(0, 0), 1e-6);     // no max -> 0, no divide-by-zero
    }

    @Test
    void isLowDurability_onlyWhenDamagedAndBelowThreshold() {
        assertTrue(ArmorHudMath.isLowDurability(100, 90, 0.20f));  // 10% remaining < 20%
        assertFalse(ArmorHudMath.isLowDurability(100, 50, 0.20f)); // 50% remaining >= 20%
        assertFalse(ArmorHudMath.isLowDurability(100, 0, 0.20f));  // undamaged never warns
        assertFalse(ArmorHudMath.isLowDurability(0, 0, 0.20f));    // no durability never warns
    }

    @Test
    void durabilityColor_gradientEndpointsAndMidpoint() {
        assertEquals(0xFFFF0000, ArmorHudMath.durabilityColor(0.0f)); // red at empty
        assertEquals(0xFFFFFF00, ArmorHudMath.durabilityColor(0.5f)); // yellow at half
        assertEquals(0xFF00FF00, ArmorHudMath.durabilityColor(1.0f)); // green at full
    }

    @Test
    void durabilityColor_isAlwaysFullyOpaque() {
        for (float r = 0f; r <= 1f; r += 0.1f) {
            assertEquals(0xFF, (ArmorHudMath.durabilityColor(r) >>> 24) & 0xFF);
        }
    }
}
