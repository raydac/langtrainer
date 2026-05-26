package com.igormaznitsa.langtrainer.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Phrase tokenization and typing-tip helpers. Words are whitespace-delimited tokens;
 * hyphens inside a token (e.g. {@code north-west}) are intra-word joiners, not word boundaries.
 */
public final class PhraseWordSupport {

  private PhraseWordSupport() {
  }

  public static boolean isIntraWordJoiner(final int codePoint) {
    return codePoint == '-' || codePoint == '‐' || codePoint == '‑';
  }

  public static boolean isAutoInsertedInExpected(final int codePoint) {
    return isIntraWordJoiner(codePoint)
        || codePoint == ','
        || codePoint == ';'
        || codePoint == ':'
        || codePoint == '.'
        || codePoint == '!'
        || codePoint == '?'
        || codePoint == '…';
  }

  /**
   * Parts of a single token split at intra-word joiners ({@code north-west} → {@code north}, {@code west}).
   */
  public static List<String> hyphenSeparatedParts(final String word) {
    if (word == null || word.isEmpty()) {
      return List.of();
    }
    final List<String> parts = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    for (int i = 0; i < word.length(); ) {
      final int cp = word.codePointAt(i);
      final int len = Character.charCount(cp);
      if (isIntraWordJoiner(cp)) {
        if (!current.isEmpty()) {
          parts.add(current.toString());
          current.setLength(0);
        }
      } else {
        current.appendCodePoint(cp);
      }
      i += len;
    }
    if (!current.isEmpty()) {
      parts.add(current.toString());
    }
    return List.copyOf(parts);
  }

  /**
   * True when {@code word} equals one hyphen-delimited segment of another entry (e.g. {@code SOUTH}
   * for {@code SOUTH-WEST}).
   */
  public static boolean isHyphenCompoundPart(final String word, final Iterable<String> allWords) {
    if (word == null || word.isEmpty()) {
      return false;
    }
    for (final String candidate : allWords) {
      if (word.equals(candidate)) {
        continue;
      }
      final List<String> parts = hyphenSeparatedParts(candidate);
      if (parts.size() < 2) {
        continue;
      }
      for (final String part : parts) {
        if (word.equals(part)) {
          return true;
        }
      }
    }
    return false;
  }

  public static List<String> splitWords(final String line) {
    if (line == null || line.isBlank()) {
      return List.of();
    }
    final String[] parts = line.strip().split("\\s+");
    final List<String> words = new ArrayList<>();
    for (final String part : parts) {
      if (!part.isEmpty()) {
        words.add(part);
      }
    }
    return List.copyOf(words);
  }

  /**
   * Within each whitespace-delimited word, collapses runs of typed joiners to a single joiner when
   * the expected word has one joiner at that position; drops typed joiners not present in expected.
   */
  public static String collapseRedundantTypedJoiners(final String raw, final String expected) {
    if (raw == null || raw.isEmpty()) {
      return raw == null ? "" : raw;
    }
    if (expected == null || expected.isEmpty()) {
      return raw;
    }
    final StringBuilder result = new StringBuilder(raw.length());
    int ei = 0;
    int ej = 0;
    final int rawLen = raw.length();
    final int expLen = expected.length();
    while (true) {
      while (ei < rawLen && Character.isWhitespace(raw.charAt(ei))) {
        result.append(raw.charAt(ei++));
      }
      while (ej < expLen && Character.isWhitespace(expected.charAt(ej))) {
        ej++;
      }
      if (ej >= expLen) {
        if (ei < rawLen) {
          result.append(raw.substring(ei));
        }
        break;
      }
      if (ei >= rawLen) {
        break;
      }
      final int wordStartExpected = ej;
      while (ej < expLen && !Character.isWhitespace(expected.charAt(ej))) {
        ej++;
      }
      final String expectedWord = expected.substring(wordStartExpected, ej);
      final int wordStartEntered = ei;
      while (ei < rawLen && !Character.isWhitespace(raw.charAt(ei))) {
        ei++;
      }
      final String enteredWord = raw.substring(wordStartEntered, ei);
      result.append(collapseRedundantJoinersInWord(enteredWord, expectedWord));
    }
    return result.toString();
  }

  private static String collapseRedundantJoinersInWord(
      final String entered, final String expected) {
    final StringBuilder out = new StringBuilder(entered.length());
    int ei = 0;
    int ej = 0;
    while (ei < entered.length() || ej < expected.length()) {
      if (ej < expected.length() && isIntraWordJoiner(expected.codePointAt(ej))) {
        final int expCp = expected.codePointAt(ej);
        final int expLen = Character.charCount(expCp);
        ej += expLen;
        if (ei < entered.length() && isIntraWordJoiner(entered.codePointAt(ei))) {
          out.appendCodePoint(expCp);
          while (ei < entered.length() && isIntraWordJoiner(entered.codePointAt(ei))) {
            ei += Character.charCount(entered.codePointAt(ei));
          }
        }
        continue;
      }
      if (ei < entered.length() && isIntraWordJoiner(entered.codePointAt(ei))) {
        if (ej < expected.length() && Character.isLetter(expected.codePointAt(ej))) {
          while (ei < entered.length() && isIntraWordJoiner(entered.codePointAt(ei))) {
            ei += Character.charCount(entered.codePointAt(ei));
          }
          continue;
        }
      }
      if (ei < entered.length() && ej < expected.length()) {
        out.appendCodePoint(entered.codePointAt(ei));
        ei += Character.charCount(entered.codePointAt(ei));
        ej += Character.charCount(expected.codePointAt(ej));
        continue;
      }
      if (ei < entered.length()) {
        out.append(entered.substring(ei));
      }
      break;
    }
    return out.toString();
  }

  public static boolean hasLettersFrom(final String text, final int from) {
    int i = Math.max(0, from);
    while (i < text.length()) {
      if (Character.isLetter(text.codePointAt(i))) {
        return true;
      }
      i += Character.charCount(text.codePointAt(i));
    }
    return false;
  }

  public static String computeTypingTip(final String entered, final String expected) {
    if (expected == null || expected.isEmpty()) {
      return "";
    }
    int ei = 0;
    int ej = 0;
    final int enteredLen = entered.length();
    final int expectedLen = expected.length();
    while (true) {
      while (ei < enteredLen && Character.isWhitespace(entered.charAt(ei))) {
        ei++;
      }
      while (ej < expectedLen && Character.isWhitespace(expected.charAt(ej))) {
        ej++;
      }
      if (ej >= expectedLen) {
        return "";
      }
      final int wordStartExpected = ej;
      while (ej < expectedLen && !Character.isWhitespace(expected.charAt(ej))) {
        ej++;
      }
      final String expectedWord = expected.substring(wordStartExpected, ej);
      if (expectedWord.isEmpty()) {
        continue;
      }
      final int wordStartEntered = ei;
      while (ei < enteredLen && !Character.isWhitespace(entered.charAt(ei))) {
        ei++;
      }
      final String enteredWord = entered.substring(wordStartEntered, ei);
      final int matchedInWord = matchWordPrefixForTip(enteredWord, expectedWord);
      if (matchedInWord < expectedWord.length()) {
        if (!hasLettersFrom(expectedWord, matchedInWord)) {
          continue;
        }
        return formatWordTipWithLetterDots(expectedWord, matchedInWord);
      }
    }
  }

  private static int matchWordPrefixForTip(final String enteredWord, final String expectedWord) {
    int eiW = 0;
    int ejW = 0;
    while (eiW < enteredWord.length() || ejW < expectedWord.length()) {
      while (ejW < expectedWord.length()
          && isIntraWordJoiner(expectedWord.codePointAt(ejW))) {
        final int joinerLen = Character.charCount(expectedWord.codePointAt(ejW));
        if (eiW < enteredWord.length()
            && isIntraWordJoiner(enteredWord.codePointAt(eiW))) {
          while (eiW < enteredWord.length()
              && isIntraWordJoiner(enteredWord.codePointAt(eiW))) {
            eiW += Character.charCount(enteredWord.codePointAt(eiW));
          }
          ejW += joinerLen;
          continue;
        }
        ejW += joinerLen;
      }
      if (ejW >= expectedWord.length()) {
        break;
      }
      if (eiW >= enteredWord.length()) {
        break;
      }
      final int cpa = enteredWord.codePointAt(eiW);
      final int cpb = expectedWord.codePointAt(ejW);
      if (!codePointsMatchForTip(cpa, cpb)) {
        break;
      }
      eiW += Character.charCount(cpa);
      ejW += Character.charCount(cpb);
    }
    return ejW;
  }

  private static boolean codePointsMatchForTip(final int typed, final int expected) {
    if (typed == expected) {
      return true;
    }
    if (Character.isLetter(typed) && Character.isLetter(expected)) {
      return Character.toLowerCase(typed) == Character.toLowerCase(expected);
    }
    return false;
  }

  public static String formatWordTipWithLetterDots(final String word, final int correctPrefixLen) {
    if (word.isEmpty() || correctPrefixLen > word.length()) {
      return "";
    }
    if (correctPrefixLen == word.length()) {
      return "";
    }
    final int lastLetterStart = indexOfLastLetterStart(word);
    if (lastLetterStart < 0) {
      if (correctPrefixLen == 0) {
        final int dots = Math.max(0, word.length() - 2);
        return word.charAt(0) + ".".repeat(dots) + word.charAt(word.length() - 1);
      }
      return word.substring(correctPrefixLen);
    }
    final String lastLetter = substringOneCodePoint(word, lastLetterStart);
    if (lastLetterStart == 0) {
      return lastLetter;
    }
    if (correctPrefixLen > lastLetterStart) {
      final int dots = word.length() - correctPrefixLen - 1;
      return word.substring(0, correctPrefixLen) + ".".repeat(dots)
          + word.charAt(word.length() - 1);
    }
    if (correctPrefixLen == 0) {
      return word.charAt(0) + maskMiddlePreservingJoiners(word, 1, lastLetterStart)
          + lastLetter;
    }
    return word.substring(0, correctPrefixLen)
        + maskMiddlePreservingJoiners(word, correctPrefixLen, lastLetterStart)
        + lastLetter;
  }

  private static String maskMiddlePreservingJoiners(
      final String word, final int from, final int lastLetterStart) {
    if (from >= lastLetterStart) {
      return "";
    }
    final StringBuilder middle = new StringBuilder();
    int i = from;
    while (i < lastLetterStart) {
      final int cp = word.codePointAt(i);
      final int len = Character.charCount(cp);
      if (isIntraWordJoiner(cp) || !Character.isLetter(cp)) {
        middle.appendCodePoint(cp);
      } else {
        middle.append('.');
      }
      i += len;
    }
    return middle.toString();
  }

  private static int indexOfLastLetterStart(final String word) {
    int last = -1;
    for (int i = 0; i < word.length(); ) {
      final int cp = word.codePointAt(i);
      if (Character.isLetter(cp)) {
        last = i;
      }
      i += Character.charCount(cp);
    }
    return last;
  }

  private static String substringOneCodePoint(final String word, final int start) {
    final int cp = word.codePointAt(start);
    return new String(Character.toChars(cp));
  }
}
