package com.igormaznitsa.langtrainer.engine;

import java.util.List;

public record InputEquivalenceRow(List<String> key, List<String> value) {
  public InputEquivalenceRow {
    key = List.copyOf(key);
    value = List.copyOf(value);
  }
}
