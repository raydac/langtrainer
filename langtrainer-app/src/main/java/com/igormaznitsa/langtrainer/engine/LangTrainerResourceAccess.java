package com.igormaznitsa.langtrainer.engine;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;
import java.util.List;

/**
 * Visibility of bundled resources for a {@link com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule}.
 * If a {@link DialogDefinition} has a {@code modules} list, it is shown only to modules named there;
 * if {@code modules} is absent or empty, the resource is available to all modules. A module id
 * prefixed with {@code !} excludes that module from the resource list.
 */
public final class LangTrainerResourceAccess {

  private static final String UNSUPPORTED_MODULE_PREFIX = "!";

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
    if (modules.stream().anyMatch(module -> excludesModuleId(module, target))) {
      return false;
    }
    return modules.stream().noneMatch(LangTrainerResourceAccess::isSupportedModuleMarker)
        || modules.stream().anyMatch(module -> sameModuleId(module, target));
  }

  private static boolean excludesModuleId(final String raw, final LangTrainerModuleId id) {
    final String marker = raw == null ? "" : raw.strip();
    return !isEmpty(raw)
        && marker.startsWith(UNSUPPORTED_MODULE_PREFIX)
        && id.name().equals(marker.substring(UNSUPPORTED_MODULE_PREFIX.length()).strip());
  }

  private static boolean isSupportedModuleMarker(final String raw) {
    return !isEmpty(raw) && !raw.strip().startsWith(UNSUPPORTED_MODULE_PREFIX);
  }

  private static boolean sameModuleId(final String raw, final LangTrainerModuleId id) {
    if (isEmpty(raw)) {
      return false;
    }
    return id.name().equals(raw.strip());
  }
}
