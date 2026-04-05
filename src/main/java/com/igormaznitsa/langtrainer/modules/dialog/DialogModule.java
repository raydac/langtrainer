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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

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

  private final DefaultListModel<DialogDefinition> dialogListModel = new DefaultListModel<>();
  private final JPanel rootPanel = new JPanel(new CardLayout());
  private final JTextArea showA = makeShowArea();
  private final JTextArea showB = makeShowArea();
  private final JTextArea inputA = makeInputArea();
  private final JTextArea inputB = makeInputArea();
  private final JLabel progressHeader = new JLabel(" ", SwingConstants.CENTER);
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

  public DialogModule() {
    DialogDataLoader.loadAll().stream().forEach(this.dialogListModel::addElement);
    bindEnterToSubmit(this.inputA);
    bindEnterToSubmit(this.inputB);
    attachKeepFocusOnWorkField(this.inputA);
    attachKeepFocusOnWorkField(this.inputB);
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

  @Override
  public void onActivation() {
    dismissCompletionBanner();
    showCard(CARD_SELECT);
    SwingUtilities.invokeLater(() -> {
      if (this.dialogSelectionList != null) {
        this.dialogSelectionList.requestFocusInWindow();
      }
    });
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
    panel.add(this.progressHeader, BorderLayout.NORTH);

    final JPanel shows = new JPanel(new GridLayout(1, 2, 8, 8));
    shows.add(new JScrollPane(this.showA));
    shows.add(new JScrollPane(this.showB));
    panel.add(shows, BorderLayout.CENTER);

    final JPanel enterPanel = new JPanel(new GridLayout(1, 2, 8, 8));
    enterPanel.add(wrapInputScroll(this.inputA));
    enterPanel.add(wrapInputScroll(this.inputB));
    panel.add(enterPanel, BorderLayout.SOUTH);
    return panel;
  }

  private JTextArea makeShowArea() {
    final JTextArea area = new JTextArea();
    area.setEditable(false);
    area.setFocusable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setMargin(new Insets(8, 10, 8, 10));
    area.setOpaque(true);
    area.setFont(area.getFont().deriveFont(INPUT_FONT_SIZE));
    area.setPreferredSize(new Dimension(320, 220));
    return area;
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
        if (opposite instanceof JButton) {
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
    this.historyA.clear();
    this.historyB.clear();
    this.showA.setText("");
    this.showB.setText("");
    this.inputA.setText("");
    this.inputB.setText("");
    applyWorkbenchStyles(false);
    updateTargetTextForCurrentLine();
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
          .replace('\u0401', '\u0415')
          .replace('\u0451', '\u0435');
      final String lower = withYeFolded.toLowerCase();
      final StringBuilder builder = new StringBuilder(lower.length());
      lower
          .codePoints()
          .filter(code -> Character.isLetterOrDigit(code))
          .forEach(code -> builder.appendCodePoint(code));
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
