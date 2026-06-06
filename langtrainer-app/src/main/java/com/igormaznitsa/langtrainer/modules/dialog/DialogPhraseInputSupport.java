package com.igormaznitsa.langtrainer.modules.dialog;

import com.igormaznitsa.langtrainer.text.TypingComparisonUtils;

/**
 * Dialog work-field policy: users type naturally; mapped letters may be corrected while typing;
 * history always shows canonical resource lines after a successful Enter.
 */
public final class DialogPhraseInputSupport {

  private static final int MAX_ALLOWED_TYPOS = 4;

  private DialogPhraseInputSupport() {
  }

  /**
   * Accepts answers that match when intra-word hyphens and other non-alphanumeric separators are
   * ignored ({@code northwest} matches {@code north-west}); a small number of real typing mistakes
   * is allowed on Enter.
   */
  public static boolean isAnswerAccepted(final String entered, final String expected) {
    final String normalizedExpected = TypingComparisonUtils.normalizeForTypingMatch(expected);
    final int expectedLength = normalizedExpected.codePointCount(0, normalizedExpected.length());
    return TypingComparisonUtils.editDistance(entered, expected)
        <= DialogPhraseInputSupport.allowedTypoCount(expectedLength);
  }

  private static int allowedTypoCount(final int expectedLength) {
    if (expectedLength <= 4) {
      return 0;
    }
    return Math.min(MAX_ALLOWED_TYPOS, Math.max(1, expectedLength / 10));
  }
}
