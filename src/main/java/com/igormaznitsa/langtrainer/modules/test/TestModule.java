package com.igormaznitsa.langtrainer.modules.test;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import com.igormaznitsa.langtrainer.engine.ImageResourceLoader;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public final class TestModule extends AbstractLangTrainerModule {

  private final JLabel textLabel = new JLabel("TEST", SwingConstants.CENTER);

  @Override
  public String getName() {
    String result;
    result = "TEST";
    return result;
  }

  @Override
  public String getDescription() {
    String result;
    result = "Technical test module";
    return result;
  }

  @Override
  public Icon getImage() {
    Icon result;
    result = ImageResourceLoader.loadIcon("/images/module-test.svg", 128, 128);
    return result;
  }

  @Override
  public JComponent createControlForm() {
    final JPanel panel = new JPanel(new BorderLayout());
    this.textLabel.setFont(this.textLabel.getFont().deriveFont(Font.BOLD, 56.0f));
    panel.add(this.textLabel, BorderLayout.CENTER);
    return panel;
  }

  @Override
  public List<KeyboardLanguage> getSupportedLanguages() {
    List<KeyboardLanguage> result;
    result = List.of(KeyboardLanguage.ENG, KeyboardLanguage.RUS, KeyboardLanguage.EST);
    return result;
  }

  @Override
  public void onCharClick(final char symbol) {
    this.textLabel.setText("TEST " + symbol);
  }
}
