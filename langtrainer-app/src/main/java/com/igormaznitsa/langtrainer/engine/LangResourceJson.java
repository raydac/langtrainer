package com.igormaznitsa.langtrainer.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LangResourceJson {

  private static final Gson GSON = new Gson();
  private static final Gson GSON_PRETTY = new GsonBuilder()
      .setPrettyPrinting()
      .create();

  private LangResourceJson() {
  }

  public static String toPrettyJson(final DialogDefinition definition) {
    return GSON_PRETTY.toJson(definition) + "\n";
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
    return new DialogDefinition(
        nullToEmpty(def.menuName()),
        nullToEmpty(def.description()),
        nullToEmpty(def.langA()),
        nullToEmpty(def.langB()),
        lines,
        def.inputEqu(),
        def.shuffled());
  }

  private static String nullToEmpty(final String s) {
    return s == null ? "" : s;
  }
}
