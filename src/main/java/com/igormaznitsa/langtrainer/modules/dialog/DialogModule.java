package com.igormaznitsa.langtrainer.modules.dialog;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import com.igormaznitsa.langtrainer.engine.ImageResourceLoader;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

public final class DialogModule extends AbstractLangTrainerModule {

  private static final String CARD_SELECT = "select";
  private static final String CARD_WORK = "work";

  private static final Color ZONE_WORK_BG = new Color(198, 245, 198);
  private static final Color ZONE_TARGET_BG = new Color(18, 42, 86);
  private static final Color ZONE_TARGET_FG = new Color(255, 235, 100);
  private static final float INPUT_FONT_SIZE = 22f;

  private static final float PROGRESS_HEADER_FONT_SIZE = 20f;
  private static final Color PROGRESS_HEADER_BG = new Color(255, 248, 220);
  private static final Color PROGRESS_HEADER_FG = new Color(20, 20, 20);

  private static final Color BANNER_BG = new Color(0, 75, 38);
  private static final Color BANNER_FG = new Color(255, 145, 30);
  private static final float BANNER_FONT_SIZE = 72f;

  private static final Color TIP_ZONE_BG = new Color(255, 205, 205);
  private static final Color TIP_ZONE_FG = new Color(90, 0, 0);
  private static final Color TIP_TOGGLE_BG = new Color(255, 152, 0);
  private static final Color TIP_TOGGLE_FG = new Color(40, 20, 0);
  private static final Color TIP_TOGGLE_BORDER = new Color(191, 87, 0);

  private final DefaultListModel<DialogDefinition> dialogListModel = new DefaultListModel<>();
  private final JPanel rootPanel = new JPanel(new CardLayout());
  private final JTextArea showA = makeShowArea();
  private final JTextArea showB = makeShowArea();
  private final JToggleButton tipToggle = new JToggleButton("Tip");
  private final JLabel tipZone = new JLabel(" ", SwingConstants.CENTER);
  private final JTextArea inputA = makeInputArea();
  private final JTextArea inputB = makeInputArea();
  private final JLabel progressHeader = new JLabel(" ", SwingConstants.CENTER);
  private JScrollPane historyScrollA;
  private JScrollPane historyScrollB;
  private final List<String> historyA = new ArrayList<>();
  private final List<String> historyB = new ArrayList<>();
  private File lastDialogOpenDirectory;
  private JList<DialogDefinition> dialogSelectionList;
  private DialogDefinition activeDialog;
  private boolean userWritesToA;
  private int lineIndex;
  private boolean workRoundActive;
  private JDialog completionOverlay;
  private Timer completionDismissTimer;
  private boolean applyingInputEquivalence;

  public DialogModule() {
    DialogDataLoader.loadAll().forEach(this.dialogListModel::addElement);
    bindEnterToSubmit(this.inputA);
    bindEnterToSubmit(this.inputB);
    attachKeepFocusOnWorkField(this.inputA);
    attachKeepFocusOnWorkField(this.inputB);
    attachInputEquivalence(this.inputA);
    attachInputEquivalence(this.inputB);
    attachTipRefreshOnInput(this.inputA);
    attachTipRefreshOnInput(this.inputB);
    configureTipUi();
    this.rootPanel.add(makeSelectPanel(), CARD_SELECT);
    this.rootPanel.add(makeWorkPanel(), CARD_WORK);
    showCard(CARD_SELECT);
  }

  private static void syncViewportBackground(final JTextArea area) {
    final Container parent = area.getParent();
    if (parent instanceof JViewport viewport) {
      viewport.setBackground(area.getBackground());
    }
  }

  private static void scrollHistoryPaneToBottom(final JScrollPane scroll, final JTextArea area) {
    if (scroll == null || area == null) {
      return;
    }
    try {
      area.setCaretPosition(area.getDocument().getLength());
    } catch (final Exception ignored) {
      // caret move is best-effort for non-editable areas
    }
  }

  private static void nudgeScrollBarToEnd(final JScrollPane scroll) {
    if (scroll == null) {
      return;
    }
    final JScrollBar vertical = scroll.getVerticalScrollBar();
    vertical.setValue(vertical.getMaximum());
  }

  /**
   * Line-wrapped {@link JTextArea} that still reports a real content height so a {@link
   * JScrollPane} can show a vertical bar (default wrapped text areas track viewport height and
   * never scroll vertically).
   */
  private static JTextArea makeShowArea() {
    final JTextArea area =
        new JTextArea() {
          @Override
          public boolean getScrollableTracksViewportHeight() {
            return false;
          }
        };
    area.setEditable(false);
    area.setFocusable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setMargin(new Insets(8, 10, 8, 10));
    area.setOpaque(true);
    area.setFont(area.getFont().deriveFont(INPUT_FONT_SIZE));
    return area;
  }

  @Override
  public String getName() {
    String result;
    result = "DIALOG";
    return result;
  }

  @Override
  public String getDescription() {
    String result;
    result = "Translation dialog trainer";
    return result;
  }

  @Override
  public Icon getImage() {
    Icon result;
    result = ImageResourceLoader.loadIcon("/images/module-dialog.svg", 128, 128);
    return result;
  }

  @Override
  public JComponent createControlForm() {
    return this.rootPanel;
  }

  @Override
  public List<KeyboardLanguage> getSupportedLanguages() {
    List<KeyboardLanguage> result;
    result = List.of(KeyboardLanguage.ENG, KeyboardLanguage.RUS, KeyboardLanguage.EST);
    return result;
  }

  private static void configureHistoryScrollPane(final JScrollPane scroll) {
    scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setPreferredSize(new Dimension(280, 220));
  }

  @Override
  public void onCharClick(final char symbol) {
    final JTextArea target = focusedInput();
    target.replaceSelection(String.valueOf(symbol));
    target.requestFocusInWindow();
  }

  private JPanel makeSelectPanel() {
    final Color panelBg = new Color(236, 242, 249);
    final Color listBorder = new Color(100, 130, 170);
    final Color rowDivider = new Color(215, 224, 238);
    final Color selectedBg = new Color(21, 101, 192);
    final Color unselectedBg = Color.WHITE;
    final Color unselectedFg = new Color(38, 50, 56);

    final JPanel panel = new JPanel(new BorderLayout(12, 14));
    panel.setBackground(panelBg);
    panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));

    final JLabel title = new JLabel("Select dialog", SwingConstants.CENTER);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 28.0f));
    title.setForeground(new Color(25, 45, 85));
    title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    panel.add(title, BorderLayout.NORTH);

    final JList<DialogDefinition> list = new JList<>(this.dialogListModel);
    this.dialogSelectionList = list;
    list.setBackground(unselectedBg);
    list.setSelectionBackground(selectedBg);
    list.setSelectionForeground(Color.WHITE);
    list.setFont(list.getFont().deriveFont(Font.PLAIN, 19f));
    list.setFixedCellHeight(52);
    list.setSelectedIndex(0);
    list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
      final JLabel label = new JLabel(value.menuName());
      label.setOpaque(true);
      label.setFont(label.getFont().deriveFont(Font.BOLD, 19f));
      label.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createMatteBorder(0, 0, 1, 0, rowDivider),
          BorderFactory.createEmptyBorder(12, 18, 12, 18)));
      label.setToolTipText(
          "<html><body style='width:280px;'>%s</body></html>".formatted(value.description()));
      if (isSelected) {
        label.setBackground(selectedBg);
        label.setForeground(Color.WHITE);
      } else {
        label.setBackground(unselectedBg);
        label.setForeground(unselectedFg);
      }
      return label;
    });

    final JScrollPane scroll = new JScrollPane(list);
    scroll.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(listBorder, 2, true),
        BorderFactory.createEmptyBorder(2, 2, 2, 2)));
    scroll.getViewport().setBackground(unselectedBg);
    scroll.setPreferredSize(new Dimension(480, 280));
    panel.add(scroll, BorderLayout.CENTER);

    final JButton start = new JButton("Choose and Start");
    start.setFont(start.getFont().deriveFont(Font.BOLD, 18f));
    start.setForeground(Color.WHITE);
    start.setBackground(new Color(46, 125, 50));
    start.setOpaque(true);
    start.setContentAreaFilled(true);
    start.setFocusPainted(false);
    start.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(27, 94, 32), 2, true),
        BorderFactory.createEmptyBorder(14, 32, 14, 32)));
    start.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    start.addActionListener(event -> {
      final DialogDefinition selected = list.getSelectedValue();
      if (selected != null) {
        chooseUserLanguageAndStart(selected);
      }
    });

    final JPanel southWrap = new JPanel(new BorderLayout());
    southWrap.setBackground(panelBg);
    southWrap.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
    buttonRow.setOpaque(false);

    final JButton openFile = new JButton("Open from file");
    openFile.setFont(openFile.getFont().deriveFont(Font.BOLD, 18f));
    openFile.setForeground(Color.WHITE);
    openFile.setBackground(new Color(25, 118, 210));
    openFile.setOpaque(true);
    openFile.setContentAreaFilled(true);
    openFile.setFocusPainted(false);
    openFile.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(13, 71, 161), 2, true),
        BorderFactory.createEmptyBorder(14, 28, 14, 28)));
    openFile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    openFile.addActionListener(event -> openDialogFromFile(list));

    buttonRow.add(openFile);
    buttonRow.add(start);
    southWrap.add(buttonRow, BorderLayout.CENTER);
    panel.add(southWrap, BorderLayout.SOUTH);
    return panel;
  }

  private void openDialogFromFile(final JList<DialogDefinition> list) {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Dialog JSON (*.json)", "json"));
    chooser.setAcceptAllFileFilterUsed(false);
    if (this.lastDialogOpenDirectory != null) {
      chooser.setCurrentDirectory(this.lastDialogOpenDirectory);
    }
    final int option = chooser.showOpenDialog(this.rootPanel);
    if (option != JFileChooser.APPROVE_OPTION) {
      return;
    }
    final File file = chooser.getSelectedFile();
    if (file == null) {
      return;
    }
    try {
      final DialogDefinition loaded = DialogDataLoader.loadFromFile(file.toPath());
      this.dialogListModel.addElement(loaded);
      list.setSelectedIndex(this.dialogListModel.getSize() - 1);
      final File parent = file.getParentFile();
      if (parent != null) {
        this.lastDialogOpenDirectory = parent;
      }
    } catch (final Exception ex) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          ex.getMessage(),
          "Can't open dialog file",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Maps a character index in the user's text to the corresponding index in the expected line.
   * Handles omitted punctuation (e.g. comma after {@code Jah}) and letter case so {@code jah ma
   * toötan} lines up with {@code Jah, ma töötan}.
   */
  private static int expectedOffsetForDocumentIndex(
      final String doc, final String expected, final int docIndex) {
    int i = 0;
    int j = 0;
    final int target = Math.min(docIndex, doc.length());
    while (i < target && j < expected.length()) {
      final int cpd = doc.codePointAt(i);
      final int cpe = expected.codePointAt(j);
      final int di = Character.charCount(cpd);
      final int ej = Character.charCount(cpe);
      if (codePointsMatchForTip(cpd, cpe)) {
        i += di;
        j += ej;
        continue;
      }
      if (isSkippableSeparatorInExpected(cpe)) {
        final int afterSep = j + ej;
        if (afterSep < expected.length()) {
          final int cpNext = expected.codePointAt(afterSep);
          if (codePointsMatchForTip(cpd, cpNext)) {
            j = afterSep + Character.charCount(cpNext);
            i += di;
            continue;
          }
        }
        if (Character.isWhitespace(cpd)) {
          j += ej;
          continue;
        }
        i += di;
        j += ej;
        continue;
      }
      i += di;
      j += ej;
    }
    return j;
  }

  private static boolean isSkippableSeparatorInExpected(final int cp) {
    return cp == ',' || cp == ';' || cp == ':' || cp == '.' || cp == '!' || cp == '?' || cp == '…';
  }

  private static String matchInputEquivalence(
      final String typed,
      final String expectedChar,
      final List<InputEquivalenceRow> rules) {
    for (final InputEquivalenceRow row : rules) {
      final List<String> keys = row.key();
      final List<String> vals = row.value();
      for (int i = 0; i < keys.size(); i++) {
        if (typed.equals(keys.get(i)) && expectedChar.equals(vals.get(i))) {
          return vals.get(i);
        }
      }
    }
    return null;
  }

  private static String extractDocumentText(final Document doc) {
    try {
      return doc.getText(0, doc.getLength());
    } catch (final BadLocationException ex) {
      return "";
    }
  }

  /**
   * Tip for the current expected word: correct prefix, then one {@code .} per still-hidden slot
   * before the last <em>letter</em> of the word (trailing {@code ? ! …} are not used as the
   * right anchor). If the user finished all letters of a token and only punctuation remains
   * (e.g. {@code Jah} vs {@code Jah,}), the tip advances to the next word.
   */
  private static String computeTypingTip(final String entered, final String expected) {
    if (expected == null || expected.isEmpty()) {
      return "";
    }
    int ei = 0;
    int ej = 0;
    final int enteredLen = entered.length();
    final int expectedLen = expected.length();
    while (true) {
      while (ei < enteredLen && Character.isWhitespace(entered.charAt(ei))) {
        ei++;
      }
      while (ej < expectedLen && Character.isWhitespace(expected.charAt(ej))) {
        ej++;
      }
      if (ej >= expectedLen) {
        return "";
      }
      final int wordStartExpected = ej;
      while (ej < expectedLen && !Character.isWhitespace(expected.charAt(ej))) {
        ej++;
      }
      final String expectedWord = expected.substring(wordStartExpected, ej);
      if (expectedWord.isEmpty()) {
        continue;
      }
      final int wordStartEntered = ei;
      while (ei < enteredLen && !Character.isWhitespace(entered.charAt(ei))) {
        ei++;
      }
      final String enteredWord = entered.substring(wordStartEntered, ei);
      int eiW = 0;
      int ejW = 0;
      while (eiW < enteredWord.length() && ejW < expectedWord.length()) {
        final int cpa = enteredWord.codePointAt(eiW);
        final int cpb = expectedWord.codePointAt(ejW);
        if (!codePointsMatchForTip(cpa, cpb)) {
          break;
        }
        eiW += Character.charCount(cpa);
        ejW += Character.charCount(cpb);
      }
      if (eiW < enteredWord.length()) {
        if (ejW < expectedWord.length()) {
          return formatWordTipWithLetterDots(expectedWord, ejW);
        }
        return expectedWord;
      }
      if (ejW < expectedWord.length()) {
        if (onlyNonLettersFrom(expectedWord, ejW)) {
          continue;
        }
        return formatWordTipWithLetterDots(expectedWord, ejW);
      }
    }
  }

  /**
   * True if every code point from {@code from} onward is not a Unicode letter.
   */
  private static boolean onlyNonLettersFrom(final String word, final int from) {
    int i = from;
    while (i < word.length()) {
      final int cp = word.codePointAt(i);
      if (Character.isLetter(cp)) {
        return false;
      }
      i += Character.charCount(cp);
    }
    return true;
  }

  private static boolean codePointsMatchForTip(final int typed, final int expected) {
    if (typed == expected) {
      return true;
    }
    if (Character.isLetter(typed) && Character.isLetter(expected)) {
      return Character.toLowerCase(typed) == Character.toLowerCase(expected);
    }
    return false;
  }

  /**
   * Start index in {@code word} of the last Unicode letter, or {@code -1} if none.
   */
  private static int indexOfLastLetterStart(final String word) {
    int last = -1;
    for (int i = 0; i < word.length(); ) {
      final int cp = word.codePointAt(i);
      if (Character.isLetter(cp)) {
        last = i;
      }
      i += Character.charCount(cp);
    }
    return last;
  }

  private static String substringOneCodePoint(final String word, final int start) {
    final int cp = word.codePointAt(start);
    return new String(Character.toChars(cp));
  }

  private static String formatWordTipWithLetterDots(final String word, final int correctPrefixLen) {
    if (word.isEmpty() || correctPrefixLen > word.length()) {
      return "";
    }
    if (correctPrefixLen == word.length()) {
      return "";
    }
    final int lastLetterStart = indexOfLastLetterStart(word);
    if (lastLetterStart < 0) {
      if (correctPrefixLen == 0) {
        final int dots = Math.max(0, word.length() - 2);
        return word.charAt(0) + ".".repeat(dots) + word.charAt(word.length() - 1);
      }
      final int dots = Math.max(0, word.length() - correctPrefixLen - 1);
      return word.substring(0, correctPrefixLen) + ".".repeat(dots) +
          word.charAt(word.length() - 1);
    }
    final String lastLetter = substringOneCodePoint(word, lastLetterStart);
    if (lastLetterStart == 0) {
      return lastLetter;
    }
    if (correctPrefixLen > lastLetterStart) {
      final int dots = word.length() - correctPrefixLen - 1;
      return word.substring(0, correctPrefixLen) + ".".repeat(dots) +
          word.charAt(word.length() - 1);
    }
    if (correctPrefixLen == 0) {
      final int dots = Math.max(0, lastLetterStart - 1);
      return word.charAt(0) + ".".repeat(dots) + lastLetter;
    }
    final int dots = Math.max(0, lastLetterStart - correctPrefixLen);
    return word.substring(0, correctPrefixLen) + ".".repeat(dots) + lastLetter;
  }

  private JTextArea makeInputArea() {
    final JTextArea area = new JTextArea();
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setRows(3);
    area.setMargin(new Insets(8, 10, 8, 10));
    area.setOpaque(true);
    return area;
  }

  private JScrollPane wrapInputScroll(final JTextArea area) {
    final JScrollPane scroll = new JScrollPane(area);
    scroll.setPreferredSize(new Dimension(280, 120));
    return scroll;
  }

  /**
   * After history text grows, show the newly appended line (both panes scroll to bottom).
   */
  private void scrollHistoryPanesToBottom() {
    SwingUtilities.invokeLater(() -> {
      scrollHistoryPaneToBottom(this.historyScrollA, this.showA);
      scrollHistoryPaneToBottom(this.historyScrollB, this.showB);
      SwingUtilities.invokeLater(() -> {
        nudgeScrollBarToEnd(this.historyScrollA);
        nudgeScrollBarToEnd(this.historyScrollB);
      });
    });
  }

  @Override
  public void onActivation() {
    dismissCompletionBanner();
    setTipControlsWorkMode(false);
    showCard(CARD_SELECT);
    SwingUtilities.invokeLater(() -> {
      if (this.dialogSelectionList != null) {
        this.dialogSelectionList.requestFocusInWindow();
      }
    });
  }

  private JPanel makeWorkPanel() {
    final JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    this.progressHeader.setFont(
        this.progressHeader.getFont().deriveFont(Font.BOLD, PROGRESS_HEADER_FONT_SIZE));
    this.progressHeader.setOpaque(true);
    this.progressHeader.setBackground(PROGRESS_HEADER_BG);
    this.progressHeader.setForeground(PROGRESS_HEADER_FG);
    this.progressHeader.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(160, 140, 80), 2, true),
        BorderFactory.createEmptyBorder(10, 12, 10, 12)));

    final JPanel headerRow = new JPanel(new BorderLayout(8, 0));
    headerRow.setOpaque(false);
    headerRow.add(this.progressHeader, BorderLayout.CENTER);
    headerRow.add(this.tipToggle, BorderLayout.EAST);

    final JPanel northStack = new JPanel(new BorderLayout(0, 6));
    northStack.setOpaque(false);
    northStack.add(headerRow, BorderLayout.NORTH);
    northStack.add(this.tipZone, BorderLayout.SOUTH);
    panel.add(northStack, BorderLayout.NORTH);

    final JPanel shows = new JPanel(new GridLayout(1, 2, 8, 8));
    this.historyScrollA = new JScrollPane(this.showA);
    this.historyScrollB = new JScrollPane(this.showB);
    configureHistoryScrollPane(this.historyScrollA);
    configureHistoryScrollPane(this.historyScrollB);
    shows.add(this.historyScrollA);
    shows.add(this.historyScrollB);
    panel.add(shows, BorderLayout.CENTER);

    final JPanel enterPanel = new JPanel(new GridLayout(1, 2, 8, 8));
    enterPanel.add(wrapInputScroll(this.inputA));
    enterPanel.add(wrapInputScroll(this.inputB));
    panel.add(enterPanel, BorderLayout.SOUTH);
    return panel;
  }

  private void configureTipUi() {
    this.tipToggle.setFont(this.tipToggle.getFont().deriveFont(Font.BOLD, 16f));
    this.tipToggle.setOpaque(true);
    this.tipToggle.setContentAreaFilled(true);
    this.tipToggle.setBackground(TIP_TOGGLE_BG);
    this.tipToggle.setForeground(TIP_TOGGLE_FG);
    this.tipToggle.setFocusPainted(false);
    this.tipToggle.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(TIP_TOGGLE_BORDER, 2, true),
        BorderFactory.createEmptyBorder(8, 18, 8, 18)));
    this.tipToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    this.tipToggle.setToolTipText(
        "Show the next character hint under the line counter while typing");
    this.tipToggle.addActionListener(event -> {
      if (this.tipToggle.isSelected()) {
        this.tipToggle.setBackground(new Color(255, 193, 7));
        this.tipToggle.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(230, 162, 0), 2, true),
            BorderFactory.createEmptyBorder(8, 18, 8, 18)));
      } else {
        this.tipToggle.setBackground(TIP_TOGGLE_BG);
        this.tipToggle.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(TIP_TOGGLE_BORDER, 2, true),
            BorderFactory.createEmptyBorder(8, 18, 8, 18)));
      }
      refreshTipZone();
    });

    this.tipZone.setOpaque(true);
    this.tipZone.setBackground(TIP_ZONE_BG);
    this.tipZone.setForeground(TIP_ZONE_FG);
    this.tipZone.setFont(this.tipZone.getFont().deriveFont(Font.BOLD, 18f));
    this.tipZone.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(200, 80, 80), 2, true),
        BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    this.tipZone.setVisible(false);
  }

  private void attachInputEquivalence(final JTextArea area) {
    area.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(final DocumentEvent event) {
        if (DialogModule.this.applyingInputEquivalence) {
          return;
        }
        final int start = event.getOffset();
        final int insertLen = event.getLength();
        SwingUtilities.invokeLater(() -> {
          try {
            DialogModule.this.applyingInputEquivalence = true;
            applyInputEquivalence(area, start, insertLen);
          } finally {
            DialogModule.this.applyingInputEquivalence = false;
          }
        });
        scheduleTipRefreshAfterInputMutation();
      }

      @Override
      public void removeUpdate(final DocumentEvent event) {
        // only replacements on insert
      }

      @Override
      public void changedUpdate(final DocumentEvent event) {
        // only replacements on insert
      }
    });
  }

  private void applyInputEquivalence(final JTextArea area, final int start, final int insertLen) {
    if (!this.workRoundActive || this.activeDialog == null || area != focusedInput()) {
      return;
    }
    final List<InputEquivalenceRow> rules = this.activeDialog.inputEqu();
    if (rules.isEmpty() || insertLen <= 0) {
      return;
    }
    if (this.lineIndex >= this.activeDialog.lines().size()) {
      return;
    }
    final DialogLine line = this.activeDialog.lines().get(this.lineIndex);
    final String expectedFull = this.userWritesToA ? line.a() : line.b();
    int p = start;
    int end = start + insertLen;
    while (p < end) {
      final String doc = area.getText();
      if (p >= doc.length()) {
        break;
      }
      final int cp = doc.codePointAt(p);
      final int chLen = Character.charCount(cp);
      if (p + chLen > doc.length()) {
        break;
      }
      final String typedStr = doc.substring(p, p + chLen);
      final int expPos = expectedOffsetForDocumentIndex(doc, expectedFull, p);
      if (expPos >= expectedFull.length()) {
        p += chLen;
        continue;
      }
      final int expLen = Character.charCount(expectedFull.codePointAt(expPos));
      if (expPos + expLen > expectedFull.length()) {
        p += chLen;
        continue;
      }
      final String expStr = expectedFull.substring(expPos, expPos + expLen);
      if (chLen != expLen) {
        p += chLen;
        continue;
      }
      final String replacement = matchInputEquivalence(typedStr, expStr, rules);
      if (replacement != null && !replacement.equals(typedStr)) {
        area.replaceRange(replacement, p, p + chLen);
        final int delta = replacement.length() - chLen;
        end += delta;
        p += replacement.length();
      } else {
        p += chLen;
      }
    }
  }

  /**
   * After input-equivalence adjustments, refresh tips once the document is in its final state.
   */
  private void scheduleTipRefreshAfterInputMutation() {
    if (!this.tipToggle.isSelected() || !this.workRoundActive || this.activeDialog == null) {
      return;
    }
    SwingUtilities.invokeLater(
        () -> refreshTipZone(extractDocumentText(focusedInput().getDocument())));
  }

  private void attachTipRefreshOnInput(final JTextArea area) {
    area.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(final DocumentEvent event) {
        refreshTipZoneFromDocument(area, event.getDocument());
      }

      @Override
      public void removeUpdate(final DocumentEvent event) {
        refreshTipZoneFromDocument(area, event.getDocument());
      }

      @Override
      public void changedUpdate(final DocumentEvent event) {
        refreshTipZoneFromDocument(area, event.getDocument());
      }
    });
  }

  private void refreshTipZoneFromDocument(final JTextArea area, final Document doc) {
    if (area == focusedInput()) {
      refreshTipZone(extractDocumentText(doc));
    } else {
      refreshTipZone(null);
    }
  }

  private void refreshTipZone() {
    refreshTipZone(null);
  }

  private void refreshTipZone(final String enteredOverride) {
    if (!this.tipToggle.isSelected() || !this.workRoundActive || this.activeDialog == null) {
      this.tipZone.setVisible(false);
      return;
    }
    if (this.lineIndex >= this.activeDialog.lines().size()) {
      this.tipZone.setVisible(false);
      return;
    }
    final DialogLine line = this.activeDialog.lines().get(this.lineIndex);
    final String expected = this.userWritesToA ? line.a() : line.b();
    final String entered =
        enteredOverride != null
            ? enteredOverride
            : extractDocumentText(focusedInput().getDocument());
    final String snippet = computeTypingTip(entered, expected);
    if (snippet.isEmpty()) {
      this.tipZone.setText("—");
    } else {
      this.tipZone.setText(snippet);
    }
    this.tipZone.setVisible(true);
    this.tipZone.repaint();
  }

  private void setTipControlsWorkMode(final boolean workActive) {
    this.tipToggle.setEnabled(workActive);
    if (!workActive) {
      this.tipToggle.setSelected(false);
      this.tipToggle.setBackground(TIP_TOGGLE_BG);
      this.tipToggle.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(TIP_TOGGLE_BORDER, 2, true),
          BorderFactory.createEmptyBorder(8, 18, 8, 18)));
      this.tipZone.setVisible(false);
    }
  }

  private void attachKeepFocusOnWorkField(final JTextArea area) {
    area.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(final FocusEvent event) {
        if (event.isTemporary()) {
          return;
        }
        if (!DialogModule.this.workRoundActive) {
          return;
        }
        final Component opposite = event.getOppositeComponent();
        if (opposite instanceof JButton || opposite instanceof JToggleButton) {
          return;
        }
        SwingUtilities.invokeLater(() -> {
          if (!DialogModule.this.workRoundActive || DialogModule.this.activeDialog == null) {
            return;
          }
          final JTextArea work = focusedInput();
          if (work.isEditable() && work.isShowing()) {
            work.requestFocusInWindow();
          }
        });
      }
    });
  }

  private void bindEnterToSubmit(final JTextArea area) {
    area.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(final KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.VK_ENTER || event.isShiftDown()) {
          return;
        }
        if (!area.isEditable() || area != focusedInput()) {
          return;
        }
        event.consume();
        processEnter();
      }
    });
  }

  private void applyWorkbenchStyles(final boolean sessionFinished) {
    styleShowZone(this.showA, this.userWritesToA, sessionFinished);
    styleShowZone(this.showB, !this.userWritesToA, sessionFinished);
    styleWorkTarget(this.inputA, this.userWritesToA, sessionFinished);
    styleWorkTarget(this.inputB, !this.userWritesToA, sessionFinished);
  }

  private void styleShowZone(
      final JTextArea area,
      final boolean isWorkSide,
      final boolean sessionFinished) {
    final Font base = area.getFont().deriveFont(INPUT_FONT_SIZE);
    area.setEditable(false);
    area.setFocusable(false);
    if (sessionFinished) {
      area.setFont(base.deriveFont(Font.BOLD));
      area.setBackground(new Color(225, 225, 225));
      area.setForeground(Color.DARK_GRAY);
      syncViewportBackground(area);
      return;
    }
    if (isWorkSide) {
      area.setFont(base.deriveFont(Font.BOLD));
      area.setBackground(ZONE_WORK_BG);
      area.setForeground(Color.BLACK);
    } else {
      area.setFont(base.deriveFont(Font.BOLD));
      area.setBackground(ZONE_TARGET_BG);
      area.setForeground(ZONE_TARGET_FG);
    }
    syncViewportBackground(area);
  }

  private void styleWorkTarget(
      final JTextArea area,
      final boolean isWorkZone,
      final boolean sessionFinished) {
    final Font base = area.getFont().deriveFont(INPUT_FONT_SIZE);
    if (sessionFinished) {
      area.setEditable(false);
      area.setFocusable(false);
      area.setFont(base.deriveFont(Font.BOLD));
      area.setBackground(new Color(225, 225, 225));
      area.setForeground(Color.DARK_GRAY);
      area.setCaretColor(Color.DARK_GRAY);
      syncViewportBackground(area);
      return;
    }
    if (isWorkZone) {
      area.setEditable(true);
      area.setFocusable(true);
      area.setFont(base.deriveFont(Font.BOLD));
      area.setBackground(ZONE_WORK_BG);
      area.setForeground(Color.BLACK);
      area.setCaretColor(Color.BLACK);
    } else {
      area.setEditable(false);
      area.setFocusable(false);
      area.setFont(base.deriveFont(Font.BOLD));
      area.setBackground(ZONE_TARGET_BG);
      area.setForeground(ZONE_TARGET_FG);
      area.setCaretColor(ZONE_TARGET_FG);
    }
    syncViewportBackground(area);
  }

  private void chooseUserLanguageAndStart(final DialogDefinition definition) {
    final JComboBox<String> chooser =
        new JComboBox<>(new String[] {definition.langA(), definition.langB()});
    final int option = JOptionPane.showConfirmDialog(
        this.rootPanel,
        chooser,
        "Choose language for input",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (option == JOptionPane.OK_OPTION) {
      final String selected = (String) chooser.getSelectedItem();
      this.userWritesToA = definition.langA().equals(selected);
      startDialog(definition);
    }
  }

  private void startDialog(final DialogDefinition definition) {
    this.activeDialog = definition;
    this.lineIndex = 0;
    this.workRoundActive = true;
    this.tipToggle.setSelected(false);
    this.tipToggle.setBackground(TIP_TOGGLE_BG);
    this.tipToggle.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(TIP_TOGGLE_BORDER, 2, true),
        BorderFactory.createEmptyBorder(8, 18, 8, 18)));
    setTipControlsWorkMode(true);
    this.historyA.clear();
    this.historyB.clear();
    this.showA.setText("");
    this.showB.setText("");
    this.inputA.setText("");
    this.inputB.setText("");
    applyWorkbenchStyles(false);
    updateTargetTextForCurrentLine();
    refreshTipZone();
    showCard(CARD_WORK);
    this.rootPanel.revalidate();
    SwingUtilities.invokeLater(() -> {
      final JTextArea work = focusedInput();
      if (work.isEditable() && work.isShowing()) {
        work.requestFocusInWindow();
      }
    });
  }

  private void updateTargetTextForCurrentLine() {
    if (this.activeDialog != null && this.lineIndex < this.activeDialog.lines().size()) {
      final DialogLine line = this.activeDialog.lines().get(this.lineIndex);
      if (this.userWritesToA) {
        this.inputB.setText(line.b());
      } else {
        this.inputA.setText(line.a());
      }
      focusedInput().setText("");
    }
    refreshProgressHeader();
    refreshTipZone();
  }

  private void refreshProgressHeader() {
    if (this.activeDialog == null) {
      this.progressHeader.setText(" ");
      return;
    }
    final int total = this.activeDialog.lines().size();
    final int displayLine = Math.min(this.lineIndex + 1, total);
    this.progressHeader.setText(
        "Dialog %s,  line %d from %d".formatted(this.activeDialog.menuName(), displayLine, total));
  }

  private void processEnter() {
    if (this.activeDialog != null && this.lineIndex < this.activeDialog.lines().size()) {
      final DialogLine line = this.activeDialog.lines().get(this.lineIndex);
      final String expected = this.userWritesToA ? line.a() : line.b();
      final String entered = focusedInput().getText();
      if (isCloseEnough(entered, expected)) {
        this.historyA.add(line.a());
        this.historyB.add(line.b());
        this.showA.setText(String.join("\n", this.historyA));
        this.showB.setText(String.join("\n", this.historyB));
        scrollHistoryPanesToBottom();
        this.lineIndex++;
        if (this.lineIndex >= this.activeDialog.lines().size()) {
          finishDialog();
        } else {
          updateTargetTextForCurrentLine();
        }
      } else {
        java.awt.Toolkit.getDefaultToolkit().beep();
      }
    }
  }

  private void finishDialog() {
    this.workRoundActive = false;
    setTipControlsWorkMode(false);
    applyWorkbenchStyles(true);
    showCompletionBannerOverlay();
  }

  private void dismissCompletionBanner() {
    if (this.completionDismissTimer != null) {
      this.completionDismissTimer.stop();
      this.completionDismissTimer = null;
    }
    if (this.completionOverlay != null) {
      this.completionOverlay.dispose();
      this.completionOverlay = null;
    }
  }

  private void showCompletionBannerOverlay() {
    final Window owner = SwingUtilities.getWindowAncestor(this.rootPanel);
    if (owner == null) {
      showCard(CARD_SELECT);
      return;
    }
    dismissCompletionBanner();
    final JDialog overlay = new JDialog(owner, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    this.completionOverlay = overlay;
    overlay.setUndecorated(true);
    overlay.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

    final JPanel pane = new JPanel(new BorderLayout());
    pane.setOpaque(true);
    pane.setBackground(BANNER_BG);
    pane.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
    overlay.setContentPane(pane);

    final Runnable closeAndReturn = () -> {
      dismissCompletionBanner();
      showCard(CARD_SELECT);
      SwingUtilities.invokeLater(() -> {
        if (this.dialogSelectionList != null) {
          this.dialogSelectionList.requestFocusInWindow();
        }
      });
    };

    final JLabel label = new JLabel("Hurraa! Completed!", SwingConstants.CENTER);
    label.setFont(label.getFont().deriveFont(Font.BOLD, BANNER_FONT_SIZE));
    label.setForeground(BANNER_FG);
    label.setOpaque(false);
    label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent event) {
        closeAndReturn.run();
      }
    });
    pane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    pane.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent event) {
        closeAndReturn.run();
      }
    });
    pane.add(label, BorderLayout.CENTER);

    overlay.setSize(owner.getWidth(), owner.getHeight());
    overlay.setLocation(owner.getLocationOnScreen());

    this.completionDismissTimer = new Timer(10_000, event -> closeAndReturn.run());
    this.completionDismissTimer.setRepeats(false);
    this.completionDismissTimer.start();

    SwingUtilities.invokeLater(() -> overlay.setVisible(true));
  }

  private JTextArea focusedInput() {
    JTextArea result;
    result = this.userWritesToA ? this.inputA : this.inputB;
    return result;
  }

  private boolean isCloseEnough(final String actual, final String expected) {
    final String left = normalize(actual);
    final String right = normalize(expected);
    final int maxLen = Math.max(left.length(), right.length());
    if (maxLen == 0) {
      return true;
    }
    final int distance = levenshtein(left, right);
    final double similarity = 1.0d - (double) distance / (double) maxLen;
    return similarity >= 0.99d;
  }

  private String normalize(final String text) {
    String result;
    if (text == null) {
      result = "";
    } else {
      final String withYeFolded = text
          .replace('Ё', 'Е')
          .replace('ё', 'е');
      final String lower = withYeFolded.toLowerCase();
      final StringBuilder builder = new StringBuilder(lower.length());
      lower
          .codePoints()
          .filter(Character::isLetterOrDigit)
          .forEach(builder::appendCodePoint);
      result = builder.toString();
    }
    return result;
  }

  private int levenshtein(final String a, final String b) {
    final int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j <= b.length(); j++) {
      dp[0][j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        dp[i][j] = Math.min(
            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
            dp[i - 1][j - 1] + cost);
      }
    }
    return dp[a.length()][b.length()];
  }

  private void showCard(final String card) {
    final CardLayout layout = (CardLayout) this.rootPanel.getLayout();
    layout.show(this.rootPanel, card);
  }
}
