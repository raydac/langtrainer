package com.igormaznitsa.langtrainer.engine;

import java.util.List;

public record ClasspathResourceIndexTree(List<IndexedClasspathNode> roots) {

  private static final char PATH_SEP = '\u0001';

  public static ClasspathResourceIndexTree empty() {
    return new ClasspathResourceIndexTree(List.of());
  }

  public static String childFolderPathKey(final String parentPathKey, final String segmentName) {
    final String key = ResourceMenuPath.canonicalSegmentKey(segmentName);
    return parentPathKey.isEmpty() ? key : parentPathKey + PATH_SEP + key;
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
