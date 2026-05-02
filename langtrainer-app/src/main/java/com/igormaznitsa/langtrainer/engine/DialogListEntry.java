package com.igormaznitsa.langtrainer.engine;

import java.util.List;
import java.util.Objects;
import javax.swing.DefaultListModel;
import javax.swing.ListModel;

public sealed interface DialogListEntry
    permits DialogListEntry.DialogResourceRow, DialogListEntry.DialogFolderRow {

  static DialogResourceRow externalResourceRow(final DialogDefinition definition) {
    return new DialogResourceRow(definition, true, 0);
  }

  static DialogListEntry resource(
      final DialogDefinition definition, final boolean fromExternalFile) {
    return new DialogResourceRow(definition, fromExternalFile, 0);
  }

  static DialogListEntry resource(
      final DialogDefinition definition,
      final boolean fromExternalFile,
      final int indentLevel) {
    return new DialogResourceRow(definition, fromExternalFile, indentLevel);
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

  static int indexOfExternalResourceMenuName(
      final ListModel<DialogListEntry> model, final String menuName) {
    for (int i = 0; i < model.getSize(); i++) {
      final DialogListEntry entry = model.getElementAt(i);
      if (entry instanceof final DialogResourceRow row
          && row.fromExternalFile()
          && Objects.equals(menuName, row.definition().menuName())) {
        return i;
      }
    }
    return -1;
  }

  static void mergeExternalResourceRow(
      final List<DialogResourceRow> externalRows, final DialogResourceRow row) {
    final String title = row.definition().menuName();
    for (int i = 0; i < externalRows.size(); i++) {
      if (Objects.equals(externalRows.get(i).definition().menuName(), title)) {
        externalRows.set(i, row);
        return;
      }
    }
    externalRows.add(row);
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

  record DialogResourceRow(
      DialogDefinition definition, boolean fromExternalFile, int indentLevel)
      implements DialogListEntry {
  }

  record DialogFolderRow(String title, int depth, String pathKey, boolean expanded)
      implements DialogListEntry {
  }
}
