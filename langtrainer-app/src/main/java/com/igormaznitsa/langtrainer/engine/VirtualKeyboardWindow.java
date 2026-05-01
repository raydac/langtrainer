package com.igormaznitsa.langtrainer.engine;

import static java.util.Comparator.comparingInt;

import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import com.igormaznitsa.langtrainer.ui.LangTrainerFonts;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

public final class VirtualKeyboardWindow {

  private static final Color BACKGROUND = Color.LIGHT_GRAY;

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
  private static final int LANGUAGE_COMBO_EXTRA_WIDTH = 48;
  private static KeyboardLanguage lastSelectedLanguage = KeyboardLanguage.ENG;
  private static Point lastWindowLocation;

  private final JDialog dialog;
  private final Consumer<Character> charConsumer;
  private final Runnable onHide;
  private final List<KeyboardLanguage> languages;
  private final JComboBox<KeyboardLanguage> languageComboBox;
  private final JToggleButton shiftButton;
  private final JPanel keysPanel;
  private KeyboardLanguage currentLanguage;

  public VirtualKeyboardWindow(
      final Window owner,
      final List<KeyboardLanguage> supportedLanguages,
      final Consumer<Character> charConsumer,
      final Runnable onHide) {
    this.dialog = new JDialog(owner, "Virtual Keyboard");
    this.dialog.setLayout(new BorderLayout(0, 0));
    this.charConsumer = charConsumer;
    this.onHide = onHide;

    this.languages = KeyboardLanguage.normalize(supportedLanguages);
    this.currentLanguage = this.resolveInitialLanguage(this.languages);
    this.languageComboBox = new JComboBox<>(this.languages.toArray(KeyboardLanguage[]::new));
    this.languageComboBox.setToolTipText("Select a language");
    this.languageComboBox.setPrototypeDisplayValue(this.findLanguageWithLongestAbbreviation());

    this.shiftButton = new JToggleButton(
        new ImageIcon(ImageResourceLoader.loadImage("/images/capitalization.png")));
    this.shiftButton.setToolTipText("Shift");

    this.keysPanel = new JPanel();
    this.keysPanel.setBackground(BACKGROUND);
    this.initDialog();
  }

  public void show() {
    this.refreshLanguageSelection();
    this.applyLastWindowLocation();

    this.keysPanel.revalidate();
    this.keysPanel.doLayout();
    this.doRebuildKeys();
    this.dialog.setVisible(true);
  }

  public void hide() {
    this.dialog.setVisible(false);
  }

  public void dispose() {
    this.dialog.dispose();
  }

  private void initDialog() {
    this.dialog.setModal(false);
    this.dialog.setResizable(false);
    this.dialog.setAlwaysOnTop(false);
    this.dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    this.dialog.setLocationByPlatform(true);

    final JPanel topPanel = new JPanel();
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
    topPanel.add(Box.createHorizontalGlue());
    topPanel.setBackground(BACKGROUND);

    topPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

    final DefaultListCellRenderer languageRenderer = new DefaultListCellRenderer();
    this.languageComboBox.setRenderer((list, value, index, isSelected, cellHasFocus) ->
        languageRenderer.getListCellRendererComponent(
            list,
            value == null ? "" : value.getAbbreviation(),
            index,
            isSelected,
            cellHasFocus));
    this.applyLanguageComboBoxSize();
    this.languageComboBox.addActionListener(event -> this.changeLanguage());
    this.shiftButton.addActionListener(event -> this.doRebuildKeys());
    final JButton hideButton =
        new JButton(new ImageIcon(ImageResourceLoader.loadImage("/images/cross.png")));
    hideButton.setToolTipText("Hide keyboard");
    hideButton.addActionListener(event -> {
      this.hide();
      this.onHide.run();
    });

    topPanel.add(this.languageComboBox);
    topPanel.add(this.shiftButton);
    topPanel.add(hideButton);

    this.dialog.add(topPanel, BorderLayout.NORTH);
    this.dialog.add(this.keysPanel, BorderLayout.CENTER);
    this.dialog.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentHidden(final ComponentEvent event) {
        VirtualKeyboardWindow.this.rememberCurrentWindowLocation();
        VirtualKeyboardWindow.this.onHide.run();
      }

      @Override
      public void componentMoved(final ComponentEvent event) {
        VirtualKeyboardWindow.this.rememberCurrentWindowLocation();
      }
    });
  }

  private void changeLanguage() {
    final Object selected = this.languageComboBox.getSelectedItem();
    if (!(selected instanceof KeyboardLanguage selectedLanguage) ||
        this.currentLanguage == selectedLanguage) {
      return;
    }
    this.currentLanguage = selectedLanguage;
    lastSelectedLanguage = this.currentLanguage;
    this.rebuildKeys();
    this.dialog.pack();
  }

  private void refreshLanguageSelection() {
    this.languageComboBox.setSelectedItem(this.currentLanguage);
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

  private void doRebuildKeys() {
    this.rebuildKeys();
    this.dialog.pack();
  }

  private char applyShift(final char symbol) {
    if (!Character.isLetter(symbol)) {
      return symbol;
    }
    return this.shiftButton.isSelected() ? Character.toUpperCase(symbol) :
        Character.toLowerCase(symbol);
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

  private KeyboardLanguage findLanguageWithLongestAbbreviation() {
    return this.languages.stream()
        .max(comparingInt(left -> left.getAbbreviation().length()))
        .orElse(this.languages.get(0));
  }

  private void applyLanguageComboBoxSize() {
    final FontMetrics fontMetrics =
        this.languageComboBox.getFontMetrics(this.languageComboBox.getFont());
    final int longestLabelWidth = this.languages.stream()
        .map(KeyboardLanguage::getAbbreviation)
        .mapToInt(fontMetrics::stringWidth)
        .max()
        .orElse(0);
    final Dimension preferredSize = this.languageComboBox.getPreferredSize();
    final int width = Math.max(preferredSize.width, longestLabelWidth + LANGUAGE_COMBO_EXTRA_WIDTH);
    final Dimension fixedSize = new Dimension(width, preferredSize.height);
    this.languageComboBox.setMinimumSize(fixedSize);
    this.languageComboBox.setPreferredSize(fixedSize);
    this.languageComboBox.setMaximumSize(fixedSize);
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
