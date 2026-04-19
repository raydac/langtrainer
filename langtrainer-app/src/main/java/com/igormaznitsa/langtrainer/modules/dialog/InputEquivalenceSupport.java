package com.igormaznitsa.langtrainer.modules.dialog;

import com.igormaznitsa.langtrainer.engine.InputEquivalenceRow;
import java.util.List;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

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

  private static boolean sameSlotOrLetterCaseInsensitive(final String a, final String b) {
    if (a.equals(b)) {
      return true;
    }
    if (a.length() != 1 || b.length() != 1) {
      return false;
    }
    final int ca = a.codePointAt(0);
    final int cb = b.codePointAt(0);
    return Character.isLetter(ca) && Character.isLetter(cb)
        && Character.toLowerCase(ca) == Character.toLowerCase(cb);
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
      final String matched =
          keys.size() == vals.size()
              ? matchPositional(typed, expectedChar, keys, vals)
              : matchAnyKeyToExpectedValue(typed, expectedChar, keys, vals);
      if (matched != null) {
        return matched;
      }
    }
    return null;
  }

  private static String matchPositional(
      final String typed,
      final String expectedChar,
      final List<String> keys,
      final List<String> vals) {
    for (int i = 0; i < keys.size(); i++) {
      if (!sameSlotOrLetterCaseInsensitive(typed, keys.get(i))) {
        continue;
      }
      if (!sameSlotOrLetterCaseInsensitive(vals.get(i), expectedChar)) {
        continue;
      }
      return alignMappedLetterCaseToExpected(vals.get(i), expectedChar);
    }
    return null;
  }

  private static String matchAnyKeyToExpectedValue(
      final String typed,
      final String expectedChar,
      final List<String> keys,
      final List<String> vals) {
    if (!keys.stream().anyMatch(k -> sameSlotOrLetterCaseInsensitive(typed, k))) {
      return null;
    }
    for (final String v : vals) {
      if (sameSlotOrLetterCaseInsensitive(v, expectedChar)) {
        return alignMappedLetterCaseToExpected(v, expectedChar);
      }
    }
    return null;
  }

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
        end += replacement.length() - chLen;
        p += replacement.length();
      } else {
        p += chLen;
      }
    }
  }

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
