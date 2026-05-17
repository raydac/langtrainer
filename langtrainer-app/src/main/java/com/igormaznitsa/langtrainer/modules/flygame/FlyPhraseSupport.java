package com.igormaznitsa.langtrainer.modules.flygame;

import com.igormaznitsa.langtrainer.engine.DialogLine;
import com.igormaznitsa.langtrainer.text.PhraseWordSupport;
import java.util.ArrayList;
import java.util.List;

/**
 * Fly game phrase eligibility: typing targets must be short enough to enter in flight time.
 */
final class FlyPhraseSupport {

  static final int MAX_TYPING_WORDS = 4;

  private FlyPhraseSupport() {
  }

  static List<DialogLine> playableLines(
      final List<DialogLine> lines, final boolean userTypesSideA) {
    if (lines == null || lines.isEmpty()) {
      return List.of();
    }
    final List<DialogLine> out = new ArrayList<>();
    for (final DialogLine line : lines) {
      final String typingTarget = userTypesSideA ? line.a() : line.b();
      if (wordCount(typingTarget) <= MAX_TYPING_WORDS) {
        out.add(line);
      }
    }
    return List.copyOf(out);
  }

  static int wordCount(final String line) {
    return PhraseWordSupport.splitWords(line).size();
  }
}
