package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

public final class VirtualKeyboardWindow {

  private final JDialog dialog;
  private final Consumer<Character> charConsumer;
  private final Runnable onHide;
  private final List<KeyboardLanguage> languages;
  private final JButton languageButton;
  private final JButton shiftButton;
  private final JPanel keysPanel;
  private KeyboardLanguage currentLanguage;
  private boolean shiftEnabled;

  public VirtualKeyboardWindow(
      final Window owner,
      final List<KeyboardLanguage> supportedLanguages,
      final Consumer<Character> charConsumer,
      final Runnable onHide) {
    this.dialog = new JDialog(owner, "Virtual Keyboard");
    this.charConsumer = charConsumer;
    this.onHide = onHide;
    this.languages = KeyboardLanguage.normalize(supportedLanguages);
    this.currentLanguage = this.languages.get(0);
    this.languageButton = new JButton();
    this.shiftButton = new JButton();
    this.keysPanel = new JPanel();
    this.shiftEnabled = false;
    this.initDialog();
  }

  public void show() {
    this.refreshLanguageButton();
    this.refreshShiftButton();
    this.rebuildKeys();
    this.dialog.setVisible(true);
  }

  public void hide() {
    this.dialog.setVisible(false);
  }

  public boolean isVisible() {
    return this.dialog.isVisible();
  }

  public void dispose() {
    this.dialog.dispose();
  }

  private void initDialog() {
    this.dialog.setModal(false);
    this.dialog.setResizable(true);
    this.dialog.setAlwaysOnTop(false);
    this.dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    this.dialog.setLayout(new BorderLayout(8, 8));
    this.dialog.setSize(660, 260);
    this.dialog.setLocationByPlatform(true);

    final JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    this.languageButton.addActionListener(event -> this.switchLanguage());
    this.shiftButton.addActionListener(event -> this.toggleShift());
    final JButton hideButton = new JButton("Hide");
    hideButton.addActionListener(event -> {
      this.hide();
      this.onHide.run();
    });
    topPanel.add(this.languageButton);
    topPanel.add(this.shiftButton);
    topPanel.add(hideButton);
    this.dialog.add(topPanel, BorderLayout.NORTH);

    this.keysPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    this.dialog.add(this.keysPanel, BorderLayout.CENTER);
    this.dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override
      public void componentHidden(final java.awt.event.ComponentEvent event) {
        VirtualKeyboardWindow.this.onHide.run();
      }
    });
  }

  private void switchLanguage() {
    this.currentLanguage = this.currentLanguage.nextIn(this.languages);
    this.refreshLanguageButton();
    this.rebuildKeys();
  }

  private void refreshLanguageButton() {
    this.languageButton.setText(this.currentLanguage.getAbbreviation());
  }

  private void refreshShiftButton() {
    final String state = this.shiftEnabled ? "ON" : "OFF";
    this.shiftButton.setText("SHIFT " + state);
  }

  private void rebuildKeys() {
    this.keysPanel.removeAll();
    final List<String> rows = this.currentLanguage.getRows();
    this.keysPanel.setLayout(new GridLayout(rows.size(), 1, 4, 4));
    for (final String row : rows) {
      this.keysPanel.add(this.makeRowPanel(row));
    }
    this.keysPanel.revalidate();
    this.keysPanel.repaint();
  }

  private JPanel makeRowPanel(final String row) {
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
    row.chars()
        .mapToObj(code -> (char) code)
        .forEach(symbol -> panel.add(this.makeCharButton(symbol)));
    return panel;
  }

  private JButton makeCharButton(final char symbol) {
    final char mapped = this.applyShift(symbol);
    final JButton button = new JButton(this.toButtonText(mapped));
    button.setFocusable(false);
    button.addActionListener(event -> this.charConsumer.accept(mapped));
    return button;
  }

  private String toButtonText(final char symbol) {
    return symbol == ' ' ? "Space" : Character.toString(symbol);
  }

  private void toggleShift() {
    this.shiftEnabled = !this.shiftEnabled;
    this.refreshShiftButton();
    this.rebuildKeys();
  }

  private char applyShift(final char symbol) {
    if (!Character.isLetter(symbol)) {
      return symbol;
    }
    return this.shiftEnabled ? Character.toUpperCase(symbol) : Character.toLowerCase(symbol);
  }
}
