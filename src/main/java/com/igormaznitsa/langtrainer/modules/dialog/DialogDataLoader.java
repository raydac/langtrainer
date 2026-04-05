package com.igormaznitsa.langtrainer.modules.dialog;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DialogDataLoader {

  private static final Pattern FILE_PATTERN = Pattern.compile("^dialogs/(.+)\\.json$");
  private static final Pattern STRING_FIELD_PATTERN =
      Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern LINE_PATTERN = Pattern.compile(
      "\\{\\s*\"A\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"B\"\\s*:\\s*\"([^\"]*)\"\\s*\\}");

  private DialogDataLoader() {
  }

  public static List<DialogDefinition> loadAll() {
    final List<DialogDefinition> result = new ArrayList<>();
    try (InputStream indexStream = DialogDataLoader.class.getResourceAsStream(
        "/dialogs/index.txt")) {
      if (indexStream == null) {
        throw new IllegalStateException("Resource not found: /dialogs/index.txt");
      }
      final String index = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
      index.lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .filter(line -> FILE_PATTERN.matcher(line).matches())
          .map(DialogDataLoader::loadOne)
          .sorted(Comparator.comparing(DialogDefinition::menuName))
          .forEach(result::add);
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load dialog definitions", ex);
    }
    return result;
  }

  private static DialogDefinition loadOne(final String resourcePath) {
    final String text;
    try (InputStream stream = DialogDataLoader.class.getResourceAsStream("/" + resourcePath)) {
      if (stream == null) {
        throw new IllegalStateException("Resource not found: /" + resourcePath);
      }
      text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load " + resourcePath, ex);
    }
    return parseDialogJson(text);
  }

  public static DialogDefinition loadFromFile(final Path path) {
    final DialogDefinition result;
    try {
      final String text = Files.readString(path, StandardCharsets.UTF_8);
      result = parseDialogJson(text);
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load dialog file: " + path, ex);
    }
    return result;
  }

  public static DialogDefinition parseDialogJson(final String jsonText) {
    final String menuName = extractStringField(jsonText, "menuName");
    final String description = extractStringField(jsonText, "description");
    final String langA = extractStringField(jsonText, "langA");
    final String langB = extractStringField(jsonText, "langB");
    final List<DialogLine> lines = extractLines(jsonText);
    return new DialogDefinition(menuName, description, langA, langB, lines);
  }

  private static String extractStringField(final String text, final String fieldName) {
    final Pattern pattern = Pattern.compile(STRING_FIELD_PATTERN.pattern().formatted(fieldName));
    final Matcher matcher = pattern.matcher(text);
    if (!matcher.find()) {
      throw new IllegalStateException("Field not found: " + fieldName);
    }
    return matcher.group(1);
  }

  private static List<DialogLine> extractLines(final String text) {
    final List<DialogLine> result = new ArrayList<>();
    final Matcher matcher = LINE_PATTERN.matcher(text);
    while (matcher.find()) {
      result.add(new DialogLine(matcher.group(1), matcher.group(2)));
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("Dialog lines are empty");
    }
    return result;
  }
}
