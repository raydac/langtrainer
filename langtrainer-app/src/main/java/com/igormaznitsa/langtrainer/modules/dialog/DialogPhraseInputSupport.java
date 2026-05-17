package com.igormaznitsa.langtrainer.modules.dialog;

import com.igormaznitsa.langtrainer.text.PhraseWordSupport;
import com.igormaznitsa.langtrainer.text.TypingComparisonUtils;
import com.igormaznitsa.langtrainer.text.TypingPhraseFormatter;

/**
 * Dialog work-field policy: tips use expected spelling; hyphens may be omitted or over-typed;
 * history always shows canonical resource lines after a successful Enter.
 */
public final class DialogPhraseInputSupport {

  private DialogPhraseInputSupport() {
  }

  /**
   * Collapses extra typed dashes, then aligns letters to the expected template.
   */
  public static String normalizeWorkInput(final String raw, final String expected) {
    if (expected == null || expected.isEmpty()) {
      return raw == null ? "" : raw;
    }
    final String prepared =
        PhraseWordSupport.collapseRedundantTypedJoiners(
            raw == null ? "" : raw, expected);
    return TypingPhraseFormatter.mergeLettersIntoExpected(expected, prepared);
  }

  /**
   * Accepts answers that match when intra-word hyphens and other non-alphanumeric separators are
   * ignored ({@code northwest} matches {@code north-west}).
   */
  public static boolean isAnswerAccepted(final String entered, final String expected) {
    return TypingComparisonUtils.isCloseEnough(entered, expected);
  }
}
