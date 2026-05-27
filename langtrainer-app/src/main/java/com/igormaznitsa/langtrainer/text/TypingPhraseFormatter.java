package com.igormaznitsa.langtrainer.text;

import java.util.Locale;

/**
 * Aligns typed letters to an expected phrase template (auto spacing, punctuation, intra-word hyphens)
 * and builds fly-game sky overlay lines.
 */
public final class TypingPhraseFormatter {

  private static final String OVERLAY_INTER_WORD_GAP = "  ";

  private TypingPhraseFormatter() {
  }

  public static String extractLetterDigits(final String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    final StringBuilder builder = new StringBuilder(text.length());
    text.codePoints().filter(Character::isLetterOrDigit).forEach(builder::appendCodePoint);
    return builder.toString();
  }

  public static int countLetterDigitsBefore(final String text, final int caretIndex) {
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
   * Caret index after the {@code letterCount}-th letter/digit, then past any immediately following
   * auto-inserted template characters (hyphens, punctuation) so the caret does not sit before them.
   */
  public static int caretAfterNthLetterSlot(final String merged, final int letterCount) {
    return skipAutoInsertedAfter(
        TypingPhraseFormatter.caretAfterNthLetterSlotRaw(merged, letterCount),
        merged);
  }

  private static int caretAfterNthLetterSlotRaw(final String merged, final int letterCount) {
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

  private static int skipAutoInsertedAfter(final int from, final String merged) {
    int pos = from;
    while (pos < merged.length()) {
      final int cp = merged.codePointAt(pos);
      if (!PhraseWordSupport.isAutoInsertedInExpected(cp)) {
        break;
      }
      pos += Character.charCount(cp);
    }
    return pos;
  }

  /**
   * Maps typed letters/digits onto {@code expected}: copies whitespace, punctuation, and intra-word
   * joiners from the template without requiring the user to type them.
   */
  public static String mergeLettersIntoExpected(final String expected, final String rawInput) {
    if (expected == null || expected.isEmpty()) {
      return "";
    }
    final String letters = TypingPhraseFormatter.extractLetterDigits(rawInput);
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
        out.appendCodePoint(TypingPhraseFormatter.alignLetterCase(cpL, cpE));
        li += llen;
        ei += elen;
        continue;
      }
      if (li >= letters.length() && !PhraseWordSupport.isIntraWordJoiner(cpE)) {
        break;
      }
      out.appendCodePoint(cpE);
      ei += elen;
    }
    return out.toString();
  }

  public static String mergeLettersIntoExpectedKeepingExtraInput(
      final String expected,
      final String rawInput) {
    if (expected == null || expected.isEmpty()) {
      return rawInput == null ? "" : rawInput;
    }
    final String raw = rawInput == null ? "" : rawInput;
    final String letters = TypingPhraseFormatter.extractLetterDigits(raw);
    final String expectedLetters = TypingPhraseFormatter.extractLetterDigits(expected);
    final String merged = TypingPhraseFormatter.mergeLettersIntoExpected(expected, raw);
    final int expectedLetterCount = TypingPhraseFormatter.codePointCount(expectedLetters);
    if (TypingPhraseFormatter.codePointCount(letters) <= expectedLetterCount) {
      return merged;
    }
    return merged
        + raw.substring(TypingPhraseFormatter.indexAfterLetterDigits(raw, expectedLetterCount));
  }

  private static int codePointCount(final String text) {
    return text.codePointCount(0, text.length());
  }

  private static int indexAfterLetterDigits(final String text, final int count) {
    int index = 0;
    int seen = 0;
    while (seen < count && index < text.length()) {
      final int codePoint = text.codePointAt(index);
      index += Character.charCount(codePoint);
      if (Character.isLetterOrDigit(codePoint)) {
        seen++;
      }
    }
    return index;
  }

  private static int alignLetterCase(final int typedLetter, final int expectedLetter) {
    if (Character.isUpperCase(expectedLetter)) {
      return Character.toUpperCase(typedLetter);
    }
    return Character.toLowerCase(typedLetter);
  }

  /**
   * Overlay line: typed letters, underscores for remaining slots, visible intra-word joiners,
   * two-column word gaps; other punctuation omitted from display.
   */
  public static String overlayDisplayUpper(final String expected, final String merged) {
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
        display.append(TypingPhraseFormatter.OVERLAY_INTER_WORD_GAP);
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
        } else {
          display.append('_');
        }
        ei += elen;
        continue;
      }
      if (PhraseWordSupport.isIntraWordJoiner(cpE)) {
        display.appendCodePoint(cpE);
        if (mi < mer.length()) {
          final int cpM = mer.codePointAt(mi);
          if (cpM == cpE) {
            mi += Character.charCount(cpM);
          }
        }
        ei += elen;
        continue;
      }
      if (mi < mer.length()) {
        final int cpM = mer.codePointAt(mi);
        mi += Character.charCount(cpM);
      }
      ei += elen;
    }
    return display.toString();
  }
}
