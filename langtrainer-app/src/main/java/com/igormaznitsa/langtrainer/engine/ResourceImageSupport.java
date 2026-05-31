package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.awt.Component;
import javax.swing.JOptionPane;

public final class ResourceImageSupport {

  private ResourceImageSupport() {
  }

  public static boolean ensureRequiredImagesReady(
      final AbstractLangTrainerModule module,
      final Component parent,
      final DialogDefinition definition) {
    if (!module.requiresResourceImages()) {
      return true;
    }
    try {
      ImageResourceLoader.requireLoadableLineImages(definition);
      return true;
    } catch (final Exception ex) {
      JOptionPane.showMessageDialog(
          parent,
          ex.getMessage() == null ? String.valueOf(ex) : ex.getMessage(),
          "Can't load resource images",
          JOptionPane.ERROR_MESSAGE);
      return false;
    }
  }
}
