package com.foukas.dropbox2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

/**
 * Loads the Press Start 2P display font (plan-eng-review Next Step 11) --
 * pixel-arcade feel fits the neon/synthwave direction, and (unlike the
 * default BitmapFont it replaces everywhere) reads as a deliberate visual
 * choice rather than a debug placeholder. Generated once at a fixed base
 * pixel size; runtime sizing still goes through BitmapFont.getData().setScale(...)
 * exactly like the default font already was (HudFontScale, per-element
 * multipliers) -- swapping the glyph source doesn't change how sizing
 * works anywhere else in the codebase.
 *
 * SIL Open Font License -- free to embed in an APK, no royalty, attribution
 * satisfied by keeping the font's own license file alongside the asset
 * (android/assets/PressStart2P-OFL.txt). No further licensing decision
 * needed (resolved in the design doc's Open Questions).
 */
final class HudFont {
    private static final String FONT_PATH = "PressStart2P-Regular.ttf";
    // Base glyph resolution the font is generated at; HudFontScale and
    // per-element multipliers (e.g. the depth number's larger scale) work
    // the same way on top of this as they did on the default font.
    private static final int BASE_PIXEL_SIZE = 24;

    static BitmapFont generate() {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(FONT_PATH));
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = BASE_PIXEL_SIZE;
        parameter.color = Color.WHITE;
        parameter.minFilter = Texture.TextureFilter.Linear;
        parameter.magFilter = Texture.TextureFilter.Linear;
        BitmapFont font = generator.generateFont(parameter);
        // The generator owns the loaded FreeType face/library; the
        // generated BitmapFont's texture is independent and already baked,
        // so the generator itself is safe to dispose immediately.
        generator.dispose();
        return font;
    }

    private HudFont() {
    }
}
