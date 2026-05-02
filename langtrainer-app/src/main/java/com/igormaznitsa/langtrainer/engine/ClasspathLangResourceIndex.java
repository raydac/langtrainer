package com.igormaznitsa.langtrainer.engine;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ClasspathLangResourceIndex {

  private static final String SHARED_INDEX = "/common/jsons/index.json";
  private static final Pattern RESOURCE_AT_COMMON_JSONS =
      Pattern.compile("^/common/jsons/[^/]+\\.json$");
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
    final List<ClasspathResourceIndexTree.IndexedClasspathNode> roots = new ArrayList<>();
    for (final JsonElement el : root.get("resources").getAsJsonArray()) {
      if (!el.isJsonObject()) {
        continue;
      }
      final ClasspathResourceIndexTree.IndexedClasspathNode built =
          tryBuildTreeNode(
              anchor,
              module,
              el.getAsJsonObject(),
              "",
              indexResourcePath,
              perEntryFailureContext);
      if (built != null) {
        roots.add(built);
      }
    }
    return new ClasspathResourceIndexTree(List.copyOf(roots));
  }

  private static ClasspathResourceIndexTree.IndexedClasspathNode tryBuildTreeNode(
      final Class<?> anchor,
      final AbstractLangTrainerModule module,
      final JsonObject node,
      final String parentPathKey,
      final String indexResourcePath,
      final String perEntryFailureContext) {
    if (isFolderNode(node)) {
      if (node.has("resource")) {
        throw new IllegalStateException(
            "index.json folder must not contain \"resource\" alongside \"children\" ("
                + indexResourcePath
                + "): "
                + node);
      }
      if (!node.has("name") || !node.get("name").isJsonPrimitive()) {
        throw new IllegalStateException(
            "index.json folder requires string \"name\" (" + indexResourcePath + "): " + node);
      }
      if (!subtreeHasVisibleResource(anchor, module, node)) {
        return null;
      }
      final String name = node.get("name").getAsString();
      final String pathKey = ClasspathResourceIndexTree.childFolderPathKey(parentPathKey, name);
      final List<ClasspathResourceIndexTree.IndexedClasspathNode> childNodes = new ArrayList<>();
      for (final JsonElement child : node.getAsJsonArray("children")) {
        if (!child.isJsonObject()) {
          continue;
        }
        final ClasspathResourceIndexTree.IndexedClasspathNode built =
            tryBuildTreeNode(
                anchor,
                module,
                child.getAsJsonObject(),
                pathKey,
                indexResourcePath,
                perEntryFailureContext);
        if (built != null) {
          childNodes.add(built);
        }
      }
      if (childNodes.isEmpty()) {
        return null;
      }
      return new ClasspathResourceIndexTree.IndexedClasspathNode.IndexedFolder(
          name, pathKey, List.copyOf(childNodes));
    }
    if (isLeafNode(node)) {
      if (!isIndexEntryOnClasspath(node) || !module.isResourceAllowed(node)) {
        return null;
      }
      final DialogDefinition loaded =
          loadDialogFromResourcePath(
              anchor, node.get("resource").getAsString(), perEntryFailureContext);
      return new ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf(loaded);
    }
    throw new IllegalStateException(
        "index.json entry must be a leaf with \"resource\" or a folder with \"name\" and"
            + " \"children\" ("
            + indexResourcePath
            + "): "
            + node);
  }

  private static boolean isFolderNode(final JsonObject node) {
    return node.has("children") && node.get("children").isJsonArray();
  }

  private static boolean isLeafNode(final JsonObject node) {
    return node.has("resource") && node.get("resource").isJsonPrimitive();
  }

  private static boolean subtreeHasVisibleResource(
      final Class<?> anchor, final AbstractLangTrainerModule module, final JsonObject folderNode) {
    final JsonArray children = folderNode.getAsJsonArray("children");
    for (final JsonElement el : children) {
      if (!el.isJsonObject()) {
        continue;
      }
      final JsonObject ch = el.getAsJsonObject();
      if (isFolderNode(ch)) {
        if (subtreeHasVisibleResource(anchor, module, ch)) {
          return true;
        }
      } else if (isLeafNode(ch)
          && isIndexEntryOnClasspath(ch)
          && module.isResourceAllowed(ch)) {
        return true;
      }
    }
    return false;
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
    try (InputStream stream = openClasspathStreamOrThrow(anchor, normalized)) {
      return LangResourceJson.parse(
          new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (final Exception ex) {
      throw wrapUnlessAlreadyIllegalState(ex, "Can't load " + normalized);
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
