package com.igormaznitsa.langtrainer.ui;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * JetBrains Mono NL faces from {@code /fonts/*.ttf}. Each constant is one registered physical weight.
 * The JVM runs enum initialization (constructor for every constant) the first time this type is used,
 * e.g. the first {@code LangTrainerFonts.MONO_NL_…} access when modules build their UI.
 */
public enum LangTrainerFonts {

  MONO_NL_REGULAR("/fonts/JetBrainsMonoNL-Regular.ttf"),
  MONO_NL_SEMI_BOLD("/fonts/JetBrainsMonoNL-SemiBold.ttf"),
  MONO_NL_BOLD("/fonts/JetBrainsMonoNL-Bold.ttf"),
  MONO_NL_EXTRA_BOLD("/fonts/JetBrainsMonoNL-ExtraBold.ttf");

  private final Font baseFont;

  LangTrainerFonts(final String classpath) {
    try {
      this.baseFont = LangTrainerFonts.loadAndRegister(classpath);
    } catch (final FontFormatException | IOException e) {
      throw new ExceptionInInitializerError(
          new IllegalStateException("Can't load JetBrains Mono NL: " + classpath, e));
    }
  }

  private static Font loadAndRegister(final String classpath)
      throws FontFormatException, IOException {
    final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    try (InputStream in = LangTrainerFonts.class.getResourceAsStream(classpath)) {
      Objects.requireNonNull(in, "Missing resource: " + classpath);
      final Font base = Font.createFont(Font.TRUETYPE_FONT, in);
      ge.registerFont(base);
      return base;
    }
  }

  public Font atPoints(final float sizePt) {
    return this.baseFont.deriveFont(sizePt);
  }
}
