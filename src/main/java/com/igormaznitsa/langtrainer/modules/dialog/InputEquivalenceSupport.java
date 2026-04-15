package com.igormaznitsa.langtrainer.modules.dialog;

import java.util.List;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Shared logic for {@code inputEqu} rules: map typed characters to expected positions and replace
 * known alternative spellings with the canonical expected substring.
 */
public final class InputEquivalenceSupport {

  private InputEquivalenceSupport() {
  }

  public static boolean codePointsMatchForTip(final int typed, final int expected) {
    if (typed == expected) {
      return true;
    }
    if (Character.isLetter(typed) && Character.isLetter(expected)) {
      return Character.toLowerCase(typed) == Character.toLowerCase(expected);
    }
    return false;
  }

  public static boolean isSkippableSeparatorInExpected(final int cp) {
    return cp == ',' || cp == ';' || cp == ':' || cp == '.' || cp == '!' || cp == '?' || cp == '…';
  }

  /**
   * Maps a character index in the user's text to the corresponding index in the expected line.
   * Handles omitted punctuation and letter case so typed text lines up with the expected string.
   */
  public static int expectedOffsetForDocumentIndex(
      final String doc, final String expected, final int docIndex) {
    int i = 0;
    int j = 0;
    final int target = Math.min(docIndex, doc.length());
    while (i < target && j < expected.length()) {
      final int cpd = doc.codePointAt(i);
      final int cpe = expected.codePointAt(j);
      final int di = Character.charCount(cpd);
      final int ej = Character.charCount(cpe);
      if (codePointsMatchForTip(cpd, cpe)) {
        i += di;
        j += ej;
        continue;
      }
      if (isSkippableSeparatorInExpected(cpe)) {
        final int afterSep = j + ej;
        if (afterSep < expected.length()) {
          final int cpNext = expected.codePointAt(afterSep);
          if (codePointsMatchForTip(cpd, cpNext)) {
            j = afterSep + Character.charCount(cpNext);
            i += di;
            continue;
          }
        }
        if (Character.isWhitespace(cpd)) {
          j += ej;
          continue;
        }
        i += di;
        j += ej;
        continue;
      }
      i += di;
      j += ej;
    }
    return j;
  }

  /**
   * True if {@code a} equals {@code b}, or both are a single letter and match ignoring case
   * (covers ASCII {@code a}/{@code A} after filters vs keys in JSON).
   */
  private static boolean typedKeyMatches(final String typed, final String key) {
    if (typed.equals(key)) {
      return true;
    }
    if (typed.length() != 1 || key.length() != 1) {
      return false;
    }
    final int t = typed.codePointAt(0);
    final int k = key.codePointAt(0);
    return Character.isLetter(t) && Character.isLetter(k)
        && Character.toLowerCase(t) == Character.toLowerCase(k);
  }

  /**
   * True if the rule value matches the expected slot (exact, or same letter ignoring case for
   * single code points so {@code A}↔{@code Ä} rows still apply when the answer has {@code ä}).
   */
  private static boolean valueMatchesExpectedSlot(final String value, final String expectedChar) {
    if (value.equals(expectedChar)) {
      return true;
    }
    if (value.length() != 1 || expectedChar.length() != 1) {
      return false;
    }
    final int v = value.codePointAt(0);
    final int e = expectedChar.codePointAt(0);
    return Character.isLetter(v) && Character.isLetter(e)
        && Character.toLowerCase(v) == Character.toLowerCase(e);
  }

  private static String alignMappedLetterCaseToExpected(
      final String mappedOneChar, final String expectedOneChar) {
    if (mappedOneChar.length() != 1 || expectedOneChar.length() != 1) {
      return mappedOneChar;
    }
    final int m = mappedOneChar.codePointAt(0);
    final int e = expectedOneChar.codePointAt(0);
    if (!Character.isLetter(m) || !Character.isLetter(e)) {
      return mappedOneChar;
    }
    if (Character.isUpperCase(e)) {
      return new String(Character.toChars(Character.toUpperCase(m)));
    }
    return new String(Character.toChars(Character.toLowerCase(m)));
  }

  public static String matchInputEquivalence(
      final String typed,
      final String expectedChar,
      final List<InputEquivalenceRow> rules) {
    for (final InputEquivalenceRow row : rules) {
      final List<String> keys = row.key();
      final List<String> vals = row.value();
      for (int i = 0; i < keys.size(); i++) {
        if (!typedKeyMatches(typed, keys.get(i))) {
          continue;
        }
        if (!valueMatchesExpectedSlot(vals.get(i), expectedChar)) {
          continue;
        }
        return alignMappedLetterCaseToExpected(vals.get(i), expectedChar);
      }
    }
    return null;
  }

  /**
   * After {@code insertLen} characters were inserted at {@code start}, replace any segments that
   * match {@code inputEqu} rules for the corresponding expected substring.
   */
  public static void applyAfterInsert(
      final JTextComponent area,
      final String expectedFull,
      final List<InputEquivalenceRow> rules,
      final int start,
      final int insertLen) {
    if (rules.isEmpty() || insertLen <= 0 || expectedFull == null) {
      return;
    }
    int p = start;
    int end = start + insertLen;
    while (p < end) {
      final String doc = area.getText();
      if (p >= doc.length()) {
        break;
      }
      final int cp = doc.codePointAt(p);
      final int chLen = Character.charCount(cp);
      if (p + chLen > doc.length()) {
        break;
      }
      final String typedStr = doc.substring(p, p + chLen);
      final int expPos = expectedOffsetForDocumentIndex(doc, expectedFull, p);
      if (expPos >= expectedFull.length()) {
        p += chLen;
        continue;
      }
      final int expLen = Character.charCount(expectedFull.codePointAt(expPos));
      if (expPos + expLen > expectedFull.length()) {
        p += chLen;
        continue;
      }
      final String expStr = expectedFull.substring(expPos, expPos + expLen);
      if (chLen != expLen) {
        p += chLen;
        continue;
      }
      final String replacement = matchInputEquivalence(typedStr, expStr, rules);
      if (replacement != null && !replacement.equals(typedStr)) {
        replaceSubstring(area, p, p + chLen, replacement);
        final int delta = replacement.length() - chLen;
        end += delta;
        p += replacement.length();
      } else {
        p += chLen;
      }
    }
  }

  /**
   * {@link JTextComponent#replaceRange} exists only from Java 21; use {@link AbstractDocument}
   * APIs so Fly (single-line) and Dialog work on the project's Java 17 baseline.
   */
  private static void replaceSubstring(
      final JTextComponent area,
      final int startInclusive,
      final int endExclusive,
      final String replacement) {
    final Document doc = area.getDocument();
    try {
      if (doc instanceof AbstractDocument ad) {
        ad.replace(startInclusive, endExclusive - startInclusive, replacement, null);
      } else {
        doc.remove(startInclusive, endExclusive - startInclusive);
        doc.insertString(startInclusive, replacement, null);
      }
    } catch (final BadLocationException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
