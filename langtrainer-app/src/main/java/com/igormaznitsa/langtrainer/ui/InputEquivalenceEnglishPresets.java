package com.igormaznitsa.langtrainer.ui;

import com.igormaznitsa.langtrainer.engine.InputEquivalenceRow;
import java.util.ArrayList;
import java.util.List;

/**
 * Ready-made {@link InputEquivalenceRow} lists for learners who type on a US English keyboard while
 * the answer side uses Estonian, German, Czech, Esperanto, Swedish, Norwegian, Danish, Dutch,
 * Finnish, Lithuanian, Latvian, or Ukrainian letters. Key tokens are what may be typed;
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
      case SWEDISH, FINNISH -> List.of(
          row("a,A", "ä,Ä,å,Å"),
          row("o,O", "ö,Ö"));
      case NORWEGIAN, DANISH -> List.of(
          row("a,A", "æ,Æ,å,Å"),
          row("o,O", "ø,Ø"));
      case DUTCH -> List.of(
          row("a,A", "à,À,á,Á,â,Â,ä,Ä"),
          row("e,E", "è,È,é,É,ê,Ê,ë,Ë"),
          row("i,I", "ì,Ì,í,Í,î,Î,ï,Ï"),
          row("o,O", "ò,Ò,ó,Ó,ô,Ô,ö,Ö"),
          row("u,U", "ù,Ù,ú,Ú,û,Û,ü,Ü"),
          row("y,Y", "ÿ,Ÿ"));
      case LITHUANIAN -> List.of(
          row("a,A", "ą,Ą,á,Á"),
          row("c,C", "č,Č"),
          row("e,E", "ę,Ę,ė,Ė,é,É"),
          row("i,I", "į,Į,í,Í"),
          row("s,S", "š,Š"),
          row("u,U", "ų,Ų,ū,Ū,ú,Ú"),
          row("z,Z", "ž,Ž"));
      case LATVIAN -> List.of(
          row("a,A", "ā,Ā"),
          row("c,C", "č,Č"),
          row("e,E", "ē,Ē"),
          row("g,G", "ģ,Ģ"),
          row("i,I", "ī,Ī"),
          row("k,K", "ķ,Ķ"),
          row("l,L", "ļ,Ļ"),
          row("n,N", "ņ,Ņ"),
          row("s,S", "š,Š"),
          row("u,U", "ū,Ū"),
          row("z,Z", "ž,Ž"));
      case UKRAINIAN -> List.of(
          row("e,E", "є,Є"),
          row("g,G", "ґ,Ґ"),
          row("i,I", "і,І,ї,Ї"));
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
    ESPERANTO("Esperanto"),
    SWEDISH("Swedish"),
    NORWEGIAN("Norwegian"),
    DANISH("Danish"),
    DUTCH("Dutch"),
    FINNISH("Finnish"),
    LITHUANIAN("Lithuanian"),
    LATVIAN("Latvian"),
    UKRAINIAN("Ukrainian");

    private final String label;

    TargetLanguage(final String label) {
      this.label = label;
    }

    public String label() {
      return this.label;
    }
  }
}
