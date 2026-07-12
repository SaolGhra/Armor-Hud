package com.saolghra.armor_hud;

/**
 * Pure, loader- and version-independent maths for the armor HUD (durability ratios, the
 * low-durability check, and the durability→colour gradient). Kept free of any Minecraft types so it
 * can be unit-tested directly with JUnit — see {@code ArmorHudMathTest}.
 */
public final class ArmorHudMath {
    private ArmorHudMath() {}

    /** Remaining-durability fraction (0 = broken, 1 = pristine), clamped to [0, 1]. */
    public static float durabilityRatio(int maxDamage, int damage) {
        if (maxDamage <= 0) {
            return 0f;
        }
        float ratio = (maxDamage - damage) / (float) maxDamage;
        return Math.max(0f, Math.min(1f, ratio));
    }

    /**
     * Whether an item should show the low-durability warning: it has taken damage and its remaining
     * fraction is below {@code threshold}.
     */
    public static boolean isLowDurability(int maxDamage, int damage, float threshold) {
        return maxDamage > 0 && damage > 0 && durabilityRatio(maxDamage, damage) < threshold;
    }

    /** Fully-opaque ARGB colour for a durability ratio: red (0) → yellow (0.5) → green (1). */
    public static int durabilityColor(float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        return hsvToArgb(clamped * 120f, 1f, 1f);
    }

    /** Converts an HSV colour (h in degrees, s/v in [0,1]) to a fully-opaque ARGB int. */
    public static int hsvToArgb(float h, float s, float v) {
        h = (h % 360 + 360) % 360;
        float hh = h / 60.0f;
        int i = (int) hh % 6;
        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        int r = 0, g = 0, b = 0;
        switch (i) {
            case 0 -> { r = Math.round(v * 255); g = Math.round(t * 255); b = Math.round(p * 255); }
            case 1 -> { r = Math.round(q * 255); g = Math.round(v * 255); b = Math.round(p * 255); }
            case 2 -> { r = Math.round(p * 255); g = Math.round(v * 255); b = Math.round(t * 255); }
            case 3 -> { r = Math.round(p * 255); g = Math.round(q * 255); b = Math.round(v * 255); }
            case 4 -> { r = Math.round(t * 255); g = Math.round(p * 255); b = Math.round(v * 255); }
            case 5 -> { r = Math.round(v * 255); g = Math.round(p * 255); b = Math.round(q * 255); }
        }
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
