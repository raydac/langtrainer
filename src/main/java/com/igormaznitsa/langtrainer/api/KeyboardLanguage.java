package com.igormaznitsa.langtrainer.api;

import java.util.List;

public enum KeyboardLanguage {
  ENG(
      "ENG",
      List.of(
          "1234567890",
          "qwertyuiop",
          "asdfghjkl",
          "zxcvbnm",
          " ,.-()")),
  RUS(
      "RUS",
      List.of(
          "1234567890",
          "йцукенгшщз",
          "фывапролдж",
          "ячсмитьбю",
          " ,.-()")),
  EST(
      "EST",
      List.of(
          "1234567890",
          "qwertyuiopü",
          "asdfghjklöä",
          "zxcvbnmšžõ",
          " ,.-()"));

  private final String abbreviation;
  private final List<String> rows;

  KeyboardLanguage(final String abbreviation, final List<String> rows) {
    this.abbreviation = abbreviation;
    this.rows = rows;
  }

  public static List<KeyboardLanguage> normalize(final List<KeyboardLanguage> languages) {
    List<KeyboardLanguage> result;
    result = (languages == null || languages.isEmpty())
        ? List.of(ENG)
        : languages.stream().distinct().toList();
    return result;
  }

  public String getAbbreviation() {
    String result;
    result = this.abbreviation;
    return result;
  }

  public List<String> getRows() {
    List<String> result;
    result = this.rows;
    return result;
  }

  public KeyboardLanguage nextIn(final List<KeyboardLanguage> enabledLanguages) {
    final int index = enabledLanguages.indexOf(this);
    final int safeIndex = Math.max(index, 0);
    KeyboardLanguage result;
    result = enabledLanguages.get((safeIndex + 1) % enabledLanguages.size());
    return result;
  }
}
