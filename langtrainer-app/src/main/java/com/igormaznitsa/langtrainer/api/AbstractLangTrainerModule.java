package com.igormaznitsa.langtrainer.api;

import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;

public abstract class AbstractLangTrainerModule {

  public abstract String getName();

  public abstract String getDescription();

  public abstract Icon getImage();

  public abstract JComponent createControlForm();

  public List<KeyboardLanguage> getSupportedLanguages() {
    return List.of(KeyboardLanguage.ENG);
  }

  /**
   * When {@code false}, the main window omits the virtual-keyboard toolbar control for this
   * module (and opening the keyboard via client property is ignored).
   */
  public boolean isVirtualKeyboardToolbarButtonShown() {
    return true;
  }

  /**
   * Future image-driven modules can return {@code true} so resource activation validates embedded
   * line images before the module starts.
   */
  public boolean requiresResourceImages() {
    return false;
  }

  public void onCharClick(final char symbol) {
  }

  public void onActivation() {
  }

  public void onClose() {
  }

  /**
   * Optional controls placed in the main window toolbar after the virtual-keyboard button and
   * before the close (exit module) control.
   */
  public void populateMainToolbar(final JPanel eastToolbar) {
  }

  /**
   * Whether a bundled classpath JSON resource may appear in this module’s resource list. Restriction
   * comes from the optional {@code "modules"} field on the resource JSON ({@link DialogDefinition}).
   */
  public boolean isResourceAllowed(final DialogDefinition resourceDefinition) {
    return true;
  }
}
