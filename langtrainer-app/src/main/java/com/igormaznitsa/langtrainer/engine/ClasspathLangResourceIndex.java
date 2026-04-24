package com.igormaznitsa.langtrainer.engine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class ClasspathLangResourceIndex {

  private static final String SHARED_INDEX = "/common/jsons/index.json";
  private static final Pattern RESOURCE_AT_COMMON_JSONS =
      Pattern.compile("^/common/jsons/[^/]+\\.json$");
  private static final Gson GSON = new Gson();

  private ClasspathLangResourceIndex() {
  }

  public static List<DialogDefinition> loadShared(
      final Class<?> anchor, final AbstractLangTrainerModule module, final String failureMessage) {
    return loadShared(anchor, module, SHARED_INDEX, failureMessage);
  }

  public static List<DialogDefinition> loadShared(
      final Class<?> anchor,
      final AbstractLangTrainerModule module,
      final String indexResourcePath,
      final String failureMessage) {
    try (InputStream indexStream = anchor.getResourceAsStream(indexResourcePath)) {
      if (indexStream == null) {
        throw new IllegalStateException("Resource not found: " + indexResourcePath);
      }
      final String text = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
      final JsonObject root = GSON.fromJson(text, JsonObject.class);
      if (root == null) {
        throw new IllegalStateException("Invalid index JSON: " + indexResourcePath);
      }
      if (!root.has("resources") || !root.get("resources").isJsonArray()) {
        throw new IllegalStateException(
            "index.json must contain a 'resources' array: " + indexResourcePath);
      }
      return root.get("resources")
          .getAsJsonArray()
          .asList()
          .stream()
          .map(JsonElement::getAsJsonObject)
          .filter(m -> isValidResourcePath(m) && module.isResourceAllowed(m))
          .map(m -> loadOne(anchor, m.get("resource").getAsString(), failureMessage))
          .sorted(Comparator.comparing(DialogDefinition::menuName))
          .toList();
    } catch (final Exception ex) {
      if (ex instanceof final IllegalStateException ise) {
        throw ise;
      }
      throw new IllegalStateException(failureMessage, ex);
    }
  }

  private static boolean isValidResourcePath(final JsonObject entry) {
    if (!entry.has("resource") || !entry.get("resource").isJsonPrimitive()) {
      return false;
    }
    return RESOURCE_AT_COMMON_JSONS.matcher(entry.get("resource").getAsString()).matches();
  }

  private static DialogDefinition loadOne(
      final Class<?> anchor, final String resourcePath, final String failureContext) {
    if (resourcePath == null) {
      throw new IllegalStateException("Missing resource path (" + failureContext + ")");
    }
    final String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    try (InputStream stream = anchor.getResourceAsStream(normalized)) {
      if (stream == null) {
        throw new IllegalStateException("Resource not found: " + normalized);
      }
      return LangResourceJson.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (final Exception ex) {
      if (ex instanceof final IllegalStateException ise) {
        throw ise;
      }
      throw new IllegalStateException("Can't load " + normalized, ex);
    }
  }
}
