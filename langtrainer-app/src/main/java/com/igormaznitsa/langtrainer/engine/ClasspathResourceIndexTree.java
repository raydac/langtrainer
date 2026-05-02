package com.igormaznitsa.langtrainer.engine;

import java.util.List;
import java.util.Set;
import javax.swing.DefaultListModel;

public record ClasspathResourceIndexTree(List<IndexedClasspathNode> roots) {

  private static final char PATH_SEP = '\u0001';

  public static String childFolderPathKey(final String parentPathKey, final String segmentName) {
    return parentPathKey.isEmpty()
        ? segmentName
        : parentPathKey + PATH_SEP + segmentName;
  }

  public void materializeInto(
      final DefaultListModel<DialogListEntry> model, final Set<String> expandedFolderPathKeys) {
    for (final IndexedClasspathNode root : this.roots) {
      this.appendMaterialized(root, 0, expandedFolderPathKeys, model);
    }
  }

  private void appendMaterialized(
      final IndexedClasspathNode node,
      final int visibleDepth,
      final Set<String> expandedFolderPathKeys,
      final DefaultListModel<DialogListEntry> model) {
    if (node instanceof final IndexedClasspathNode.IndexedLeaf leaf) {
      model.addElement(DialogListEntry.resource(leaf.definition(), false, visibleDepth));
      return;
    }
    if (node instanceof final IndexedClasspathNode.IndexedFolder folder) {
      final boolean expanded = expandedFolderPathKeys.contains(folder.pathKey());
      model.addElement(
          DialogListEntry.folder(folder.name(), visibleDepth, folder.pathKey(), expanded));
      if (expanded) {
        for (final IndexedClasspathNode child : folder.children()) {
          this.appendMaterialized(child, visibleDepth + 1, expandedFolderPathKeys, model);
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
