package com.igormaznitsa.langtrainer.engine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.io.IOException;
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
    try (InputStream indexStream =
             openClasspathStreamOrThrow(anchor, indexResourcePath)) {
      return sortedDialogDefinitionsFromIndex(
          anchor,
          module,
          readUtf8(indexStream),
          indexResourcePath,
          failureMessage);
    } catch (final Exception ex) {
      throw wrapUnlessAlreadyIllegalState(ex, failureMessage);
    }
  }

  private static List<DialogDefinition> sortedDialogDefinitionsFromIndex(
      final Class<?> anchor,
      final AbstractLangTrainerModule module,
      final String indexText,
      final String indexResourcePath,
      final String perEntryFailureContext) {
    final JsonObject root =
        requireIndexWithResourcesArray(
            GSON.fromJson(indexText, JsonObject.class),
            indexResourcePath);
    return root.get("resources")
        .getAsJsonArray()
        .asList()
        .stream()
        .map(JsonElement::getAsJsonObject)
        .filter(
            entry ->
                isIndexEntryOnClasspath(entry)
                    && module.isResourceAllowed(entry))
        .map(
            entry ->
                loadDialogFromResourcePath(
                    anchor, entry.get("resource").getAsString(), perEntryFailureContext))
        .sorted(Comparator.comparing(DialogDefinition::menuName))
        .toList();
  }

  private static InputStream openClasspathStreamOrThrow(final Class<?> anchor, final String path)
      throws IllegalStateException {
    final InputStream stream = anchor.getResourceAsStream(path);
    if (stream == null) {
      throw new IllegalStateException("Resource not found: " + path);
    }
    return stream;
  }

  private static String readUtf8(final InputStream stream) throws IOException {
    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
  }

  private static JsonObject requireIndexWithResourcesArray(
      final JsonObject root, final String indexResourcePath) {
    if (root == null) {
      throw new IllegalStateException("Invalid index JSON: " + indexResourcePath);
    }
    if (!root.has("resources") || !root.get("resources").isJsonArray()) {
      throw new IllegalStateException(
          "index.json must contain a 'resources' array: " + indexResourcePath);
    }
    return root;
  }

  private static boolean isIndexEntryOnClasspath(final JsonObject entry) {
    if (!entry.has("resource") || !entry.get("resource").isJsonPrimitive()) {
      return false;
    }
    return RESOURCE_AT_COMMON_JSONS.matcher(entry.get("resource").getAsString()).matches();
  }

  private static DialogDefinition loadDialogFromResourcePath(
      final Class<?> anchor, final String resourcePath, final String loadFailureContext) {
    if (resourcePath == null) {
      throw new IllegalStateException("Missing resource path (" + loadFailureContext + ")");
    }
    final String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    try (InputStream stream =
             openClasspathStreamOrThrow(anchor, normalized)) {
      return LangResourceJson.parse(
          new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (final Exception ex) {
      throw wrapUnlessAlreadyIllegalState(
          ex, "Can't load " + normalized);
    }
  }

  private static IllegalStateException wrapUnlessAlreadyIllegalState(
      final Exception ex, final String message) {
    if (ex instanceof final IllegalStateException ise) {
      return ise;
    }
    return new IllegalStateException(message, ex);
  }
}
