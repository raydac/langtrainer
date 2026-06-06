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
    modules = isEmpty(modules) ? List.of() : List.copyOf(modules);
  }
}
