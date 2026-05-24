package com.igormaznitsa.langtrainer.engine;

import java.util.List;
import java.util.Set;
import javax.swing.DefaultListModel;

public record ClasspathResourceIndexTree(List<IndexedClasspathNode> roots) {

  private static final char PATH_SEP = '\u0001';

  public static ClasspathResourceIndexTree empty() {
    return new ClasspathResourceIndexTree(List.of());
  }

  public static String childFolderPathKey(final String parentPathKey, final String segmentName) {
    final String key = ResourceMenuPath.canonicalSegmentKey(segmentName);
    return parentPathKey.isEmpty() ? key : parentPathKey + PATH_SEP + key;
  }

  public void materializeInto(
      final DefaultListModel<DialogListEntry> model, final Set<String> expandedFolderPathKeys) {
    this.materializeInto(model, expandedFolderPathKeys, false, "");
  }

  public void materializeInto(
      final DefaultListModel<DialogListEntry> model,
      final Set<String> expandedFolderPathKeys,
      final boolean fromExternalFile,
      final String pathKeyPrefix) {
    for (final IndexedClasspathNode root : this.roots) {
      this.appendMaterialized(
          root, 0, expandedFolderPathKeys, model, fromExternalFile, pathKeyPrefix);
    }
  }

  private void appendMaterialized(
      final IndexedClasspathNode node,
      final int visibleDepth,
      final Set<String> expandedFolderPathKeys,
      final DefaultListModel<DialogListEntry> model,
      final boolean fromExternalFile,
      final String pathKeyPrefix) {
    if (node instanceof final IndexedClasspathNode.IndexedLeaf leaf) {
      model.addElement(DialogListEntry.resource(leaf.definition(), fromExternalFile, visibleDepth));
      return;
    }
    if (node instanceof final IndexedClasspathNode.IndexedFolder folder) {
      final String pathKey = pathKeyPrefix + folder.pathKey();
      final boolean expanded = expandedFolderPathKeys.contains(pathKey);
      model.addElement(
          DialogListEntry.folder(folder.name(), visibleDepth, pathKey, expanded));
      if (expanded) {
        for (final IndexedClasspathNode child : folder.children()) {
          this.appendMaterialized(
              child,
              visibleDepth + 1,
              expandedFolderPathKeys,
              model,
              fromExternalFile,
              pathKeyPrefix);
        }
      }
    }
  }

  public sealed interface IndexedClasspathNode
      permits IndexedClasspathNode.IndexedLeaf, IndexedClasspathNode.IndexedFolder {

    record IndexedLeaf(DialogDefinition definition) implements IndexedClasspathNode {
    }

    record IndexedFolder(String name, String pathKey, List<IndexedClasspathNode> children)
        implements IndexedClasspathNode {
    }
  }
}
