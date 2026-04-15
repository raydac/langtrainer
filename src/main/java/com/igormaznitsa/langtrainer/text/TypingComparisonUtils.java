package com.igormaznitsa.langtrainer.text;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * Shared normalization and fuzzy equality for typed answers (dialog and fly game).
 */
public final class TypingComparisonUtils {

  private static final double DEFAULT_CLOSE_ENOUGH = 0.99d;
  private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();

  private TypingComparisonUtils() {
  }

  /**
   * Folds Russian yo, lowercases, keeps letters and digits only — comparable to prior module logic.
   */
  public static String normalizeForTypingMatch(final String text) {
    if (text == null) {
      return "";
    }
    final String withYeFolded = text
        .replace('Ё', 'Е')
        .replace('ё', 'е');
    final String lower = withYeFolded.toLowerCase();
    final StringBuilder builder = new StringBuilder(lower.length());
    lower.codePoints()
        .filter(Character::isLetterOrDigit)
        .forEach(builder::appendCodePoint);
    return builder.toString();
  }

  /**
   * Normalized similarity in {@code [0,1]} from Levenshtein distance vs max length (same rule as
   * before: {@code 1 - distance / max(len)}).
   */
  public static double similarity(final String typed, final String expected) {
    final String left = normalizeForTypingMatch(typed);
    final String right = normalizeForTypingMatch(expected);
    final int maxLen = Math.max(left.length(), right.length());
    if (maxLen == 0) {
      return 1.0;
    }
    final Integer distance = LEVENSHTEIN.apply(left, right);
    final int d = distance == null ? maxLen : distance;
    return 1.0d - (double) d / (double) maxLen;
  }

  /**
   * Levenshtein distance between normalized forms; used for UI that scales with edit distance.
   */
  public static int editDistance(final String typed, final String expected) {
    final String left = normalizeForTypingMatch(typed);
    final String right = normalizeForTypingMatch(expected);
    final Integer distance = LEVENSHTEIN.apply(left, right);
    final int maxLen = Math.max(left.length(), right.length());
    return distance == null ? maxLen : distance;
  }

  public static boolean isCloseEnough(final String actual, final String expected) {
    return similarity(actual, expected) >= DEFAULT_CLOSE_ENOUGH;
  }

  /**
   * HTML-escape for short snippets embedded in Swing HTML labels; newlines become {@code <br/>}.
   */
  public static String escapeHtmlForBanner(final String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return StringEscapeUtils.escapeHtml4(text).replace("\n", "<br/>");
  }
}
