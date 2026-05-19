package com.igormaznitsa.langtrainer.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LangResourceJson {

  private static final Gson GSON = new Gson();
  private static final Gson GSON_PRETTY = new GsonBuilder()
      .setPrettyPrinting()
      .create();

  private LangResourceJson() {
  }

  public static String toPrettyJson(final DialogDefinition definition) {
    final JsonObject root = new JsonObject();
    root.addProperty("menuName", definition.menuName());
    root.addProperty("description", definition.description());
    root.addProperty("langA", definition.langA());
    root.addProperty("langB", definition.langB());
    if (definition.path() != null && !definition.path().isBlank()) {
      root.addProperty("path", definition.path().strip());
    }
    if (definition.modules() != null && !definition.modules().isEmpty()) {
      final JsonArray modules = new JsonArray();
      for (final String moduleId : definition.modules()) {
        modules.add(moduleId);
      }
      root.add("modules", modules);
    }
    if (definition.shuffled()) {
      root.addProperty("shuffled", true);
    }
    final JsonArray lines = new JsonArray();
    for (final DialogLine line : definition.lines()) {
      final JsonObject row = new JsonObject();
      row.addProperty("A", line.a());
      row.addProperty("B", line.b());
      lines.add(row);
    }
    root.add("lines", lines);
    if (!definition.inputEqu().isEmpty()) {
      root.add("inputEqu", GSON.toJsonTree(definition.inputEqu()));
    }
    return GSON_PRETTY.toJson(root) + "\n";
  }

  public static DialogDefinition parse(final String jsonText) {
    try {
      final DialogDefinition def =
          GSON.fromJson(jsonText, DialogDefinition.class);
      if (def == null) {
        throw new JsonParseException("Empty or invalid JSON");
      }
      return normalizeAfterLoad(def);
    } catch (final JsonParseException ex) {
      throw new IllegalStateException(ex.getMessage() == null
          ? "Invalid JSON"
          : ex.getMessage(), ex);
    }
  }

  public static DialogDefinition parseFromPath(final Path path) {
    try {
      return parse(Files.readString(path, StandardCharsets.UTF_8));
    } catch (final IOException ex) {
      throw new IllegalStateException("Can't load JSON file: " + path, ex);
    }
  }

  private static DialogDefinition normalizeAfterLoad(final DialogDefinition def) {
    final List<DialogLine> lines = def.lines() == null || def.lines().isEmpty()
        ? List.of(new DialogLine("", ""))
        : def.lines();
    final String path = def.path() == null || def.path().isBlank() ? null : def.path().strip();
    return new DialogDefinition(
        nullToEmpty(def.menuName()),
        nullToEmpty(def.description()),
        nullToEmpty(def.langA()),
        nullToEmpty(def.langB()),
        lines,
        def.inputEqu(),
        def.shuffled(),
        path,
        normalizeModules(def.modules()));
  }

  private static List<String> normalizeModules(final List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    final List<String> out = new ArrayList<>();
    for (final String moduleId : raw) {
      if (moduleId == null || moduleId.isBlank()) {
        continue;
      }
      final String id = moduleId.strip();
      try {
        LangTrainerModuleId.valueOf(id);
      } catch (final IllegalArgumentException ex) {
        throw new IllegalStateException("Unknown module id in \"modules\": " + id, ex);
      }
      if (!out.contains(id)) {
        out.add(id);
      }
    }
    return out.isEmpty() ? null : List.copyOf(out);
  }

  private static String nullToEmpty(final String s) {
    return s == null ? "" : s;
  }
}
