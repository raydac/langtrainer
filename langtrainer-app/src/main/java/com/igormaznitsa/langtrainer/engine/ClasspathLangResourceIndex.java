package com.igormaznitsa.langtrainer.engine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ClasspathLangResourceIndex {

  private static final String SHARED_INDEX = "/common/jsons/index.json";
  private static final Gson GSON = new Gson();

  private ClasspathLangResourceIndex() {
  }

  public static ClasspathResourceIndexTree loadSharedTree(
      final Class<?> anchor, final AbstractLangTrainerModule module, final String failureMessage) {
    return loadSharedTree(anchor, module, SHARED_INDEX, failureMessage);
  }

  public static ClasspathResourceIndexTree loadSharedTree(
      final Class<?> anchor,
      final AbstractLangTrainerModule module,
      final String indexResourcePath,
      final String failureMessage) {
    try (InputStream indexStream = openClasspathStreamOrThrow(anchor, indexResourcePath)) {
      return classpathResourceTreeFromIndex(
          anchor,
          module,
          readUtf8(indexStream),
          indexResourcePath,
          failureMessage);
    } catch (final Exception ex) {
      throw wrapUnlessAlreadyIllegalState(ex, failureMessage);
    }
  }

  private static ClasspathResourceIndexTree classpathResourceTreeFromIndex(
      final Class<?> anchor,
      final AbstractLangTrainerModule module,
      final String indexText,
      final String indexResourcePath,
      final String perEntryFailureContext) {
    final JsonObject root =
        requireIndexWithResourcesArray(
            GSON.fromJson(indexText, JsonObject.class),
            indexResourcePath);
    final List<DialogDefinition> definitions = new ArrayList<>();
    for (final JsonElement el : root.get("resources").getAsJsonArray()) {
      if (!el.isJsonObject()) {
        continue;
      }
      final JsonObject entry = el.getAsJsonObject();
      if (entry.has("children")) {
        throw new IllegalStateException(
            "index.json no longer supports folder entries; set \"path\" on each resource JSON ("
                + indexResourcePath
                + "): "
                + entry);
      }
      if (entry.has("modules")) {
        throw new IllegalStateException(
            "index.json no longer supports \"modules\"; set \"modules\" on each resource JSON ("
                + indexResourcePath
                + "): "
                + entry);
      }
      if (!isLeafNode(entry)) {
        continue;
      }
      final DialogDefinition loaded =
          loadDialogFromResourcePath(
              anchor,
              resolveClasspathResourcePath(indexResourcePath, entry.get("resource").getAsString()),
              perEntryFailureContext);
      definitions.add(loaded);
    }
    return LangResourceIndexTrees.fromDefinitions(module, definitions);
  }

  private static boolean isLeafNode(final JsonObject node) {
    return node.has("resource") && node.get("resource").isJsonPrimitive();
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

  private static DialogDefinition loadDialogFromResourcePath(
      final Class<?> anchor, final String resourcePath, final String loadFailureContext) {
    if (resourcePath == null) {
      throw new IllegalStateException("Missing resource path (" + loadFailureContext + ")");
    }
    final String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
    try (InputStream stream = openClasspathStreamOrThrow(anchor, normalized)) {
      return LangResourceJson.parse(
          new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (final Exception ex) {
      throw wrapUnlessAlreadyIllegalState(ex, "Can't load " + normalized);
    }
  }

  private static String resolveClasspathResourcePath(
      final String indexResourcePath, final String resourcePath) {
    if (resourcePath == null || resourcePath.isBlank()) {
      throw new IllegalStateException("Missing resource path in " + indexResourcePath);
    }
    final String rawPath = resourcePath.strip();
    final String absolutePath = rawPath.startsWith("/")
        ? rawPath
        : classpathParent(indexResourcePath) + "/" + rawPath;
    final String normalized = normalizeClasspathPath(absolutePath);
    if (!normalized.endsWith(".json")) {
      throw new IllegalStateException("Resource path must point to a JSON file: " + resourcePath);
    }
    return normalized;
  }

  private static String classpathParent(final String resourcePath) {
    final String normalized = normalizeClasspathPath(resourcePath);
    final int slash = normalized.lastIndexOf('/');
    return slash <= 0 ? "" : normalized.substring(0, slash);
  }

  private static String normalizeClasspathPath(final String resourcePath) {
    final List<String> segments = new ArrayList<>();
    for (final String segment : resourcePath.split("/+")) {
      if (segment.isBlank() || ".".equals(segment)) {
        continue;
      }
      if ("..".equals(segment)) {
        if (segments.isEmpty()) {
          throw new IllegalStateException("Classpath resource path escapes root: " + resourcePath);
        }
        segments.remove(segments.size() - 1);
      } else {
        segments.add(segment);
      }
    }
    return "/" + String.join("/", segments);
  }

  private static IllegalStateException wrapUnlessAlreadyIllegalState(
      final Exception ex, final String message) {
    if (ex instanceof final IllegalStateException ise) {
      return ise;
    }
    return new IllegalStateException(message, ex);
  }

}
