package com.igormaznitsa.langtrainer.api;

import com.google.gson.JsonObject;
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
   * Whether a shared classpath JSON entry (from {@code /common/jsons/index.json}) may appear in
   * this module’s resource list. The {@link JsonObject} is one element of the {@code resources}
   * array; it at least includes {@code "resource": "/path/…"}.
   */
  public boolean isResourceAllowed(final JsonObject resourceDescription) {
    return true;
  }
}
