package com.igormaznitsa.langtrainer.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;

/**
 * How shared entries in {@code /common/jsons/index.json} are visible to a module. If a resource
 * entry has a {@code modules} array, it is shown only to modules named there; if {@code modules} is
 * absent, the entry is available to all modules.
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
    final JsonArray modules = entry.getAsJsonArray("modules");
    for (JsonElement m : modules) {
      if (m.isJsonPrimitive() && sameModuleId(m.getAsString(), target)) {
        return true;
      }
    }
    return false;
  }

  private static boolean sameModuleId(final String raw, final LangTrainerModuleId id) {
    if (raw == null || raw.isEmpty()) {
      return false;
    }
    return id.name().equals(raw);
  }
}
