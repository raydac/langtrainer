package com.igormaznitsa.langtrainer.modules.editor;

import com.igormaznitsa.langtrainer.engine.InputEquivalenceRow;
import java.util.ArrayList;
import java.util.List;

/**
 * Ready-made {@link InputEquivalenceRow} lists for learners who type on a US English keyboard while
 * the answer side uses Estonian, German, Czech, or Esperanto letters. Key tokens are what may be typed;
 * value tokens are accepted substitutions for the expected character (see {@code InputEquivalenceSupport}).
 */
public final class InputEquivalenceEnglishPresets {

  private InputEquivalenceEnglishPresets() {
  }

  public static List<InputEquivalenceRow> rowsFor(final TargetLanguage lang) {
    return switch (lang) {
      case ESTONIAN -> List.of(
          row("a,A", "ä,Ä"),
          row("o,O", "ö,Ö,õ,Õ"),
          row("u,U", "ü,Ü"),
          row("s,S", "š,Š"),
          row("z,Z", "ž,Ž"));
      case GERMAN -> List.of(
          row("a,A", "ä,Ä"),
          row("o,O", "ö,Ö"),
          row("u,U", "ü,Ü"),
          row("a,A", "ä,Ä"),
          row("o,O", "ö,Ö"),
          row("u,U", "ü,Ü"),
          row("s,S", "ß,ẞ"));
      case CZECH -> List.of(
          row("a,A", "á,Á"),
          row("c,C", "č,Č"),
          row("d,D", "ď,Ď"),
          row("e,E", "é,É,ě,Ě"),
          row("i,I", "í,Í"),
          row("n,N", "ň,Ň"),
          row("o,O", "ó,Ó"),
          row("r,R", "ř,Ř"),
          row("s,S", "š,Š"),
          row("t,T", "ť,Ť"),
          row("u,U", "ú,Ú,ů,Ů"),
          row("y,Y", "ý,Ý"),
          row("z,Z", "ž,Ž"));
      case ESPERANTO -> List.of(
          row("c,C", "ĉ,Ĉ"),
          row("g,G", "ĝ,Ĝ"),
          row("h,H", "ĥ,Ĥ"),
          row("j,J", "ĵ,Ĵ"),
          row("s,S", "ŝ,Ŝ"),
          row("u,U", "ŭ,Ŭ"));
    };
  }

  private static InputEquivalenceRow row(final String keysCsv, final String valuesCsv) {
    return new InputEquivalenceRow(splitCsv(keysCsv), splitCsv(valuesCsv));
  }

  private static List<String> splitCsv(final String csv) {
    final List<String> out = new ArrayList<>();
    for (final String p : csv.split(",")) {
      final String s = p.strip();
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return List.copyOf(out);
  }

  public enum TargetLanguage {
    ESTONIAN("Estonian"),
    GERMAN("German"),
    CZECH("Czech"),
    ESPERANTO("Esperanto");

    private final String label;

    TargetLanguage(final String label) {
      this.label = label;
    }

    public String label() {
      return this.label;
    }
  }
}
