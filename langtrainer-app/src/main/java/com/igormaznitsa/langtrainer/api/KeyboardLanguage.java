package com.igormaznitsa.langtrainer.api;

import java.util.List;

public enum KeyboardLanguage {
  ENG(
      "ENGLISH",
      List.of(
          "1234567890",
          "qwertyuiop",
          "asdfghjkl",
          "zxcvbnm",
          " ,.-()")),
  RUS(
      "РУССКИЙ",
      List.of(
          "1234567890",
          "йцукенгшщзё",
          "фывапролдж",
          "ячсмитьбю",
          " ,.-()")),
  EST(
      "EESTI",
      List.of(
          "1234567890",
          "qwertyuiopü",
          "asdfghjklöä",
          "zxcvbnmšžõ",
          " ,.-()")),
  GER(
      "DEUTSCH",
      List.of(
          "1234567890",
          "qwertzuiopü",
          "asdfghjklöä",
          "yxcvbnmß",
          " ,.-()")),
  CZE(
      "ČEŠTINA",
      List.of(
          "1234567890",
          "qwertzuiopúů",
          "asdfghjkléáí",
          "yxcvbnmčďěňřšťžýó",
          " ,.-()")),
  ESP(
      "ESPERANTO",
      List.of(
          "1234567890",
          "qwertyuiopĝĥ",
          "asdfghjklĵŝ",
          "zxcvbnmĉŭ",
          " ,.-()")),
  SWE(
      "SVENSKA",
      List.of(
          "1234567890",
          "qwertyuiopå",
          "asdfghjklöä",
          "zxcvbnm",
          " ,.-()")),
  NOR(
      "NORSK",
      List.of(
          "1234567890",
          "qwertyuiopå",
          "asdfghjkløæ",
          "zxcvbnm",
          " ,.-()")),
  DAN(
      "DANSK",
      List.of(
          "1234567890",
          "qwertyuiopå",
          "asdfghjklæø",
          "zxcvbnm",
          " ,.-()")),
  DUT(
      "NEDERLANDS",
      List.of(
          "1234567890",
          "qwertyuiopùúûüÿ",
          "asdfghjklêëìíîï",
          "zxcvbnmàáâäèéòóôö",
          " ,.-()")),
  FIN(
      "SUOMEN",
      List.of(
          "1234567890",
          "qwertyuiopå",
          "asdfghjklöä",
          "zxcvbnm",
          " ,.-()")),
  LIT(
      "LIETUVIŲ",
      List.of(
          "1234567890",
          "qwertyuiopąčęė",
          "asdfghjklįšųū",
          "zxcvbnmž",
          " ,.-()")),
  LAT(
      "LATVIEŠU",
      List.of(
          "1234567890",
          "qwertyuiopāčē",
          "asdfghjklģīķļ",
          "zxcvbnmņšūž",
          " ,.-()")),
  SPA(
      "ESPAÑOL",
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
      ENG, SPA, GER, EST, FIN, LIT, LAT, RUS, CZE, ESP, SWE, NOR, DAN, DUT);

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
