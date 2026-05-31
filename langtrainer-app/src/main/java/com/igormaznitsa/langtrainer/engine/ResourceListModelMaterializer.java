package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.engine.ClasspathResourceIndexTree.IndexedClasspathNode;
import com.igormaznitsa.langtrainer.engine.DialogListEntry.ResourceSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultListModel;

public final class ResourceListModelMaterializer {

  private ResourceListModelMaterializer() {
  }

  public static void materializeMergedTrees(
      final DefaultListModel<DialogListEntry> model,
      final Set<String> expandedFolderPathKeys,
      final ClasspathResourceIndexTree embeddedTree,
      final ClasspathResourceIndexTree externalTree) {
    model.clear();
    final FolderBuilder root = new FolderBuilder("", "");
    root.addTree(embeddedTree, ResourceSource.EMBEDDED);
    root.addTree(externalTree, ResourceSource.EXTERNAL);
    root.materializeChildren(model, expandedFolderPathKeys, 0);
  }

  private sealed interface Slot permits LeafSlot, FolderSlot {
  }

  private record LeafSlot(DialogDefinition definition, ResourceSource source) implements Slot {
  }

  private record FolderSlot(FolderBuilder folder) implements Slot {
  }

  private static final class FolderBuilder {

    private final String title;
    private final String pathKey;
    private final List<Slot> slots = new ArrayList<>();
    private final Map<String, FolderBuilder> foldersByPathKey = new LinkedHashMap<>();

    private FolderBuilder(final String title, final String pathKey) {
      this.title = title;
      this.pathKey = pathKey;
    }

    private static String resourceTitle(final DialogDefinition definition) {
      return definition.menuName() == null ? "" : definition.menuName();
    }

    private void addTree(final ClasspathResourceIndexTree tree, final ResourceSource source) {
      for (final IndexedClasspathNode node : tree.roots()) {
        this.addNode(node, source);
      }
    }

    private void addNode(final IndexedClasspathNode node, final ResourceSource source) {
      if (node instanceof final IndexedClasspathNode.IndexedLeaf leaf) {
        this.slots.add(new LeafSlot(leaf.definition(), source));
        return;
      }
      if (node instanceof final IndexedClasspathNode.IndexedFolder folder) {
        this.addFolder(folder).addChildren(folder.children(), source);
      }
    }

    private FolderBuilder addFolder(final IndexedClasspathNode.IndexedFolder folder) {
      FolderBuilder builder = this.foldersByPathKey.get(folder.pathKey());
      if (builder == null) {
        builder = new FolderBuilder(folder.name(), folder.pathKey());
        this.foldersByPathKey.put(folder.pathKey(), builder);
        this.slots.add(new FolderSlot(builder));
      }
      return builder;
    }

    private void addChildren(final List<IndexedClasspathNode> children,
                             final ResourceSource source) {
      children.forEach(child -> this.addNode(child, source));
    }

    private void materializeChildren(
        final DefaultListModel<DialogListEntry> model,
        final Set<String> expandedFolderPathKeys,
        final int depth) {
      final Map<String, Integer> duplicateCounts = this.countDirectResourceTitles();
      final Map<String, Integer> duplicateOrdinals = new LinkedHashMap<>();
      for (final Slot slot : this.slots) {
        this.materializeSlot(slot, model, expandedFolderPathKeys, depth, duplicateCounts,
            duplicateOrdinals);
      }
    }

    private Map<String, Integer> countDirectResourceTitles() {
      final Map<String, Integer> result = new LinkedHashMap<>();
      for (final Slot slot : this.slots) {
        if (slot instanceof final LeafSlot leaf) {
          final String title = resourceTitle(leaf.definition());
          result.put(title, result.getOrDefault(title, 0) + 1);
        }
      }
      return result;
    }

    private void materializeSlot(
        final Slot slot,
        final DefaultListModel<DialogListEntry> model,
        final Set<String> expandedFolderPathKeys,
        final int depth,
        final Map<String, Integer> duplicateCounts,
        final Map<String, Integer> duplicateOrdinals) {
      if (slot instanceof final LeafSlot leaf) {
        model.addElement(DialogListEntry.resource(
            leaf.definition(),
            leaf.source(),
            depth,
            this.displayTitle(leaf.definition(), duplicateCounts, duplicateOrdinals)));
        return;
      }
      if (slot instanceof final FolderSlot folderSlot) {
        final FolderBuilder folder = folderSlot.folder();
        final boolean expanded = expandedFolderPathKeys.contains(folder.pathKey);
        model.addElement(DialogListEntry.folder(folder.title, depth, folder.pathKey, expanded));
        if (expanded) {
          folder.materializeChildren(model, expandedFolderPathKeys, depth + 1);
        }
      }
    }

    private String displayTitle(
        final DialogDefinition definition,
        final Map<String, Integer> duplicateCounts,
        final Map<String, Integer> duplicateOrdinals) {
      final String title = resourceTitle(definition);
      if (duplicateCounts.getOrDefault(title, 0) <= 1) {
        return title;
      }
      final int ordinal = duplicateOrdinals.getOrDefault(title, 0) + 1;
      duplicateOrdinals.put(title, ordinal);
      return "%s (%d)".formatted(title, ordinal);
    }
  }
}
