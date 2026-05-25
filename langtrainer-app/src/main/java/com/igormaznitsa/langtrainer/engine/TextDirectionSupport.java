package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.ui.LangTrainerFonts;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;

public final class TextDirectionSupport {

  private TextDirectionSupport() {
  }

  public static boolean isRightToLeft(final DialogDefinition definition, final boolean sideA) {
    return definition != null && isRightToLeft(sideA ? definition.langA() : definition.langB(),
        definition.rtl());
  }

  public static boolean isRightToLeft(final String language, final List<String> rtlLanguages) {
    if (language == null || language.isBlank() || rtlLanguages == null || rtlLanguages.isEmpty()) {
      return false;
    }
    final String normalized = language.strip();
    return rtlLanguages.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::strip)
        .anyMatch(normalized::equalsIgnoreCase);
  }

  public static void applyToTextComponent(final JTextComponent component, final boolean rtl) {
    component.setComponentOrientation(
        rtl ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
    if (component instanceof JTextField field) {
      field.setHorizontalAlignment(rtl ? SwingConstants.RIGHT : SwingConstants.LEFT);
    }
  }

  public static void applyToLabel(final JLabel label, final boolean rtl) {
    label.setComponentOrientation(
        rtl ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
    label.setHorizontalAlignment(rtl ? SwingConstants.RIGHT : SwingConstants.LEFT);
  }

  public static String bidiEmbedding(final String text, final boolean rtl) {
    final String value = text == null ? "" : text;
    return rtl ? "\u202B" + value + "\u202C" : value;
  }

  public static Font fontForDirection(
      final LangTrainerFonts leftToRightFont,
      final int rtlStyle,
      final boolean rtl,
      final float sizePt) {
    if (!rtl) {
      return leftToRightFont.atPoints(sizePt);
    }
    return LangTrainerFonts.SYSTEM_MONOSPACED.atPoints(sizePt).deriveFont(rtlStyle, sizePt);
  }
}
