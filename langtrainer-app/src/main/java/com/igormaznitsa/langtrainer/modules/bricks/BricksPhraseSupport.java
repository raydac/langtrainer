package com.igormaznitsa.langtrainer.modules.bricks;

import com.igormaznitsa.langtrainer.text.PhraseWordSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bricks phrase tokenization: word bricks exclude trailing sentence punctuation.
 */
final class BricksPhraseSupport {

  private BricksPhraseSupport() {
  }

  static Parts parsePhrase(final String line) {
    if (line == null || line.isBlank()) {
      return new Parts(List.of(), null);
    }
    final List<String> raw = new ArrayList<>(PhraseWordSupport.splitWords(line));
    if (raw.isEmpty()) {
      return new Parts(List.of(), null);
    }
    String trailingSuffix = null;
    while (!raw.isEmpty()) {
      final String last = raw.get(raw.size() - 1);
      if (!isPunctuationOnlyToken(last)) {
        break;
      }
      trailingSuffix = mergeSuffix(trailingSuffix, last);
      raw.remove(raw.size() - 1);
    }
    if (!raw.isEmpty()) {
      final int lastIndex = raw.size() - 1;
      final String lastWord = raw.get(lastIndex);
      final int peelEnd = peelSentenceEndPunctuationEnd(lastWord);
      if (peelEnd < lastWord.length()) {
        final String punct = lastWord.substring(peelEnd);
        trailingSuffix = mergeSuffix(trailingSuffix, punct);
        final String stem = lastWord.substring(0, peelEnd);
        if (stem.isEmpty()) {
          raw.remove(lastIndex);
        } else {
          raw.set(lastIndex, stem);
        }
      }
    }
    return new Parts(toBrickDisplayTokens(raw), displayableFixedSuffix(trailingSuffix));
  }

  /**
   * True when {@code slotIds} left-to-right reads the target phrase ({@code wordTokens.get(i)} at
   * each index), allowing interchangeable bricks that share the same label (e.g. two {@code THE}).
   */
  static boolean matchesTargetPhraseOrder(
      final List<Integer> slotIds, final List<String> wordTokens) {
    if (slotIds.size() != wordTokens.size()) {
      return false;
    }
    for (int i = 0; i < slotIds.size(); i++) {
      if (!wordTokens.get(slotIds.get(i)).equals(wordTokens.get(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Uppercase brick labels so capitalization does not reveal the first word of a sentence.
   */
  static String brickDisplayText(final String token) {
    if (token == null || token.isEmpty()) {
      return "";
    }
    return token.toUpperCase(Locale.ROOT);
  }

  private static List<String> toBrickDisplayTokens(final List<String> tokens) {
    return tokens.stream().map(BricksPhraseSupport::brickDisplayText).toList();
  }

  private static String mergeSuffix(final String existing, final String added) {
    if (added == null || added.isEmpty()) {
      return existing;
    }
    return existing == null ? added : existing + added;
  }

  /**
   * {@code !}, {@code ?}, {@code …} shown fixed after the build strip; {@code .} omitted.
   */
  private static String displayableFixedSuffix(final String trailing) {
    if (trailing == null || trailing.isEmpty()) {
      return null;
    }
    if (trailing.codePoints().allMatch(cp -> cp == '.')) {
      return null;
    }
    return trailing;
  }

  private static boolean isPunctuationOnlyToken(final String token) {
    if (token.isEmpty()) {
      return false;
    }
    return token.codePoints().allMatch(BricksPhraseSupport::isSentenceEndPunctuation);
  }

  private static int peelSentenceEndPunctuationEnd(final String word) {
    int end = word.length();
    while (end > 0) {
      final int cp = word.codePointBefore(end);
      if (!isSentenceEndPunctuation(cp)) {
        break;
      }
      end -= Character.charCount(cp);
    }
    return end;
  }

  private static boolean isSentenceEndPunctuation(final int codePoint) {
    return codePoint == '.'
        || codePoint == '!'
        || codePoint == '?'
        || codePoint == '…'
        || codePoint == ','
        || codePoint == ';'
        || codePoint == ':';
  }

  record Parts(List<String> wordTokens, String fixedEndSuffix) {
    Parts {
      wordTokens = List.copyOf(wordTokens);
    }
  }
}
