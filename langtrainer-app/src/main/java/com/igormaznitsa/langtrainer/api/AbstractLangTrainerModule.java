package com.igormaznitsa.langtrainer.api;

import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;

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
}
