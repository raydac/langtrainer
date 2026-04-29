package com.igormaznitsa.langtrainer.modules.flygame;

import java.util.Locale;

/**
 * Aligns typed letters to the expected phrase (auto spacing / punctuation from template) and builds
 * the sky overlay (underscores for unfilled letter slots, word gaps; punctuation is not drawn).
 */
final class FlyTypingSlotFormatter {

  /**
   * Inter-word gap in the sky overlay: two normal spaces so, with a monospaced font, the break is
   * exactly two columns (Unicode em/en spaces are not monospace and often render huge).
   */
  private static final String OVERLAY_INTER_WORD_GAP = "  ";

  private FlyTypingSlotFormatter() {
  }

  static String extractLetterDigits(final String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    final StringBuilder builder = new StringBuilder(text.length());
    text.codePoints().filter(Character::isLetterOrDigit).forEach(builder::appendCodePoint);
    return builder.toString();
  }

  static int countLetterDigitsBefore(final String text, final int caretIndex) {
    if (text == null || text.isEmpty() || caretIndex <= 0) {
      return 0;
    }
    final int end = Math.min(caretIndex, text.length());
    int count = 0;
    int i = 0;
    while (i < end) {
      final int cp = text.codePointAt(i);
      if (Character.isLetterOrDigit(cp)) {
        count++;
      }
      i += Character.charCount(cp);
    }
    return count;
  }

  /**
   * Document position after the {@code letterCount}-th letter/digit in {@code merged}, or end of
   * string.
   */
  static int caretAfterNthLetterSlot(final String merged, final int letterCount) {
    if (letterCount <= 0) {
      return 0;
    }
    int seen = 0;
    int i = 0;
    while (i < merged.length()) {
      final int cp = merged.codePointAt(i);
      final int len = Character.charCount(cp);
      if (Character.isLetterOrDigit(cp)) {
        seen++;
        if (seen == letterCount) {
          return i + len;
        }
      }
      i += len;
    }
    return merged.length();
  }

  /**
   * Maps typed letters/digits onto {@code expected}: inserts spaces and copies punctuation from the
   * template without requiring the user to type them.
   */
  static String mergeLettersIntoExpected(final String expected, final String rawInput) {
    if (expected == null || expected.isEmpty()) {
      return "";
    }
    final String letters = FlyTypingSlotFormatter.extractLetterDigits(rawInput);
    final StringBuilder out = new StringBuilder();
    int li = 0;
    int ei = 0;
    while (ei < expected.length()) {
      final int cpE = expected.codePointAt(ei);
      final int elen = Character.charCount(cpE);
      if (Character.isWhitespace(cpE)) {
        out.appendCodePoint(cpE);
        ei += elen;
        continue;
      }
      if (Character.isLetterOrDigit(cpE)) {
        if (li >= letters.length()) {
          break;
        }
        final int cpL = letters.codePointAt(li);
        final int llen = Character.charCount(cpL);
        out.appendCodePoint(FlyTypingSlotFormatter.alignLetterCase(cpL, cpE));
        li += llen;
        ei += elen;
        continue;
      }
      out.appendCodePoint(cpE);
      ei += elen;
    }
    return out.toString();
  }

  private static int alignLetterCase(final int typedLetter, final int expectedLetter) {
    if (Character.isUpperCase(expectedLetter)) {
      return Character.toUpperCase(typedLetter);
    }
    return Character.toLowerCase(typedLetter);
  }

  /**
   * Full-width overlay line: typed letters from {@code merged}, underscores for remaining letter
   * slots, two ASCII spaces where {@code expected} has whitespace (monospace column width);
   * punctuation and other non-alphanumeric signs are omitted from the display (still consumed from
   * {@code merged} to stay in sync). Uppercase for display.
   */
  static String overlayDisplayUpper(final String expected, final String merged) {
    if (expected == null || expected.isEmpty()) {
      return "";
    }
    final Locale loc = Locale.ROOT;
    final String exp = expected.toUpperCase(loc);
    final String mer = merged == null ? "" : merged.toUpperCase(loc);
    final StringBuilder display = new StringBuilder();
    int mi = 0;
    int ei = 0;
    while (ei < exp.length()) {
      final int cpE = exp.codePointAt(ei);
      final int elen = Character.charCount(cpE);
      if (Character.isWhitespace(cpE)) {
        display.append(FlyTypingSlotFormatter.OVERLAY_INTER_WORD_GAP);
        if (mi < mer.length()) {
          final int cpM = mer.codePointAt(mi);
          if (Character.isWhitespace(cpM)) {
            mi += Character.charCount(cpM);
          }
        }
        ei += elen;
        continue;
      }
      if (Character.isLetterOrDigit(cpE)) {
        if (mi < mer.length()) {
          final int cpM = mer.codePointAt(mi);
          final int mlen = Character.charCount(cpM);
          display.appendCodePoint(cpM);
          mi += mlen;
          ei += elen;
        } else {
          display.append('_');
          ei += elen;
        }
        continue;
      }
      if (mi < mer.length()) {
        final int cpM = mer.codePointAt(mi);
        final int mlen = Character.charCount(cpM);
        mi += mlen;
      }
      ei += elen;
    }
    return display.toString();
  }
}
