package com.igormaznitsa.langtrainer.engine;

import static java.util.Objects.requireNonNull;

import java.util.List;

public record InputEquivalenceRow(List<String> key, List<String> value) {
  public InputEquivalenceRow {
    key = List.copyOf(requireNonNull(key, "inputEqu key must not be null"));
    value = List.copyOf(requireNonNull(value, "inputEqu value must not be null"));
  }
}
