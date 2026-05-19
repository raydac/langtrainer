package com.igormaznitsa.langtrainer.engine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    final List<RootSlot> rootOrder = new ArrayList<>();
    final Map<String, FolderBuilder> rootFoldersByKey = new LinkedHashMap<>();
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
      if (!isLeafNode(entry) || !isIndexEntryOnClasspath(entry)) {
        continue;
      }
      final DialogDefinition loaded =
          loadDialogFromResourcePath(
              anchor, entry.get("resource").getAsString(), perEntryFailureContext);
      if (!module.isResourceAllowed(loaded)) {
        continue;
      }
      final ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf leaf =
          new ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf(loaded);
      insertLeafByMenuPath(
          rootOrder, rootFoldersByKey, leaf, ResourceMenuPath.parseSegments(loaded.path()));
    }
    return new ClasspathResourceIndexTree(materializeRootOrder(rootOrder));
  }

  private static List<ClasspathResourceIndexTree.IndexedClasspathNode> materializeRootOrder(
      final List<RootSlot> rootOrder) {
    final List<ClasspathResourceIndexTree.IndexedClasspathNode> roots = new ArrayList<>();
    for (final RootSlot slot : rootOrder) {
      if (slot.leaf() != null) {
        roots.add(slot.leaf());
      } else {
        roots.add(slot.folder().toIndexedFolder());
      }
    }
    return List.copyOf(roots);
  }

  private static void insertLeafByMenuPath(
      final List<RootSlot> rootOrder,
      final Map<String, FolderBuilder> rootFoldersByKey,
      final ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf leaf,
      final List<String> pathSegments) {
    if (pathSegments.isEmpty()) {
      rootOrder.add(RootSlot.leaf(leaf));
      return;
    }
    final String rootSegment = pathSegments.get(0);
    final String rootKey = ResourceMenuPath.canonicalSegmentKey(rootSegment);
    FolderBuilder folder = rootFoldersByKey.get(rootKey);
    if (folder == null) {
      folder =
          new FolderBuilder(
              ResourceMenuPath.displaySegment(rootSegment),
              ClasspathResourceIndexTree.childFolderPathKey("", rootSegment));
      rootFoldersByKey.put(rootKey, folder);
      rootOrder.add(RootSlot.folder(folder));
    }
    for (int i = 1; i < pathSegments.size(); i++) {
      folder = folder.childFolder(pathSegments.get(i));
    }
    folder.addLeaf(leaf);
  }

  private record RootSlot(
      ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf leaf,
      FolderBuilder folder) {

    static RootSlot leaf(
        final ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf leaf) {
      return new RootSlot(leaf, null);
    }

    static RootSlot folder(final FolderBuilder folder) {
      return new RootSlot(null, folder);
    }
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

  private static final class FolderBuilder {

    private final String displayName;
    private final String pathKey;
    private final List<ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf> leaves =
        new ArrayList<>();
    private final Map<String, FolderBuilder> childFoldersByKey = new LinkedHashMap<>();

    private FolderBuilder(final String displayName, final String pathKey) {
      this.displayName = displayName;
      this.pathKey = pathKey;
    }

    private void addLeaf(
        final ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf leaf) {
      this.leaves.add(leaf);
    }

    private FolderBuilder childFolder(final String segment) {
      final String key = ResourceMenuPath.canonicalSegmentKey(segment);
      return this.childFoldersByKey.computeIfAbsent(
          key,
          ignored ->
              new FolderBuilder(
                  ResourceMenuPath.displaySegment(segment),
                  ClasspathResourceIndexTree.childFolderPathKey(this.pathKey, segment)));
    }

    private ClasspathResourceIndexTree.IndexedClasspathNode.IndexedFolder toIndexedFolder() {
      final List<ClasspathResourceIndexTree.IndexedClasspathNode> children = new ArrayList<>();
      for (final ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf leaf : this.leaves) {
        children.add(leaf);
      }
      for (final FolderBuilder sub : this.childFoldersByKey.values()) {
        children.add(sub.toIndexedFolder());
      }
      return new ClasspathResourceIndexTree.IndexedClasspathNode.IndexedFolder(
          this.displayName, this.pathKey, List.copyOf(children));
    }
  }
}
