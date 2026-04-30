package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import com.igormaznitsa.langtrainer.ui.LangTrainerFonts;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public final class VirtualKeyboardWindow {

  private static final Color DIGIT_KEY_COLOR = new Color(141, 244, 255);
  private static final Color LETTER_KEY_COLOR = new Color(191, 248, 191);
  private static final Color SPECIAL_KEY_COLOR = new Color(255, 224, 179);
  private static final Color KEY_TEXT_COLOR = Color.BLACK;
  private static final Font KEY_FONT = LangTrainerFonts.MONO_NL_SEMI_BOLD.atPoints(18.0f);
  private static final int KEY_MIN_WIDTH = 52;
  private static final int KEY_HEIGHT = 52;
  private static final int KEY_HORIZONTAL_PADDING = 12;
  private static final int KEY_LABEL_MIN_WIDTH = 72;
  private static final int SPACE_KEY_MIN_WIDTH = 180;
  private static KeyboardLanguage lastSelectedLanguage = KeyboardLanguage.ENG;
  private static Point lastWindowLocation;

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
    this.currentLanguage = this.resolveInitialLanguage(this.languages);
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
    this.dialog.pack();
    this.applyLastWindowLocation();
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
    this.dialog.setResizable(false);
    this.dialog.setAlwaysOnTop(false);
    this.dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    this.dialog.setLayout(new BorderLayout(8, 8));
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
    final JScrollPane keysScrollPane = new JScrollPane(this.keysPanel);
    keysScrollPane.setBorder(BorderFactory.createEmptyBorder());
    keysScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    keysScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    this.dialog.add(keysScrollPane, BorderLayout.CENTER);
    this.dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override
      public void componentHidden(final java.awt.event.ComponentEvent event) {
        VirtualKeyboardWindow.this.rememberCurrentWindowLocation();
        VirtualKeyboardWindow.this.onHide.run();
      }

      @Override
      public void componentMoved(final java.awt.event.ComponentEvent event) {
        VirtualKeyboardWindow.this.rememberCurrentWindowLocation();
      }
    });
  }

  private void switchLanguage() {
    this.currentLanguage = this.currentLanguage.nextIn(this.languages);
    lastSelectedLanguage = this.currentLanguage;
    this.refreshLanguageButton();
    this.rebuildKeys();
    this.dialog.pack();
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
    final List<Character> symbols = row.chars().mapToObj(code -> (char) code).toList();
    if (symbols.contains(' ')) {
      return this.makeBottomRowPanel(symbols);
    }
    final JPanel panel = new JPanel(new GridLayout(1, symbols.size(), 4, 2));
    symbols.forEach(symbol -> panel.add(this.makeCharButton(symbol)));
    return panel;
  }

  private JPanel makeBottomRowPanel(final List<Character> symbols) {
    final JPanel rowPanel = new JPanel(new BorderLayout(4, 0));
    final List<Character> symbolsWithoutSpace = new ArrayList<>(symbols);
    symbolsWithoutSpace.remove(Character.valueOf(' '));
    final int middleIndex = symbolsWithoutSpace.size() / 2;

    final JPanel leftPanel = new JPanel(new GridLayout(1, Math.max(1, middleIndex), 4, 2));
    symbolsWithoutSpace.subList(0, middleIndex)
        .forEach(symbol -> leftPanel.add(this.makeCharButton(symbol)));

    final JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(this.makeCharButton(' '), BorderLayout.CENTER);

    final int rightSize = symbolsWithoutSpace.size() - middleIndex;
    final JPanel rightPanel = new JPanel(new GridLayout(1, Math.max(1, rightSize), 4, 2));
    symbolsWithoutSpace.subList(middleIndex, symbolsWithoutSpace.size())
        .forEach(symbol -> rightPanel.add(this.makeCharButton(symbol)));

    rowPanel.add(leftPanel, BorderLayout.WEST);
    rowPanel.add(centerPanel, BorderLayout.CENTER);
    rowPanel.add(rightPanel, BorderLayout.EAST);
    return rowPanel;
  }

  private JButton makeCharButton(final char symbol) {
    final char mapped = this.applyShift(symbol);
    final JButton button = new JButton(this.toButtonText(mapped));
    this.styleKeyButton(button, mapped);
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
    this.dialog.pack();
  }

  private char applyShift(final char symbol) {
    if (!Character.isLetter(symbol)) {
      return symbol;
    }
    return this.shiftEnabled ? Character.toUpperCase(symbol) : Character.toLowerCase(symbol);
  }

  private void styleKeyButton(final JButton button, final char symbol) {
    button.setFont(KEY_FONT);
    if (symbol == ' ') {
      this.applySpaceButtonSize(button);
    } else {
      final Dimension buttonSize = this.resolveKeySize(button);
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setMaximumSize(buttonSize);
    }
    button.setForeground(KEY_TEXT_COLOR);
    button.setBackground(this.resolveKeyBackground(symbol));
    button.setOpaque(true);
    button.setBorderPainted(true);
    button.setContentAreaFilled(true);
  }

  private Dimension resolveKeySize(final JButton button) {
    final Insets insets = button.getInsets();
    final int textWidth = button.getFontMetrics(button.getFont()).stringWidth(button.getText());
    final int paddedTextWidth = textWidth + insets.left + insets.right + KEY_HORIZONTAL_PADDING;
    final int minLabelWidth = button.getText().length() > 1 ? KEY_LABEL_MIN_WIDTH : KEY_MIN_WIDTH;
    final int width = Math.max(minLabelWidth, paddedTextWidth);
    return new Dimension(width, KEY_HEIGHT);
  }

  private void applySpaceButtonSize(final JButton button) {
    final Dimension minSize = new Dimension(SPACE_KEY_MIN_WIDTH, KEY_HEIGHT);
    final Dimension prefSize = new Dimension(SPACE_KEY_MIN_WIDTH, KEY_HEIGHT);
    final Dimension maxSize = new Dimension(Integer.MAX_VALUE, KEY_HEIGHT);
    button.setMinimumSize(minSize);
    button.setPreferredSize(prefSize);
    button.setMaximumSize(maxSize);
  }

  private Color resolveKeyBackground(final char symbol) {
    if (Character.isDigit(symbol)) {
      return DIGIT_KEY_COLOR;
    }
    if (Character.isLetter(symbol)) {
      return LETTER_KEY_COLOR;
    }
    return SPECIAL_KEY_COLOR;
  }

  private KeyboardLanguage resolveInitialLanguage(final List<KeyboardLanguage> availableLanguages) {
    if (availableLanguages.contains(lastSelectedLanguage)) {
      return lastSelectedLanguage;
    }
    final KeyboardLanguage fallback = availableLanguages.get(0);
    lastSelectedLanguage = fallback;
    return fallback;
  }

  private void rememberCurrentWindowLocation() {
    lastWindowLocation = this.dialog.getLocation();
  }

  private void applyLastWindowLocation() {
    if (lastWindowLocation != null) {
      this.dialog.setLocation(lastWindowLocation);
    }
  }
}
