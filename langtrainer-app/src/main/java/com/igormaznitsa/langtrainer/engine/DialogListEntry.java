package com.igormaznitsa.langtrainer.engine;

import javax.swing.DefaultListModel;

/**
 * Dialog shown in a module list: bundled resources vs a file opened by the user.
 */
public record DialogListEntry(DialogDefinition definition, boolean fromExternalFile) {

  /**
   * If the model already has an entry with the same {@link DialogDefinition#menuName()} as {@code
   * newEntry}, that element is replaced (keeping list order). Otherwise the entry is appended.
   *
   * @return list index of the new or updated row
   */
  public static int addOrReplaceByMenuTitle(
      final DefaultListModel<DialogListEntry> model, final DialogListEntry newEntry) {
    final String title = newEntry.definition().menuName();
    for (int i = 0; i < model.getSize(); i++) {
      if (java.util.Objects.equals(model.getElementAt(i).definition().menuName(), title)) {
        model.set(i, newEntry);
        return i;
      }
    }
    model.addElement(newEntry);
    return model.getSize() - 1;
  }
}
