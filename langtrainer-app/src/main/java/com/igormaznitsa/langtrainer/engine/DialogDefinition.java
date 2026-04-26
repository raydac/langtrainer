package com.igormaznitsa.langtrainer.engine;

import java.util.List;

public record DialogDefinition(
    String menuName,
    String description,
    String langA,
    String langB,
    List<DialogLine> lines,
    List<InputEquivalenceRow> inputEqu,
    boolean shuffled
) {
  public DialogDefinition {
    inputEqu = inputEqu == null ? List.of() : List.copyOf(inputEqu);
  }
}
