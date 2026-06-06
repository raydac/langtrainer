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
  private static final String UNSUPPORTED_MODULE_PREFIX = "!";
  private static final long MAX_JSON_BYTES = 8L * 1024L * 1024L;

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
      if (line.hasImage()) {
        row.put("image", line.image());
      }
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
      requireJsonFileSize(path);
      return parse(Files.readString(path, StandardCharsets.UTF_8));
    } catch (final IOException ex) {
      throw new IllegalStateException("Can't load JSON file: " + path, ex);
    }
  }

  private static void requireJsonFileSize(final Path path) throws IOException {
    final long size = Files.size(path);
    if (size > MAX_JSON_BYTES) {
      throw new IllegalStateException(
          "JSON file is too large: %s bytes, maximum is %s bytes".formatted(size, MAX_JSON_BYTES));
    }
  }

  private static DialogDefinition normalizeAfterLoad(final DialogDefinition def) {
    final List<DialogLine> lines = normalizeLines(def.lines());
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

  private static List<DialogLine> normalizeLines(final List<DialogLine> raw) {
    if (isEmpty(raw)) {
      return List.of(new DialogLine("", ""));
    }
    return raw.stream()
        .map(line -> new DialogLine(
            requireNonNullElse(line.a(), ""),
            requireNonNullElse(line.b(), ""),
            stripToNull(line.image())))
        .toList();
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
      final String marker = normalizeModuleMarker(moduleId.strip());
      if (!out.contains(marker)) {
        out.add(marker);
      }
    }
    return List.copyOf(out);
  }

  private static String normalizeModuleMarker(final String marker) {
    final String id = marker.startsWith(UNSUPPORTED_MODULE_PREFIX)
        ? marker.substring(UNSUPPORTED_MODULE_PREFIX.length()).strip()
        : marker;
    if (isBlank(id)) {
      throw new IllegalStateException("Blank module id in \"modules\": " + marker);
    }
    try {
      LangTrainerModuleId.valueOf(id);
    } catch (final IllegalArgumentException ex) {
      throw new IllegalStateException("Unknown module id in \"modules\": " + marker, ex);
    }
    return marker.startsWith(UNSUPPORTED_MODULE_PREFIX) ? UNSUPPORTED_MODULE_PREFIX + id : id;
  }
}
