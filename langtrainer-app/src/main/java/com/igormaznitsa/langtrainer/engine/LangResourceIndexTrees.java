package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LangResourceIndexTrees {

  private LangResourceIndexTrees() {
  }

  public static ClasspathResourceIndexTree fromDefinitions(
      final AbstractLangTrainerModule module, final List<DialogDefinition> definitions) {
    final List<RootSlot> rootOrder = new ArrayList<>();
    final Map<String, FolderBuilder> rootFoldersByKey = new LinkedHashMap<>();
    for (final DialogDefinition definition : definitions) {
      if (!module.isResourceAllowed(definition)) {
        continue;
      }
      final ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf leaf =
          new ClasspathResourceIndexTree.IndexedClasspathNode.IndexedLeaf(definition);
      insertLeafByMenuPath(
          rootOrder, rootFoldersByKey, leaf, ResourceMenuPath.parseSegments(definition.path()));
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
      final List<ClasspathResourceIndexTree.IndexedClasspathNode> children =
          new ArrayList<>(this.leaves);
      for (final FolderBuilder sub : this.childFoldersByKey.values()) {
        children.add(sub.toIndexedFolder());
      }
      return new ClasspathResourceIndexTree.IndexedClasspathNode.IndexedFolder(
          this.displayName, this.pathKey, List.copyOf(children));
    }
  }
}
