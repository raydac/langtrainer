package com.igormaznitsa.langtrainer.modules.flygame;

import com.igormaznitsa.langtrainer.text.TypingPhraseFormatter;

/**
 * Delegates to {@link TypingPhraseFormatter} (fly-game package entry point).
 */
final class FlyTypingSlotFormatter {

  private FlyTypingSlotFormatter() {
  }

  static String extractLetterDigits(final String text) {
    return TypingPhraseFormatter.extractLetterDigits(text);
  }

  static int countLetterDigitsBefore(final String text, final int caretIndex) {
    return TypingPhraseFormatter.countLetterDigitsBefore(text, caretIndex);
  }

  static int caretAfterNthLetterSlot(final String merged, final int letterCount) {
    return TypingPhraseFormatter.caretAfterNthLetterSlot(merged, letterCount);
  }

  static String mergeLettersIntoExpected(final String expected, final String rawInput) {
    return TypingPhraseFormatter.mergeLettersIntoExpected(expected, rawInput);
  }

  static String overlayDisplayUpper(final String expected, final String merged) {
    return TypingPhraseFormatter.overlayDisplayUpper(expected, merged);
  }
}
