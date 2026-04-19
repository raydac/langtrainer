package com.igormaznitsa.langtrainer.engine;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class ClasspathLangResourceIndex {

  private ClasspathLangResourceIndex() {
  }

  public static List<DialogDefinition> loadAll(
      final Class<?> anchor,
      final String indexResourcePath,
      final Pattern resourcePathPattern,
      final String failureMessage) {
    final List<DialogDefinition> result = new ArrayList<>();
    try (InputStream indexStream = anchor.getResourceAsStream(indexResourcePath)) {
      if (indexStream == null) {
        throw new IllegalStateException("Resource not found: " + indexResourcePath);
      }
      final String index = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
      index.lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .filter(line -> resourcePathPattern.matcher(line).matches())
          .map(line -> loadOne(anchor, line))
          .sorted(Comparator.comparing(DialogDefinition::menuName))
          .forEach(result::add);
    } catch (final Exception ex) {
      throw new IllegalStateException(failureMessage, ex);
    }
    return result;
  }

  private static DialogDefinition loadOne(final Class<?> anchor, final String resourcePath) {
    try (InputStream stream = anchor.getResourceAsStream("/" + resourcePath)) {
      if (stream == null) {
        throw new IllegalStateException("Resource not found: /" + resourcePath);
      }
      final String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return LangResourceJson.parse(text);
    } catch (final Exception ex) {
      throw new IllegalStateException("Can't load " + resourcePath, ex);
    }
  }
}
