package com.foukas.dropbox2d.progression;

import com.badlogic.gdx.graphics.Color;

/** Ball color tiers, unlocked by depth milestones. No sprite art in this
 * project (placeholder-shapes philosophy carried through from Approach A),
 * so a "skin" is a distinct ball color rather than a texture. Declared in
 * ascending threshold order so ordinal comparison doubles as tier ranking. */
public enum SkinTier {
    DEFAULT(0f, Color.ORANGE, "Default"),
    EMERALD(50f, new Color(0.2f, 0.8f, 0.4f, 1f), "Emerald"),
    AMETHYST(150f, new Color(0.6f, 0.3f, 0.8f, 1f), "Amethyst"),
    DIAMOND(300f, new Color(0.7f, 0.95f, 1f, 1f), "Diamond");

    private final float unlockDepth;
    private final Color color;
    private final String displayName;

    SkinTier(float unlockDepth, Color color, String displayName) {
        this.unlockDepth = unlockDepth;
        this.color = color;
        this.displayName = displayName;
    }

    public float getUnlockDepth() {
        return unlockDepth;
    }

    public Color getColor() {
        return color;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The highest tier unlocked by the given depth. DEFAULT (threshold 0)
     * guarantees this never returns null. */
    public static SkinTier forDepth(float depth) {
        SkinTier best = DEFAULT;
        for (SkinTier tier : values()) {
            if (depth >= tier.unlockDepth) {
                best = tier;
            }
        }
        return best;
    }
}
