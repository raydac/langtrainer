package com.igormaznitsa.langtrainer.modules.dialog;

import java.util.List;

/**
 * One row of {@code inputEqu}: typed alternatives in {@code key} align by index with {@code value}.
 * When the user inserts {@code key[i]} and the expected text at the same offset is {@code value[i]},
 * the insertion is replaced with {@code value[i]} (canonical form).
 */
public record InputEquivalenceRow(List<String> key, List<String> value) {
  public InputEquivalenceRow {
    if (key.size() != value.size()) {
      throw new IllegalArgumentException("inputEqu row: key and value must have the same length");
    }
    key = List.copyOf(key);
    value = List.copyOf(value);
  }
}
