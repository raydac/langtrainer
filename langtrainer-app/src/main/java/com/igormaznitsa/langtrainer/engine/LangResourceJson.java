package com.igormaznitsa.langtrainer.engine;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LangResourceJson {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final ObjectWriter PRETTY_WRITER = MAPPER.writerWithDefaultPrettyPrinter();

  private LangResourceJson() {
  }

  public static String toPrettyJson(final DialogDefinition definition) {
    final ObjectNode root = MAPPER.createObjectNode();
    root.put("menuName", definition.menuName());
    root.put("description", definition.description());
    root.put("langA", definition.langA());
    root.put("langB", definition.langB());
    if (!definition.rtl().isEmpty()) {
      final ArrayNode rtl = root.putArray("rtl");
      for (final String language : definition.rtl()) {
        rtl.add(language);
      }
    }
    if (!isBlank(definition.path())) {
      root.put("path", definition.path().strip());
    }
    if (!isEmpty(definition.modules())) {
      final ArrayNode modules = root.putArray("modules");
      for (final String moduleId : definition.modules()) {
        modules.add(moduleId);
      }
    }
    if (definition.shuffled()) {
      root.put("shuffled", true);
    }
    final ArrayNode lines = root.putArray("lines");
    for (final DialogLine line : definition.lines()) {
      final ObjectNode row = MAPPER.createObjectNode();
      row.put("A", line.a());
      row.put("B", line.b());
      lines.add(row);
    }
    if (!definition.inputEqu().isEmpty()) {
      root.set("inputEqu", MAPPER.valueToTree(definition.inputEqu()));
    }
    try {
      return PRETTY_WRITER.writeValueAsString(root) + "\n";
    } catch (final JsonProcessingException ex) {
      throw new IllegalStateException("Can't write JSON", ex);
    }
  }

  public static DialogDefinition parse(final String jsonText) {
    try {
      final DialogDefinition def =
          MAPPER.readValue(jsonText, DialogDefinition.class);
      if (def == null) {
        throw new IllegalStateException("Empty or invalid JSON");
      }
      return normalizeAfterLoad(def);
    } catch (final JsonProcessingException ex) {
      throw new IllegalStateException(requireNonNullElse(ex.getMessage(), "Invalid JSON"), ex);
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
    final List<DialogLine> lines = isEmpty(def.lines())
        ? List.of(new DialogLine("", ""))
        : def.lines();
    return new DialogDefinition(
        requireNonNullElse(def.menuName(), ""),
        requireNonNullElse(def.description(), ""),
        requireNonNullElse(def.langA(), ""),
        requireNonNullElse(def.langB(), ""),
        normalizeStringList(def.rtl()),
        lines,
        def.inputEqu(),
        def.shuffled(),
        stripToNull(def.path()),
        normalizeModules(def.modules()));
  }

  private static List<String> normalizeStringList(final List<String> raw) {
    if (isEmpty(raw)) {
      return List.of();
    }
    final List<String> out = new ArrayList<>();
    for (final String value : raw) {
      if (isBlank(value)) {
        continue;
      }
      final String normalized = value.strip();
      if (out.stream().noneMatch(existing -> existing.equalsIgnoreCase(normalized))) {
        out.add(normalized);
      }
    }
    return List.copyOf(out);
  }

  private static List<String> normalizeModules(final List<String> raw) {
    if (isEmpty(raw)) {
      return List.of();
    }
    final List<String> out = new ArrayList<>();
    for (final String moduleId : raw) {
      if (isBlank(moduleId)) {
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
    return List.copyOf(out);
  }
}
