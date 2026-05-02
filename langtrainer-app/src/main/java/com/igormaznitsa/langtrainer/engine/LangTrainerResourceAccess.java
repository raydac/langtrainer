package com.igormaznitsa.langtrainer.engine;

import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;
import java.util.stream.StreamSupport;

/**
 * How shared <em>leaf</em> entries in {@code /common/jsons/index.json} are visible to a module.
 * Folder nodes in the index are handled separately; this applies only to objects with {@code
 * "resource"}. If a leaf has a {@code modules} array, it is shown only to modules named there; if
 * {@code modules} is absent, the entry is available to all modules.
 */
public final class LangTrainerResourceAccess {

  private LangTrainerResourceAccess() {
  }

  public static boolean visibleToModule(
      final JsonObject entry, final LangTrainerModuleId target) {
    if (entry == null || target == null) {
      return false;
    }
    if (!entry.has("resource") || !entry.get("resource").isJsonPrimitive()) {
      return false;
    }
    if (!entry.has("modules") || !entry.get("modules").isJsonArray()) {
      return true;
    }
    return StreamSupport.stream(entry.getAsJsonArray("modules").spliterator(), false)
        .anyMatch(
            m -> m.isJsonPrimitive() && sameModuleId(m.getAsString(), target));
  }

  private static boolean sameModuleId(final String raw, final LangTrainerModuleId id) {
    if (raw == null || raw.isEmpty()) {
      return false;
    }
    return id.name().equals(raw);
  }
}
