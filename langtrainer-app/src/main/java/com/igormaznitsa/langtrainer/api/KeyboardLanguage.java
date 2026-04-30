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
          "йцукенгшщзё",
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
          " ,.-()")),
  GER(
      "GER",
      List.of(
          "1234567890",
          "qwertzuiopü",
          "asdfghjklöä",
          "yxcvbnmß",
          " ,.-()")),
  CZE(
      "CZE",
      List.of(
          "1234567890",
          "qwertzuiopúů",
          "asdfghjkléáí",
          "yxcvbnmčďěňřšťžýó",
          " ,.-()")),
  ESP(
      "ESP",
      List.of(
          "1234567890",
          "qwertyuiopĝĥ",
          "asdfghjklĵŝ",
          "zxcvbnmĉŭ",
          " ,.-()")),
  SWE(
      "SWE",
      List.of(
          "1234567890",
          "qwertyuiopå",
          "asdfghjklöä",
          "zxcvbnm",
          " ,.-()")),
  NOR(
      "NOR",
      List.of(
          "1234567890",
          "qwertyuiopå",
          "asdfghjkløæ",
          "zxcvbnm",
          " ,.-()")),
  DAN(
      "DAN",
      List.of(
          "1234567890",
          "qwertyuiopå",
          "asdfghjklæø",
          "zxcvbnm",
          " ,.-()")),
  DUT(
      "DUT",
      List.of(
          "1234567890",
          "qwertyuiop",
          "asdfghjkl",
          "zxcvbnmàáâäèéêëìíîïòóôöùúûüÿ",
          " ,.-()")),
  FIN(
      "FIN",
      List.of(
          "1234567890",
          "qwertyuiopå",
          "asdfghjklöä",
          "zxcvbnm",
          " ,.-()")),
  LIT(
      "LIT",
      List.of(
          "1234567890",
          "qwertyuiopąčęė",
          "asdfghjklįšųū",
          "zxcvbnmž",
          " ,.-()")),
  LAT(
      "LAT",
      List.of(
          "1234567890",
          "qwertyuiopāčē",
          "asdfghjklģīķļ",
          "zxcvbnmņšūž",
          " ,.-()")),
  SPA(
      "SPA",
      List.of(
          "1234567890",
          "qwertyuiop",
          "asdfghjklñ",
          "zxcvbnmáéíóúü",
          " ,.-()?!¡¿"));

  /**
   * Languages for modules that show the full on-screen virtual keyboard (ENG / RUS / EST rows).
   */
  public static final List<KeyboardLanguage> VIRTUAL_BOARD_ALL = List.of(
      ENG, RUS, EST, GER, CZE, ESP, SWE, NOR, DAN, DUT, FIN, LIT, LAT, SPA);

  private final String abbreviation;
  private final List<String> rows;

  KeyboardLanguage(final String abbreviation, final List<String> rows) {
    this.abbreviation = abbreviation;
    this.rows = rows;
  }

  public static List<KeyboardLanguage> normalize(final List<KeyboardLanguage> languages) {
    if (languages == null || languages.isEmpty()) {
      return List.of(ENG);
    }
    return languages.stream().distinct().toList();
  }

  public String getAbbreviation() {
    return this.abbreviation;
  }

  public List<String> getRows() {
    return this.rows;
  }

  public KeyboardLanguage nextIn(final List<KeyboardLanguage> enabledLanguages) {
    final int index = enabledLanguages.indexOf(this);
    final int safeIndex = Math.max(index, 0);
    return enabledLanguages.get((safeIndex + 1) % enabledLanguages.size());
  }
}
