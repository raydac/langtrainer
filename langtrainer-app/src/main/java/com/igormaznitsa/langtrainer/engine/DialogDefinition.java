package com.igormaznitsa.langtrainer.engine;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import java.util.List;

public record DialogDefinition(
    String menuName,
    String description,
    String langA,
    String langB,
    List<String> rtl,
    List<DialogLine> lines,
    List<InputEquivalenceRow> inputEqu,
    boolean shuffled,
    String path,
    List<String> modules
) {
  public DialogDefinition {
    rtl = isEmpty(rtl) ? List.of() : List.copyOf(rtl);
    inputEqu = inputEqu == null ? List.of() : List.copyOf(inputEqu);
    modules = isEmpty(modules) ? null : List.copyOf(modules);
  }

  public DialogDefinition(
      final String menuName,
      final String description,
      final String langA,
      final String langB,
      final List<DialogLine> lines,
      final List<InputEquivalenceRow> inputEqu,
      final boolean shuffled) {
    this(menuName, description, langA, langB, null, lines, inputEqu, shuffled, null, null);
  }
}
