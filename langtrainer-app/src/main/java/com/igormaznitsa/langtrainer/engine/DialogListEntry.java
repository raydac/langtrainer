package com.igormaznitsa.langtrainer.engine;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;
import javax.swing.DefaultListModel;
import javax.swing.ListModel;

public sealed interface DialogListEntry
    permits DialogListEntry.DialogResourceRow, DialogListEntry.DialogFolderRow {

  static DialogResourceRow fileResourceRow(final DialogDefinition definition) {
    return new DialogResourceRow(definition, ResourceSource.FILE, 0, definition.menuName());
  }

  static DialogListEntry resource(
      final DialogDefinition definition, final boolean fromExternalFile) {
    return new DialogResourceRow(definition, sourceOf(fromExternalFile), 0, definition.menuName());
  }

  static DialogListEntry resource(
      final DialogDefinition definition,
      final boolean fromExternalFile,
      final int indentLevel) {
    return new DialogResourceRow(
        definition, sourceOf(fromExternalFile), indentLevel, definition.menuName());
  }

  static DialogListEntry resource(
      final DialogDefinition definition,
      final ResourceSource source,
      final int indentLevel) {
    return new DialogResourceRow(definition, source, indentLevel, definition.menuName());
  }

  static DialogListEntry resource(
      final DialogDefinition definition,
      final ResourceSource source,
      final int indentLevel,
      final String displayTitle) {
    return new DialogResourceRow(definition, source, indentLevel, displayTitle);
  }

  static DialogListEntry folder(
      final String title,
      final int depth,
      final String pathKey,
      final boolean expanded) {
    return new DialogFolderRow(title, depth, pathKey, expanded);
  }

  static int indexOfFirstResourceRow(final ListModel<DialogListEntry> model) {
    for (int i = 0; i < model.getSize(); i++) {
      if (model.getElementAt(i) instanceof DialogResourceRow) {
        return i;
      }
    }
    return -1;
  }

  static int indexOfFolderPathKey(
      final ListModel<DialogListEntry> model, final String pathKey) {
    for (int i = 0; i < model.getSize(); i++) {
      final DialogListEntry entry = model.getElementAt(i);
      if (entry instanceof final DialogFolderRow folder && pathKey.equals(folder.pathKey())) {
        return i;
      }
    }
    return -1;
  }

  static int indexOfFileResourceMenuName(
      final ListModel<DialogListEntry> model, final String menuName) {
    for (int i = 0; i < model.getSize(); i++) {
      final DialogListEntry entry = model.getElementAt(i);
      if (entry instanceof final DialogResourceRow row
          && row.source() == ResourceSource.FILE
          && Objects.equals(menuName, row.definition().menuName())) {
        return i;
      }
    }
    return -1;
  }

  static void mergeFileResourceRow(
      final List<DialogResourceRow> fileRows, final DialogResourceRow row) {
    final String title = row.definition().menuName();
    for (int i = 0; i < fileRows.size(); i++) {
      if (Objects.equals(fileRows.get(i).definition().menuName(), title)) {
        fileRows.set(i, row);
        return;
      }
    }
    fileRows.add(row);
  }

  static int addOrReplaceByMenuTitle(
      final DefaultListModel<DialogListEntry> model, final DialogListEntry newEntry) {
    if (!(newEntry instanceof final DialogResourceRow resourceRow)) {
      model.addElement(newEntry);
      return model.getSize() - 1;
    }
    final String title = resourceRow.definition().menuName();
    for (int i = 0; i < model.getSize(); i++) {
      final DialogListEntry at = model.getElementAt(i);
      if (at instanceof final DialogResourceRow existing
          && Objects.equals(existing.definition().menuName(), title)) {
        model.set(i, newEntry);
        return i;
      }
    }
    model.addElement(newEntry);
    return model.getSize() - 1;
  }

  private static ResourceSource sourceOf(final boolean fromExternalFile) {
    return fromExternalFile ? ResourceSource.EXTERNAL : ResourceSource.EMBEDDED;
  }

  enum ResourceSource {
    EMBEDDED,
    EXTERNAL,
    FILE
  }

  record DialogFolderRow(String title, int depth, String pathKey, boolean expanded)
      implements DialogListEntry {
  }

  record DialogResourceRow(
      DialogDefinition definition, ResourceSource source, int indentLevel, String displayTitle)
      implements DialogListEntry {

    public DialogResourceRow {
      requireNonNull(definition, "definition must not be null");
      requireNonNull(source, "source must not be null");
      displayTitle = displayTitle == null ? definition.menuName() : displayTitle;
    }
  }
}
