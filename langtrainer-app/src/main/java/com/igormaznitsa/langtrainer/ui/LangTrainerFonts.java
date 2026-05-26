package com.igormaznitsa.langtrainer.ui;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Font palette for module UIs. Bundled JetBrains Mono NL faces are used when present, otherwise the
 * JVM monospaced font keeps the application self-contained.
 */
public enum LangTrainerFonts {

  SYSTEM_MONOSPACED(null, Font.PLAIN),
  MONO_NL_REGULAR("/fonts/JetBrainsMonoNL-Regular.ttf", Font.PLAIN),
  MONO_NL_SEMI_BOLD("/fonts/JetBrainsMonoNL-SemiBold.ttf", Font.BOLD),
  MONO_NL_BOLD("/fonts/JetBrainsMonoNL-Bold.ttf", Font.BOLD),
  MONO_NL_EXTRA_BOLD("/fonts/JetBrainsMonoNL-ExtraBold.ttf", Font.BOLD);

  private static final Logger LOG = Logger.getLogger(LangTrainerFonts.class.getName());

  private final Font baseFont;

  LangTrainerFonts(final String classpath, final int fallbackStyle) {
    this.baseFont = classpath == null
        ? fallbackFont(fallbackStyle)
        : loadAndRegister(classpath).orElseGet(() -> fallbackFont(fallbackStyle));
  }

  private static Optional<Font> loadAndRegister(final String classpath) {
    final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    try (InputStream in = LangTrainerFonts.class.getResourceAsStream(classpath)) {
      if (in == null) {
        LOG.warning("Bundled font resource is missing, using system font: " + classpath);
        return Optional.empty();
      }
      final Font base = Font.createFont(Font.TRUETYPE_FONT, in);
      ge.registerFont(base);
      return Optional.of(base);
    } catch (final FontFormatException | IOException ex) {
      LOG.log(Level.WARNING, "Can't load bundled font, using system font: " + classpath, ex);
      return Optional.empty();
    }
  }

  private static Font fallbackFont(final int style) {
    return new Font(Font.MONOSPACED, style, 12);
  }

  public Font atPoints(final float sizePt) {
    return this.baseFont.deriveFont(sizePt);
  }
}
