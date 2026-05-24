package com.igormaznitsa.langtrainer.engine;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;
import java.util.List;

/**
 * Visibility of bundled resources for a {@link com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule}.
 * If a {@link DialogDefinition} has a {@code modules} list, it is shown only to modules named there;
 * if {@code modules} is absent or empty, the resource is available to all modules.
 */
public final class LangTrainerResourceAccess {

  private LangTrainerResourceAccess() {
  }

  public static boolean visibleToModule(
      final DialogDefinition definition, final LangTrainerModuleId target) {
    if (definition == null || target == null) {
      return false;
    }
    final List<String> modules = definition.modules();
    if (isEmpty(modules)) {
      return true;
    }
    return modules.stream().anyMatch(m -> sameModuleId(m, target));
  }

  private static boolean sameModuleId(final String raw, final LangTrainerModuleId id) {
    if (isEmpty(raw)) {
      return false;
    }
    return id.name().equals(raw);
  }
}
