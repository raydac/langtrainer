package com.igormaznitsa.langtrainer.modules.dialog;

import static java.util.Collections.min;

import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;
import com.igormaznitsa.langtrainer.engine.ClasspathLangResourceIndex;
import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.DialogLine;
import com.igormaznitsa.langtrainer.engine.DialogListEntry;
import com.igormaznitsa.langtrainer.engine.ImageResourceLoader;
import com.igormaznitsa.langtrainer.engine.InputEquivalenceRow;
import com.igormaznitsa.langtrainer.engine.LangResourceJson;
import com.igormaznitsa.langtrainer.engine.LangTrainerResourceAccess;
import com.igormaznitsa.langtrainer.engine.ResourceListSelectPanel;
import com.igormaznitsa.langtrainer.text.TypingComparisonUtils;
import com.igormaznitsa.langtrainer.ui.PhraseFlashBanner;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.util.Random;
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

  private static final Color TIP_TOGGLE_OFF_BG = new Color(236, 239, 241);
  private static final Color TIP_TOGGLE_OFF_FG = new Color(55, 71, 79);
  private static final Color TIP_TOGGLE_OFF_BORDER = new Color(176, 190, 197);
  private static final Color TIP_TOGGLE_ON_BG = new Color(255, 202, 40);
  private static final Color TIP_TOGGLE_ON_FG = Color.BLACK;
  private static final Color TIP_TOGGLE_ON_BORDER = new Color(230, 81, 0);

  private static final Color SHUFFLE_TOGGLE_OFF_BG = new Color(225, 190, 231);
  private static final Color SHUFFLE_TOGGLE_OFF_FG = new Color(74, 20, 140);
  private static final Color SHUFFLE_TOGGLE_OFF_BORDER = new Color(171, 71, 188);
  private static final Color SHUFFLE_TOGGLE_ON_BG = new Color(74, 20, 140);
  private static final Color SHUFFLE_TOGGLE_ON_FG = Color.WHITE;
  private static final Color SHUFFLE_TOGGLE_ON_BORDER = new Color(255, 214, 0);

  /**
   * Same border thickness and padding for Shuffle / Show / Tip so heights align and layout is stable.
   */
  private static final int HEADER_ACTION_BORDER_W = 3;
  private static final Insets HEADER_ACTION_INSETS = new Insets(8, 16, 8, 16);

  private final DefaultListModel<DialogListEntry> dialogListModel = new DefaultListModel<>();
  private final JPanel rootPanel = new JPanel(new CardLayout());
  private final JTextArea showA = makeShowArea();
  private final JTextArea showB = makeShowArea();
  private final JToggleButton shuffleToggle = new JToggleButton("Shuffle OFF");
  private final JButton showPhraseButton = new JButton("Show");
  private final JToggleButton tipToggle = new JToggleButton("Tip OFF");
  private final JLabel tipZone = new JLabel(" ", SwingConstants.CENTER);
  private final JTextArea inputA = this.makeInputArea();
  private final JTextArea inputB = this.makeInputArea();
  private final JLabel progressHeader = new JLabel(" ", SwingConstants.CENTER);
  private JScrollPane historyScrollA;
  private JScrollPane historyScrollB;
  private final List<String> historyA = new ArrayList<>();
  private final List<String> historyB = new ArrayList<>();
  private File lastDialogOpenDirectory;
  private JList<DialogListEntry> dialogSelectionList;
  private DialogDefinition activeDialog;
  private boolean userWritesToA;
  /**
   * Indices of dialog lines not yet answered correctly. The active prompt is always one of these
   * until the user submits; then that index is removed so it is never chosen again.
   */
  private final List<Integer> remainingLineIndices = new ArrayList<>();
  /**
   * Index in {@link DialogDefinition#lines()} for the line the user is typing now.
   */
  private int currentLineOrdinal;
  private boolean workRoundActive;
  private JDialog completionOverlay;
  private Timer completionDismissTimer;
  private final PhraseFlashBanner phraseFlashBanner = new PhraseFlashBanner();
  private boolean applyingInputEquivalence;

  public DialogModule() {
    ClasspathLangResourceIndex.loadShared(
            DialogModule.class, this, "Can't load dialog definitions")
        .forEach(d -> this.dialogListModel.addElement(new DialogListEntry(d, false)));
    this.bindEnterToSubmit(this.inputA);
    this.bindEnterToSubmit(this.inputB);
    this.attachKeepFocusOnWorkField(this.inputA);
    this.attachKeepFocusOnWorkField(this.inputB);
    this.attachInputEquivalence(this.inputA);
    this.attachInputEquivalence(this.inputB);
    this.attachTipRefreshOnInput(this.inputA);
    this.attachTipRefreshOnInput(this.inputB);
    this.configureTipUi();
    this.configureShuffleAndShowUi();
    this.rootPanel.add(this.makeSelectPanel(), CARD_SELECT);
    this.rootPanel.add(this.makeWorkPanel(), CARD_WORK);
    this.showCard(CARD_SELECT);
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
        if (!InputEquivalenceSupport.codePointsMatchForTip(cpa, cpb)) {
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

  @Override
  public String getName() {
    return "Dialogs";
  }

  @Override
  public String getDescription() {
    return "Translation dialog trainer";
  }

  @Override
  public JComponent createControlForm() {
    return this.rootPanel;
  }

  private static void configureHistoryScrollPane(final JScrollPane scroll) {
    scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setPreferredSize(new Dimension(280, 220));
  }

  @Override
  public void onCharClick(final char symbol) {
    final JTextArea target = this.focusedInput();
    target.replaceSelection(String.valueOf(symbol));
    target.requestFocusInWindow();
  }

  @Override
  public boolean isResourceAllowed(final JsonObject resourceDescription) {
    return LangTrainerResourceAccess.visibleToModule(
        resourceDescription, LangTrainerModuleId.DIALOG);
  }

  @Override
  public List<KeyboardLanguage> getSupportedLanguages() {
    return KeyboardLanguage.VIRTUAL_BOARD_ALL;
  }

  private static String extractDocumentText(final Document doc) {
    try {
      return doc.getText(0, doc.getLength());
    } catch (final BadLocationException ex) {
      return "";
    }
  }

  @Override
  public Icon getImage() {
    return ImageResourceLoader.loadIcon("/dialogs/images/module-dialog.svg", 128, 128);
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

  private static int maxStringWidth(
      final FontMetrics metrics, final String a, final String b) {
    return Math.max(metrics.stringWidth(a), metrics.stringWidth(b));
  }

  /**
   * Minimum width from label text (avoids ellipsis when the east header is squeezed).
   */
  private static int minToggleWidthForLabels(
      final JToggleButton button, final String offText, final String onText) {
    final FontMetrics fm = button.getFontMetrics(button.getFont());
    return HEADER_ACTION_BORDER_W * 2
        + HEADER_ACTION_INSETS.left
        + HEADER_ACTION_INSETS.right
        + maxStringWidth(fm, offText, onText)
        + 8;
  }

  @Override
  public void onActivation() {
    this.dismissCompletionBanner();
    this.phraseFlashBanner.dismiss();
    this.setTipControlsWorkMode(false);
    this.showCard(CARD_SELECT);
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
    final JPanel eastButtons = new JPanel(new GridLayout(1, 3, 8, 0));
    eastButtons.setOpaque(false);
    eastButtons.add(this.shuffleToggle);
    eastButtons.add(this.showPhraseButton);
    eastButtons.add(this.tipToggle);
    this.syncWorkHeaderActionButtonSizes();
    headerRow.add(eastButtons, BorderLayout.EAST);

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
    enterPanel.add(this.wrapInputScroll(this.inputA));
    enterPanel.add(this.wrapInputScroll(this.inputB));
    panel.add(enterPanel, BorderLayout.SOUTH);
    return panel;
  }

  private void applyTipToggleLook() {
    final boolean on = this.tipToggle.isSelected();
    this.tipToggle.setText(on ? "Tip ON" : "Tip OFF");
    final Color lineColor = on ? TIP_TOGGLE_ON_BORDER : TIP_TOGGLE_OFF_BORDER;
    this.tipToggle.setBackground(on ? TIP_TOGGLE_ON_BG : TIP_TOGGLE_OFF_BG);
    this.tipToggle.setForeground(on ? TIP_TOGGLE_ON_FG : TIP_TOGGLE_OFF_FG);
    this.tipToggle.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(lineColor, HEADER_ACTION_BORDER_W, true),
        BorderFactory.createEmptyBorder(
            HEADER_ACTION_INSETS.top,
            HEADER_ACTION_INSETS.left,
            HEADER_ACTION_INSETS.bottom,
            HEADER_ACTION_INSETS.right)));
  }

  private void applyShuffleToggleLook() {
    final boolean on = this.shuffleToggle.isSelected();
    this.shuffleToggle.setText(on ? "Shuffle ON" : "Shuffle OFF");
    final Color lineColor = on ? SHUFFLE_TOGGLE_ON_BORDER : SHUFFLE_TOGGLE_OFF_BORDER;
    this.shuffleToggle.setBackground(on ? SHUFFLE_TOGGLE_ON_BG : SHUFFLE_TOGGLE_OFF_BG);
    this.shuffleToggle.setForeground(on ? SHUFFLE_TOGGLE_ON_FG : SHUFFLE_TOGGLE_OFF_FG);
    this.shuffleToggle.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(lineColor, HEADER_ACTION_BORDER_W, true),
        BorderFactory.createEmptyBorder(
            HEADER_ACTION_INSETS.top,
            HEADER_ACTION_INSETS.left,
            HEADER_ACTION_INSETS.bottom,
            HEADER_ACTION_INSETS.right)));
  }

  /**
   * Sets Shuffle, Show, and Tip to the same width and height (widest/tallest needed). Drops
   * {@code maximumSize} caps so BorderLayout does not clip labels to "Tip O…".
   */
  private void syncWorkHeaderActionButtonSizes() {
    this.applyShuffleToggleLook();
    this.applyTipToggleLook();

    final int wShuffle =
        Math.max(
            this.shuffleToggle.getPreferredSize().width,
            minToggleWidthForLabels(
                this.shuffleToggle, "Shuffle OFF", "Shuffle ON"));
    final int wTip =
        Math.max(
            this.tipToggle.getPreferredSize().width,
            minToggleWidthForLabels(this.tipToggle, "Tip OFF", "Tip ON"));
    final int wShow = this.showPhraseButton.getPreferredSize().width;

    final int hShuffle = this.shuffleToggle.getPreferredSize().height;
    final int hTip = this.tipToggle.getPreferredSize().height;
    final int hShow = this.showPhraseButton.getPreferredSize().height;

    final int cellW = Math.max(Math.max(wShuffle, wShow), wTip);
    final int cellH = Math.max(Math.max(hShuffle, hTip), hShow);
    final Dimension cell = new Dimension(cellW, cellH);

    this.shuffleToggle.setPreferredSize(cell);
    this.shuffleToggle.setMinimumSize(cell);
    this.shuffleToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, cell.height));

    this.showPhraseButton.setPreferredSize(cell);
    this.showPhraseButton.setMinimumSize(cell);
    this.showPhraseButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, cell.height));

    this.tipToggle.setPreferredSize(cell);
    this.tipToggle.setMinimumSize(cell);
    this.tipToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, cell.height));
  }

  private void configureTipUi() {
    this.tipToggle.setFont(this.tipToggle.getFont().deriveFont(Font.BOLD, 16f));
    this.tipToggle.setOpaque(true);
    this.tipToggle.setContentAreaFilled(true);
    this.tipToggle.setFocusPainted(false);
    this.tipToggle.setHorizontalAlignment(SwingConstants.CENTER);
    this.tipToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    this.tipToggle.setToolTipText(
        "Show the next character hint under the line counter while typing");
    this.tipToggle.addActionListener(event -> {
      this.applyTipToggleLook();
      this.refreshTipZone();
      this.syncWorkHeaderActionButtonSizes();
    });
    this.applyTipToggleLook();

    this.tipZone.setOpaque(true);
    this.tipZone.setBackground(TIP_ZONE_BG);
    this.tipZone.setForeground(TIP_ZONE_FG);
    this.tipZone.setFont(this.tipZone.getFont().deriveFont(Font.BOLD, 18f));
    this.tipZone.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(200, 80, 80), 2, true),
        BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    this.tipZone.setVisible(false);
  }

  private void configureShuffleAndShowUi() {
    this.shuffleToggle.setFont(this.shuffleToggle.getFont().deriveFont(Font.BOLD, 16f));
    this.shuffleToggle.setOpaque(true);
    this.shuffleToggle.setContentAreaFilled(true);
    this.shuffleToggle.setFocusPainted(false);
    this.shuffleToggle.setHorizontalAlignment(SwingConstants.CENTER);
    this.shuffleToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    this.shuffleToggle.setToolTipText(
        "<html>Off: remaining lines follow dialog order.<br>"
            + "On: each next line is picked at random among lines you have not completed yet.<br>"
            +
            "JSON <code>shuffled</code> sets the default when a dialog starts; you can change it here anytime.</html>");
    this.shuffleToggle.addActionListener(event -> {
      this.applyShuffleToggleLook();
      this.syncWorkHeaderActionButtonSizes();
    });
    this.applyShuffleToggleLook();

    this.showPhraseButton.setFont(this.showPhraseButton.getFont().deriveFont(Font.BOLD, 16f));
    this.showPhraseButton.setOpaque(true);
    this.showPhraseButton.setContentAreaFilled(true);
    this.showPhraseButton.setBackground(PhraseFlashBanner.SHOW_ACTION_BUTTON_BG);
    this.showPhraseButton.setForeground(PhraseFlashBanner.SHOW_ACTION_BUTTON_FG);
    this.showPhraseButton.setFocusPainted(false);
    this.showPhraseButton.setHorizontalAlignment(SwingConstants.CENTER);
    this.showPhraseButton.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(PhraseFlashBanner.SHOW_ACTION_BUTTON_BORDER,
            HEADER_ACTION_BORDER_W, true),
        BorderFactory.createEmptyBorder(
            HEADER_ACTION_INSETS.top,
            HEADER_ACTION_INSETS.left,
            HEADER_ACTION_INSETS.bottom,
            HEADER_ACTION_INSETS.right)));
    this.showPhraseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    this.showPhraseButton.setToolTipText(
        "Flash the answer and translation for 5s (alternating each second)");
    this.showPhraseButton.addActionListener(event -> this.showPhraseLearningBanner());
  }

  /**
   * Keeps toggle visuals in sync when the work round ends (buttons may be disabled).
   */
  private void refreshHeaderToggleAppearance() {
    this.applyTipToggleLook();
    this.applyShuffleToggleLook();
    this.syncWorkHeaderActionButtonSizes();
  }

  /**
   * Chooses {@link #currentLineOrdinal} from {@link #remainingLineIndices}: lowest dialog index
   * when shuffle is off, otherwise a uniform random remaining index.
   */
  private void pickCurrentLineFromRemaining() {
    if (this.remainingLineIndices.isEmpty()) {
      return;
    }
    if (this.shuffleToggle.isSelected()) {
      this.currentLineOrdinal =
          this.remainingLineIndices.get(new Random().nextInt(this.remainingLineIndices.size()));
    } else {
      this.currentLineOrdinal = min(this.remainingLineIndices);
    }
  }

  private JPanel makeSelectPanel() {
    final ResourceListSelectPanel.Result view = ResourceListSelectPanel.build(
        this.dialogListModel,
        ResourceListSelectPanel.Appearance.DIALOG,
        "Select dialog",
        "Choose and Start",
        "Open from file",
        this::chooseUserLanguageAndStart,
        this::openDialogFromFile);
    this.dialogSelectionList = view.list();
    return view.panel();
  }

  private void openDialogFromFile(final JList<DialogListEntry> list) {
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
      final DialogDefinition loaded = LangResourceJson.parseFromPath(file.toPath());
      final int index = DialogListEntry.addOrReplaceByMenuTitle(
          this.dialogListModel, new DialogListEntry(loaded, true));
      list.setSelectedIndex(index);
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

  private void showPhraseLearningBanner() {
    if (!this.workRoundActive || this.activeDialog == null) {
      return;
    }
    if (this.remainingLineIndices.isEmpty()) {
      return;
    }
    final Window owner = SwingUtilities.getWindowAncestor(this.rootPanel);
    if (owner == null) {
      return;
    }
    final DialogLine line = this.activeDialog.lines().get(this.currentLineOrdinal);
    final String expected = PhraseFlashBanner.normalizeLineBreaksForDisplay(
        this.userWritesToA ? line.a() : line.b());
    final String partner = PhraseFlashBanner.normalizeLineBreaksForDisplay(
        this.userWritesToA ? line.b() : line.a());
    this.phraseFlashBanner.show(
        owner,
        expected,
        partner,
        () -> {
          if (this.workRoundActive && this.activeDialog != null) {
            final JTextArea work = this.focusedInput();
            if (work.isEditable() && work.isShowing()) {
              work.requestFocusInWindow();
            }
          }
        });
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
            DialogModule.this.applyInputEquivalence(area, start, insertLen);
          } finally {
            DialogModule.this.applyingInputEquivalence = false;
          }
        });
        DialogModule.this.scheduleTipRefreshAfterInputMutation();
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
    if (!this.workRoundActive || this.activeDialog == null || area != this.focusedInput()) {
      return;
    }
    final List<InputEquivalenceRow> rules = this.activeDialog.inputEqu();
    if (rules.isEmpty() || insertLen <= 0) {
      return;
    }
    if (this.remainingLineIndices.isEmpty()) {
      return;
    }
    final DialogLine line = this.activeDialog.lines().get(this.currentLineOrdinal);
    final String expectedFull = this.userWritesToA ? line.a() : line.b();
    InputEquivalenceSupport.applyAfterInsert(area, expectedFull, rules, start, insertLen);
  }

  /**
   * After input-equivalence adjustments, refresh tips once the document is in its final state.
   */
  private void scheduleTipRefreshAfterInputMutation() {
    if (!this.tipToggle.isSelected() || !this.workRoundActive || this.activeDialog == null) {
      return;
    }
    SwingUtilities.invokeLater(
        () ->
            this.refreshTipZone(
                extractDocumentText(this.focusedInput().getDocument())));
  }

  private void attachTipRefreshOnInput(final JTextArea area) {
    area.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(final DocumentEvent event) {
        DialogModule.this.refreshTipZoneFromDocument(area, event.getDocument());
      }

      @Override
      public void removeUpdate(final DocumentEvent event) {
        DialogModule.this.refreshTipZoneFromDocument(area, event.getDocument());
      }

      @Override
      public void changedUpdate(final DocumentEvent event) {
        DialogModule.this.refreshTipZoneFromDocument(area, event.getDocument());
      }
    });
  }

  private void refreshTipZoneFromDocument(final JTextArea area, final Document doc) {
    if (area == this.focusedInput()) {
      this.refreshTipZone(extractDocumentText(doc));
    } else {
      this.refreshTipZone(null);
    }
  }

  private void refreshTipZone() {
    this.refreshTipZone(null);
  }

  private void refreshTipZone(final String enteredOverride) {
    if (!this.tipToggle.isSelected() || !this.workRoundActive || this.activeDialog == null) {
      this.tipZone.setVisible(false);
      return;
    }
    if (this.remainingLineIndices.isEmpty()) {
      this.tipZone.setVisible(false);
      return;
    }
    final DialogLine line = this.activeDialog.lines().get(this.currentLineOrdinal);
    final String expected = this.userWritesToA ? line.a() : line.b();
    final String entered =
        enteredOverride != null
            ? enteredOverride
            : extractDocumentText(this.focusedInput().getDocument());
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
    this.shuffleToggle.setEnabled(workActive);
    this.showPhraseButton.setEnabled(workActive);
    if (!workActive) {
      this.tipToggle.setSelected(false);
      this.tipZone.setVisible(false);
    }
    this.refreshHeaderToggleAppearance();
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
          final JTextArea work = DialogModule.this.focusedInput();
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
        if (!area.isEditable() || area != DialogModule.this.focusedInput()) {
          return;
        }
        event.consume();
        DialogModule.this.processEnter();
      }
    });
  }

  private void applyWorkbenchStyles(final boolean sessionFinished) {
    this.styleShowZone(this.showA, this.userWritesToA, sessionFinished);
    this.styleShowZone(this.showB, !this.userWritesToA, sessionFinished);
    this.styleWorkTarget(this.inputA, this.userWritesToA, sessionFinished);
    this.styleWorkTarget(this.inputB, !this.userWritesToA, sessionFinished);
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
      this.startDialog(definition);
    }
  }

  private void startDialog(final DialogDefinition definition) {
    this.activeDialog = definition;
    this.shuffleToggle.setSelected(definition.shuffled());
    final int lineCount = definition.lines().size();
    this.remainingLineIndices.clear();
    for (int i = 0; i < lineCount; i++) {
      this.remainingLineIndices.add(i);
    }
    this.pickCurrentLineFromRemaining();
    this.workRoundActive = true;
    this.tipToggle.setSelected(false);
    this.setTipControlsWorkMode(true);
    this.historyA.clear();
    this.historyB.clear();
    this.showA.setText("");
    this.showB.setText("");
    this.inputA.setText("");
    this.inputB.setText("");
    this.applyWorkbenchStyles(false);
    this.updateTargetTextForCurrentLine();
    this.refreshTipZone();
    this.showCard(CARD_WORK);
    this.rootPanel.revalidate();
    SwingUtilities.invokeLater(() -> {
      final JTextArea work = this.focusedInput();
      if (work.isEditable() && work.isShowing()) {
        work.requestFocusInWindow();
      }
    });
  }

  private void updateTargetTextForCurrentLine() {
    if (this.activeDialog != null && !this.remainingLineIndices.isEmpty()) {
      final DialogLine line = this.activeDialog.lines().get(this.currentLineOrdinal);
      if (this.userWritesToA) {
        this.inputB.setText(line.b());
      } else {
        this.inputA.setText(line.a());
      }
      this.focusedInput().setText("");
    }
    this.refreshProgressHeader();
    this.refreshTipZone();
  }

  private void refreshProgressHeader() {
    if (this.activeDialog == null) {
      this.progressHeader.setText(" ");
      return;
    }
    final int total = this.activeDialog.lines().size();
    if (total == 0) {
      this.progressHeader.setText(
          "Dialog %s,  line 0 from 0".formatted(this.activeDialog.menuName()));
      return;
    }
    final int displayLine =
        this.remainingLineIndices.isEmpty()
            ? total
            : total - this.remainingLineIndices.size() + 1;
    this.progressHeader.setText(
        "Dialog %s,  line %d from %d".formatted(this.activeDialog.menuName(), displayLine, total));
  }

  private void processEnter() {
    if (this.activeDialog != null && !this.remainingLineIndices.isEmpty()) {
      final DialogLine line = this.activeDialog.lines().get(this.currentLineOrdinal);
      final String expected = this.userWritesToA ? line.a() : line.b();
      final String entered = this.focusedInput().getText();
      if (this.isCloseEnough(entered, expected)) {
        this.historyA.add(line.a());
        this.historyB.add(line.b());
        this.showA.setText(String.join("\n", this.historyA));
        this.showB.setText(String.join("\n", this.historyB));
        this.scrollHistoryPanesToBottom();
        this.remainingLineIndices.remove(Integer.valueOf(this.currentLineOrdinal));
        if (this.remainingLineIndices.isEmpty()) {
          this.finishDialog();
        } else {
          this.pickCurrentLineFromRemaining();
          this.updateTargetTextForCurrentLine();
        }
      } else {
        java.awt.Toolkit.getDefaultToolkit().beep();
      }
    }
  }

  private void finishDialog() {
    this.phraseFlashBanner.dismiss();
    this.workRoundActive = false;
    this.setTipControlsWorkMode(false);
    this.applyWorkbenchStyles(true);
    this.showCompletionBannerOverlay();
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
      this.showCard(CARD_SELECT);
      return;
    }
    this.dismissCompletionBanner();
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
      this.dismissCompletionBanner();
      this.showCard(CARD_SELECT);
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
    return this.userWritesToA ? this.inputA : this.inputB;
  }

  private boolean isCloseEnough(final String actual, final String expected) {
    return TypingComparisonUtils.isCloseEnough(actual, expected);
  }

  private void showCard(final String card) {
    final CardLayout layout = (CardLayout) this.rootPanel.getLayout();
    layout.show(this.rootPanel, card);
  }
}
