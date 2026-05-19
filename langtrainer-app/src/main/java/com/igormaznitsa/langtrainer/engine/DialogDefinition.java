package com.igormaznitsa.langtrainer.engine;

import java.util.List;

public record DialogDefinition(
    String menuName,
    String description,
    String langA,
    String langB,
    List<DialogLine> lines,
    List<InputEquivalenceRow> inputEqu,
    boolean shuffled,
    String path,
    List<String> modules
) {
  public DialogDefinition {
    inputEqu = inputEqu == null ? List.of() : List.copyOf(inputEqu);
    modules = modules == null || modules.isEmpty() ? null : List.copyOf(modules);
  }

  public DialogDefinition(
      final String menuName,
      final String description,
      final String langA,
      final String langB,
      final List<DialogLine> lines,
      final List<InputEquivalenceRow> inputEqu,
      final boolean shuffled) {
    this(menuName, description, langA, langB, lines, inputEqu, shuffled, null, null);
  }
}
