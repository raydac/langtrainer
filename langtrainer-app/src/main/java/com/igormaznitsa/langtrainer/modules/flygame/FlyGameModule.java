package com.igormaznitsa.langtrainer.modules.flygame;

import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;
import com.igormaznitsa.langtrainer.engine.ClasspathLangResourceIndex;
import com.igormaznitsa.langtrainer.engine.ClasspathResourceIndexTree;
import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.DialogLine;
import com.igormaznitsa.langtrainer.engine.DialogListEntry;
import com.igormaznitsa.langtrainer.engine.ImageResourceLoader;
import com.igormaznitsa.langtrainer.engine.InputEquivalenceRow;
import com.igormaznitsa.langtrainer.engine.LangResourceJson;
import com.igormaznitsa.langtrainer.engine.LangTrainerApplication;
import com.igormaznitsa.langtrainer.engine.LangTrainerResourceAccess;
import com.igormaznitsa.langtrainer.engine.ResourceListSelectPanel;
import com.igormaznitsa.langtrainer.modules.dialog.InputEquivalenceSupport;
import com.igormaznitsa.langtrainer.text.TypingComparisonUtils;
import com.igormaznitsa.langtrainer.ui.LangTrainerFonts;
import com.igormaznitsa.langtrainer.ui.PhraseFlashBanner;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public final class FlyGameModule extends AbstractLangTrainerModule {

  private static final String CARD_SELECT = "select";
  private static final String CARD_GAME = "game";

  private static final int TIMER_MS = 50;

  private static final float CLOUD_DRIFT_REF_MS = 25f;
  private static final float FLIGHT_SECONDS = 20f;
  private static final int EXPLODE_FRAMES = 18;
  private static final int ANSWER_SHOW_MS = 5_000;

  private static final Color INPUT_TEXT_COLOR = Color.ORANGE.darker();
  private static final Color INPUT_TEXT_COLOR_SHADOW = Color.BLACK;

  private final DefaultListModel<DialogListEntry> listModel = new DefaultListModel<>();
  private final Set<String> expandedClasspathFolders = new HashSet<>();
  private final List<DialogListEntry.DialogResourceRow> externalClasspathResourceRows =
      new ArrayList<>();
  private ClasspathResourceIndexTree classpathResourceTree;
  private final JPanel rootPanel = new JPanel(new java.awt.CardLayout());
  private final GameBoard gameBoard = new GameBoard(this);
  private JList<DialogListEntry> selectionList;
  private File lastOpenDir;

  public FlyGameModule() {
    this.classpathResourceTree =
        ClasspathLangResourceIndex.loadSharedTree(
            FlyGameModule.class, this, "Can't load fly game word lists");
    this.rebuildFlyResourceListModel();
    this.rootPanel.add(this.makeSelectPanel(), CARD_SELECT);
    this.rootPanel.add(this.gameBoard, CARD_GAME);
    this.showCard(CARD_SELECT);
  }

  private void rebuildFlyResourceListModel() {
    this.listModel.clear();
    this.classpathResourceTree.materializeInto(this.listModel, this.expandedClasspathFolders);
    for (final DialogListEntry.DialogResourceRow row : this.externalClasspathResourceRows) {
      this.listModel.addElement(row);
    }
  }

  private void onClasspathFolderRowClicked(final DialogListEntry.DialogFolderRow folder) {
    final String key = folder.pathKey();
    if (this.expandedClasspathFolders.contains(key)) {
      this.expandedClasspathFolders.remove(key);
    } else {
      this.expandedClasspathFolders.add(key);
    }
    this.rebuildFlyResourceListModel();
    final int rowIndex = DialogListEntry.indexOfFolderPathKey(this.listModel, key);
    if (this.selectionList != null && rowIndex >= 0) {
      this.selectionList.setSelectedIndex(rowIndex);
    }
  }

  @Override
  public boolean isResourceAllowed(final JsonObject resourceDescription) {
    return LangTrainerResourceAccess.visibleToModule(
        resourceDescription, LangTrainerModuleId.FLY_GAME);
  }

  @Override
  public String getName() {
    return "Words";
  }

  @Override
  public String getDescription() {
    return "Fly across words: type the answer before the aircraft leaves";
  }

  @Override
  public Icon getImage() {
    return ImageResourceLoader.loadIcon("/fly_game/images/module-fly-game.svg", 128, 128);
  }

  @Override
  public JComponent createControlForm() {
    return this.rootPanel;
  }

  @Override
  public List<KeyboardLanguage> getSupportedLanguages() {
    return KeyboardLanguage.VIRTUAL_BOARD_ALL;
  }

  @Override
  public void onCharClick(final char symbol) {
    this.gameBoard.appendChar(symbol);
  }

  @Override
  public void onActivation() {
    this.gameBoard.shutdownSession();
    this.showCard(CARD_SELECT);
    SwingUtilities.invokeLater(() -> {
      if (this.selectionList != null) {
        this.selectionList.requestFocusInWindow();
      }
    });
  }

  @Override
  public void onClose() {
    this.gameBoard.shutdownSession();
  }

  private void showCard(final String name) {
    ((java.awt.CardLayout) this.rootPanel.getLayout()).show(this.rootPanel, name);
    final boolean showToolbar = CARD_SELECT.equals(name);
    final Runnable syncHostToolbar = () -> this.syncMainFrameToolbarVisibility(showToolbar);
    if (this.rootPanel.getParent() != null) {
      syncHostToolbar.run();
    } else {
      SwingUtilities.invokeLater(syncHostToolbar);
    }
  }

  private void syncMainFrameToolbarVisibility(final boolean toolbarVisible) {
    final java.awt.Container parent = this.rootPanel.getParent();
    if (!(parent instanceof JComponent host)) {
      return;
    }
    final Object prop =
        host.getClientProperty(LangTrainerApplication.SET_TOOLBAR_VISIBLE_CLIENT_PROPERTY);
    if (prop instanceof Consumer<?> consumer) {
      @SuppressWarnings("unchecked") final Consumer<Boolean> setter = (Consumer<Boolean>) consumer;
      setter.accept(toolbarVisible);
    }
  }

  void enterGame(final DialogDefinition definition, final boolean userTypesA) {
    if (this.gameBoard.startSession(
        definition, userTypesA, () -> FlyGameModule.this.showCard(CARD_SELECT))) {
      this.showCard(CARD_GAME);
    }
  }

  private JPanel makeSelectPanel() {
    final ResourceListSelectPanel.Result view = ResourceListSelectPanel.build(
        this.listModel,
        ResourceListSelectPanel.Appearance.FLY_GAME,
        "Select word list",
        "Choose language and play",
        "Open from file",
        this::chooseLanguage,
        this::openFromFile,
        this::onClasspathFolderRowClicked);
    this.selectionList = view.list();
    return view.panel();
  }

  private void openFromFile(final JList<DialogListEntry> list) {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("JSON word list (*.json)", "json"));
    chooser.setAcceptAllFileFilterUsed(false);
    if (this.lastOpenDir != null) {
      chooser.setCurrentDirectory(this.lastOpenDir);
    }
    if (chooser.showOpenDialog(this.rootPanel) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    final File file = chooser.getSelectedFile();
    if (file == null) {
      return;
    }
    try {
      final DialogDefinition loaded = LangResourceJson.parseFromPath(file.toPath());
      DialogListEntry.mergeExternalResourceRow(
          this.externalClasspathResourceRows, DialogListEntry.externalResourceRow(loaded));
      this.rebuildFlyResourceListModel();
      final int index =
          DialogListEntry.indexOfExternalResourceMenuName(this.listModel, loaded.menuName());
      if (index >= 0) {
        list.setSelectedIndex(index);
      }
      final File parent = file.getParentFile();
      if (parent != null) {
        this.lastOpenDir = parent;
      }
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          ex.getMessage(),
          "Can't open file",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void chooseLanguage(final DialogDefinition definition) {
    final javax.swing.JComboBox<String> combo =
        new javax.swing.JComboBox<>(new String[] {definition.langA(), definition.langB()});
    final int opt = JOptionPane.showConfirmDialog(
        this.rootPanel,
        combo,
        "Type answers in which language?",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (opt == JOptionPane.OK_OPTION) {
      final String pick = (String) combo.getSelectedItem();
      final boolean userTypesA = definition.langA().equals(pick);
      this.enterGame(definition, userTypesA);
    }
  }

  void requestCloseToMainMenu() {
    final java.awt.Container parent = this.rootPanel.getParent();
    if (parent instanceof JComponent jc) {
      final Object handler =
          jc.getClientProperty(LangTrainerApplication.CLOSE_MODULE_CLIENT_PROPERTY);
      if (handler instanceof Runnable runnable) {
        runnable.run();
      }
    }
  }

  void requestShowVirtualKeyboard() {
    final java.awt.Container parent = this.rootPanel.getParent();
    if (parent instanceof JComponent jc) {
      final Object handler =
          jc.getClientProperty(LangTrainerApplication.SHOW_VIRTUAL_KEYBOARD_CLIENT_PROPERTY);
      if (handler instanceof Runnable runnable) {
        runnable.run();
      }
    }
  }

  private static final class GameBoard extends JPanel {

    private static final Color SKY = new Color(165, 215, 255);

    /**
     * Typed answer drawn on {@link SkyCanvas}; field itself is invisible but keeps focus.
     */
    private static final float FLY_INPUT_OVERLAY_FONT_PT = 40f;
    /**
     * Fixed horizontal gap (px) between each drawn character on the sky overlay so replacing
     * placeholders with typed letters does not shift the line (uniform tracking).
     */
    private static final int FLY_OVERLAY_INTER_CHAR_GAP_PX = 4;
    private static final float FLY_INPUT_HINT_FONT_PT = 17f;

    /**
     * Same palette as {@code DialogModule} completion banner.
     */
    private static final Color VICTORY_BANNER_BG = new Color(0, 75, 38);
    private static final Color VICTORY_BANNER_FG = new Color(255, 145, 30);
    private static final float VICTORY_BANNER_FONT_PT = 72f;

    private static final Icon ICON_FLY_CLOSE =
        ImageResourceLoader.loadIcon("/fly_game/images/fly-game-close.svg", 40, 40);
    private static final Icon ICON_FLY_PAUSE =
        ImageResourceLoader.loadIcon("/fly_game/images/fly-game-pause.svg", 40, 40);
    private static final Icon ICON_FLY_PLAY =
        ImageResourceLoader.loadIcon("/fly_game/images/fly-game-play.svg", 40, 40);
    private static final Icon ICON_FLY_SOUND_ON =
        ImageResourceLoader.loadIcon("/fly_game/images/fly-game-sound-on.svg", 40, 40);
    private static final Icon ICON_FLY_SOUND_OFF =
        ImageResourceLoader.loadIcon("/fly_game/images/fly-game-sound-off.svg", 40, 40);
    private static final Icon ICON_FLY_HINT =
        ImageResourceLoader.loadIcon("/fly_game/images/fly-game-hint.svg", 40, 40);
    private static final Icon ICON_FLY_KEYBOARD =
        ImageResourceLoader.loadIcon("/fly_game/images/fly-game-keyboard.svg", 40, 40);

    private final FlyGameModule host;
    private final SkyCanvas sky = new SkyCanvas();
    private final JTextField input = new JTextField();
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private final JButton btnClose = new JButton();
    private final JButton btnPause = new JButton();
    private final JButton btnShowPhrase = new JButton();
    private final JButton btnVirtualKeyboard = new JButton();
    private final JToggleButton btnSound = new JToggleButton();
    private final PhraseFlashBanner phraseFlashBanner = new PhraseFlashBanner();
    private final Random queueRandom = new Random();
    private FlyLeitnerSession leitner;
    private boolean soundEffectsEnabled = true;
    private final FlyGameSfx sfx = new FlyGameSfx(this::isSoundEffectsEnabled);
    private Timer animator;
    private Timer answerDismissTimer;
    private Runnable onVictoryReturnToSelect;
    private DialogDefinition dialog;
    private boolean userTypesA;
    private int currentLineOrdinal;
    private float heliProgress;
    private int explodeFramesLeft;
    private boolean paused;
    private boolean showingAnswer;
    private String answerBannerText = "";
    private JDialog victoryOverlay;
    private Timer victoryDismissTimer;
    private boolean applyingInputEquivalence;
    private boolean applyingFlyNormalization;
    /**
     * While the phrase flash banner is open, freeze flight, explosions, and cloud drift (same as
     * gameplay paused; separate from {@link #paused} so the pause control icon stays unchanged).
     */
    private boolean gameFrozenForPhraseBanner;

    GameBoard(final FlyGameModule host) {
      this.host = host;
      this.setLayout(new BorderLayout(0, 0));
      this.setOpaque(true);
      this.setBackground(SKY);

      final JPanel northBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
      northBar.setOpaque(false);
      northBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
      GameBoard.configureSvgButton(this.btnClose, ICON_FLY_CLOSE, "Close game");
      GameBoard.configureSvgButton(this.btnPause, ICON_FLY_PAUSE, "Pause / resume");
      GameBoard.configureSvgToggleSound(this.btnSound);
      GameBoard.configureSvgButton(
          this.btnShowPhrase,
          ICON_FLY_HINT,
          "Flash the answer and translation");
      GameBoard.configureSvgButton(
          this.btnVirtualKeyboard,
          ICON_FLY_KEYBOARD,
          "Show virtual keyboard");
      this.btnShowPhrase.addActionListener(e -> this.showCurrentWordPhraseBanner());
      this.btnVirtualKeyboard.addActionListener(e -> {
        this.host.requestShowVirtualKeyboard();
        this.refocusInputIfPlaying();
      });
      this.btnClose.addActionListener(e -> this.host.requestCloseToMainMenu());
      this.btnPause.addActionListener(e -> {
        this.togglePause();
        this.refocusInputIfPlaying();
      });
      this.btnSound.addActionListener(e -> {
        this.soundEffectsEnabled = this.btnSound.isSelected();
        this.refreshSoundToggleIcon();
        this.refocusInputIfPlaying();
      });
      this.refreshSoundToggleIcon();
      northBar.add(this.btnSound);
      northBar.add(this.btnShowPhrase);
      northBar.add(this.btnVirtualKeyboard);
      northBar.add(this.btnPause);
      northBar.add(this.btnClose);
      this.status.setForeground(Color.WHITE);
      this.status.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(16f));

      this.input.setHorizontalAlignment(JTextField.CENTER);
      this.input.setOpaque(false);
      this.input.setBackground(new Color(0, 0, 0, 0));
      this.input.setForeground(new Color(0, 0, 0, 0));
      this.input.setCaretColor(new Color(0, 0, 0, 0));
      this.input.setBorder(BorderFactory.createEmptyBorder());
      this.input.setPreferredSize(new Dimension(1, 1));
      this.input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
      this.input.addActionListener(e -> this.trySubmit());
      GameBoard.disableFlyInputCursorKeys(this.input);
      GameBoard.attachFlyAlphanumericDocumentFilter(this.input);
      this.input.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(final KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            GameBoard.this.host.requestCloseToMainMenu();
            return;
          }
          if (GameBoard.this.shouldConsumeDisallowedFlyInputKeyPressed(e)) {
            e.consume();
          }
        }

        @Override
        public void keyTyped(final KeyEvent e) {
          if (GameBoard.this.shouldConsumeDisallowedFlyInputKeyTyped(e)) {
            e.consume();
          }
        }
      });
      this.attachFlyInputNormalizationAndEquivalence();

      final JLabel hint = new JLabel("Press Enter to fire", SwingConstants.CENTER);
      hint.setForeground(new Color(240, 248, 255));
      hint.setFont(LangTrainerFonts.MONO_NL_REGULAR.atPoints(FLY_INPUT_HINT_FONT_PT));

      final JPanel tipStack = new JPanel(new BorderLayout(0, 4));
      tipStack.setOpaque(false);
      tipStack.add(hint, BorderLayout.NORTH);
      tipStack.add(this.status, BorderLayout.SOUTH);

      final JPanel southArea = new JPanel(new BorderLayout(0, 0));
      southArea.setOpaque(false);
      southArea.setBorder(BorderFactory.createEmptyBorder(4, 16, 8, 16));
      southArea.add(tipStack, BorderLayout.CENTER);
      southArea.add(this.input, BorderLayout.SOUTH);

      final JPanel centerCol = new JPanel(new BorderLayout(0, 0));
      centerCol.setOpaque(false);
      centerCol.add(this.sky, BorderLayout.CENTER);

      this.sky.setFocusable(false);
      this.sky.setRequestFocusEnabled(false);
      this.sky.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          GameBoard.this.refocusInputIfPlaying();
        }
      });

      this.add(northBar, BorderLayout.NORTH);
      this.add(centerCol, BorderLayout.CENTER);
      this.add(southArea, BorderLayout.SOUTH);
    }

    private static void configureSvgButton(final JButton button, final Icon icon,
                                           final String tip) {
      button.setIcon(icon);
      button.setToolTipText(tip);
      button.setOpaque(false);
      button.setContentAreaFilled(false);
      button.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
      button.setFocusPainted(false);
      button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void configureSvgToggleSound(final JToggleButton toggle) {
      toggle.setSelected(true);
      toggle.setText("");
      toggle.setOpaque(false);
      toggle.setContentAreaFilled(false);
      toggle.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
      toggle.setFocusPainted(false);
      toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void showCurrentWordPhraseBanner() {
      if (this.dialog == null) {
        return;
      }
      final Window owner = SwingUtilities.getWindowAncestor(this);
      if (owner == null) {
        return;
      }
      final DialogLine line = this.dialog.lines().get(this.currentLineOrdinal);
      final String expected = PhraseFlashBanner.normalizeLineBreaksForDisplay(
          this.userTypesA ? line.a() : line.b());
      final String partner = PhraseFlashBanner.normalizeLineBreaksForDisplay(
          this.userTypesA ? line.b() : line.a());
      this.gameFrozenForPhraseBanner = true;
      this.repaintSkyFrame();
      this.phraseFlashBanner.show(
          owner,
          expected,
          partner,
          () -> {
            this.gameFrozenForPhraseBanner = false;
            this.restartCurrentWordLearnCycle();
          });
    }

    private void restartCurrentWordLearnCycle() {
      if (this.answerDismissTimer != null) {
        this.answerDismissTimer.stop();
        this.answerDismissTimer = null;
      }
      this.showingAnswer = false;
      this.answerBannerText = "";
      this.explodeFramesLeft = 0;
      this.heliProgress = 0f;
      this.input.setText("");
      if (this.dialog == null || this.leitner == null || !this.leitner.hasWorkLeft()) {
        return;
      }
      if (this.animator == null) {
        this.animator = this.makeFrameAnimatorTimer();
      }
      this.animator.start();
      SwingUtilities.invokeLater(this.input::requestFocusInWindow);
      this.refreshPauseIcon();
      this.repaintSkyFrame();
    }

    private static void attachFlyAlphanumericDocumentFilter(final JTextField field) {
      final javax.swing.text.Document doc = field.getDocument();
      if (doc instanceof AbstractDocument ad) {
        ad.setDocumentFilter(new FlyAlphanumericDocumentFilter());
      }
    }

    private static void disableFlyInputCursorKeys(final JTextField field) {
      final InputMap im = field.getInputMap(JComponent.WHEN_FOCUSED);
      final Object none = "none";
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), none);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), none);
    }

    private boolean shouldConsumeDisallowedFlyInputKeyPressed(final KeyEvent e) {
      if (e.isControlDown() || e.isMetaDown()) {
        return true;
      }
      if (e.isAltDown()
          && e.getKeyCode() != KeyEvent.VK_ALT
          && e.getKeyCode() != KeyEvent.VK_ALT_GRAPH) {
        return true;
      }
      final int code = e.getKeyCode();
      if (code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_ENTER) {
        return false;
      }
      if (code == KeyEvent.VK_DELETE || code == KeyEvent.VK_TAB) {
        return true;
      }
      if (code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_PAGE_DOWN ||
          code == KeyEvent.VK_INSERT) {
        return true;
      }
      return code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F24;
    }

    private boolean shouldConsumeDisallowedFlyInputKeyTyped(final KeyEvent e) {
      final char ch = e.getKeyChar();
      if (ch == KeyEvent.CHAR_UNDEFINED) {
        return false;
      }
      if (ch == '\b') {
        return false;
      }
      if (Character.isISOControl(ch)) {
        return ch != '\n' && ch != '\r';
      }
      return !Character.isLetterOrDigit(ch);
    }

    void appendChar(final char symbol) {
      if (!Character.isLetterOrDigit(symbol)) {
        return;
      }
      this.input.replaceSelection(String.valueOf(symbol));
      this.input.requestFocusInWindow();
    }

    private void refocusInputIfPlaying() {
      if (this.dialog == null) {
        return;
      }
      SwingUtilities.invokeLater(this.input::requestFocusInWindow);
    }

    private boolean isSoundEffectsEnabled() {
      return this.soundEffectsEnabled;
    }

    private void refreshSoundToggleIcon() {
      this.btnSound.setIcon(
          this.btnSound.isSelected() ? ICON_FLY_SOUND_ON : ICON_FLY_SOUND_OFF);
      this.btnSound.setToolTipText(
          this.btnSound.isSelected() ? "Mute sound effects" : "Turn sound effects on");
    }

    private void attachFlyInputNormalizationAndEquivalence() {
      this.input.getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(final DocumentEvent event) {
          GameBoard.this.scheduleFlyDocumentProcessing();
        }

        @Override
        public void removeUpdate(final DocumentEvent event) {
          GameBoard.this.scheduleFlyDocumentProcessing();
        }

        @Override
        public void changedUpdate(final DocumentEvent event) {
        }
      });
    }

    private void scheduleFlyDocumentProcessing() {
      if (this.applyingFlyNormalization || this.applyingInputEquivalence) {
        return;
      }
      SwingUtilities.invokeLater(() -> {
        if (this.dialog == null || this.showingAnswer || this.explodeFramesLeft > 0) {
          return;
        }
        this.normalizeFlyDocument();
        try {
          this.applyingInputEquivalence = true;
          this.reapplyFlyInputEquivalenceWholeDocument();
        } finally {
          this.applyingInputEquivalence = false;
        }
        this.repaintSkyFrame();
      });
    }

    private void normalizeFlyDocument() {
      if (this.dialog == null || this.showingAnswer || this.explodeFramesLeft > 0) {
        return;
      }
      final DialogLine line = this.dialog.lines().get(this.currentLineOrdinal);
      final String expected = this.userTypesA ? line.a() : line.b();
      final String raw = this.input.getText();
      final int caret = Math.min(this.input.getCaretPosition(), raw.length());
      final int lettersBefore = FlyTypingSlotFormatter.countLetterDigitsBefore(raw, caret);
      final String merged = FlyTypingSlotFormatter.mergeLettersIntoExpected(expected, raw);
      if (merged.equals(raw)) {
        return;
      }
      this.applyingFlyNormalization = true;
      final javax.swing.text.Document doc = this.input.getDocument();
      DocumentFilter savedFilter = null;
      final AbstractDocument abstractDoc = doc instanceof AbstractDocument d ? d : null;
      if (abstractDoc != null) {
        savedFilter = abstractDoc.getDocumentFilter();
        abstractDoc.setDocumentFilter(null);
      }
      try {
        this.input.setText(merged);
        int newCaret =
            FlyTypingSlotFormatter.caretAfterNthLetterSlot(merged, lettersBefore);
        final int docLen = this.input.getText().length();
        newCaret = Math.max(0, Math.min(newCaret, docLen));
        this.input.setCaretPosition(newCaret);
      } finally {
        if (abstractDoc != null) {
          abstractDoc.setDocumentFilter(savedFilter);
        }
        this.applyingFlyNormalization = false;
      }
    }

    private void reapplyFlyInputEquivalenceWholeDocument() {
      if (this.dialog == null) {
        return;
      }
      final List<InputEquivalenceRow> rules = this.dialog.inputEqu();
      if (rules.isEmpty()) {
        return;
      }
      final DialogLine line = this.dialog.lines().get(this.currentLineOrdinal);
      final String expectedFull = this.userTypesA ? line.a() : line.b();
      final String doc = this.input.getText();
      if (doc.isEmpty()) {
        return;
      }
      InputEquivalenceSupport.applyAfterInsert(this.input, expectedFull, rules, 0, doc.length());
    }

    private static final class FlyAlphanumericDocumentFilter extends DocumentFilter {

      private static String filterLetterDigits(final String s) {
        final StringBuilder builder = new StringBuilder(s.length());
        s.codePoints().filter(Character::isLetterOrDigit).forEach(builder::appendCodePoint);
        return builder.toString();
      }

      @Override
      public void insertString(
          final FilterBypass fb,
          final int offset,
          final String string,
          final AttributeSet attr) throws BadLocationException {
        if (string == null || string.isEmpty()) {
          return;
        }
        final String filtered = FlyAlphanumericDocumentFilter.filterLetterDigits(string);
        if (!filtered.isEmpty()) {
          super.insertString(fb, offset, filtered, attr);
        }
      }

      @Override
      public void replace(
          final FilterBypass fb,
          final int offset,
          final int length,
          final String text,
          final AttributeSet attrs) throws BadLocationException {
        final String insert =
            text == null ? "" : FlyAlphanumericDocumentFilter.filterLetterDigits(text);
        super.replace(fb, offset, length, insert, attrs);
      }
    }

    boolean startSession(
        final DialogDefinition definition,
        final boolean userTypesAFlag,
        final Runnable returnToSelect) {
      this.shutdownSession();
      this.dialog = definition;
      this.userTypesA = userTypesAFlag;
      this.onVictoryReturnToSelect = returnToSelect;
      final int lineCount = definition.lines().size();
      if (lineCount == 0) {
        JOptionPane.showMessageDialog(this, "Word list is empty.", "Fly game",
            JOptionPane.WARNING_MESSAGE);
        this.dialog = null;
        return false;
      }
      this.leitner = new FlyLeitnerSession(lineCount, this.queueRandom);
      this.currentLineOrdinal = this.leitner.nextOrdinalToFly();
      this.heliProgress = 0f;
      this.explodeFramesLeft = 0;
      this.paused = false;
      this.showingAnswer = false;
      this.answerBannerText = "";
      this.input.setText("");
      this.sky.initClouds();
      this.refreshStatus();
      this.animator = this.makeFrameAnimatorTimer();
      this.animator.start();
      this.refreshPauseIcon();
      this.sfx.play(FlyGameSfx.AIR_START);
      SwingUtilities.invokeLater(this.input::requestFocusInWindow);
      return true;
    }

    void shutdownSession() {
      this.dismissVictoryBanner();
      this.phraseFlashBanner.dismiss();
      this.gameFrozenForPhraseBanner = false;
      if (this.animator != null) {
        this.animator.stop();
        this.animator = null;
      }
      if (this.answerDismissTimer != null) {
        this.answerDismissTimer.stop();
        this.answerDismissTimer = null;
      }
      this.dialog = null;
      this.leitner = null;
    }

    private void dismissVictoryBanner() {
      if (this.victoryDismissTimer != null) {
        this.victoryDismissTimer.stop();
        this.victoryDismissTimer = null;
      }
      if (this.victoryOverlay != null) {
        this.victoryOverlay.dispose();
        this.victoryOverlay = null;
      }
    }

    private void showVictoryBannerOverlay() {
      final Window owner = SwingUtilities.getWindowAncestor(this);
      if (owner == null) {
        if (this.onVictoryReturnToSelect != null) {
          this.onVictoryReturnToSelect.run();
        }
        return;
      }
      this.dismissVictoryBanner();
      final JDialog overlay = new JDialog(owner, Dialog.ModalityType.MODELESS);
      this.victoryOverlay = overlay;
      overlay.setUndecorated(true);
      overlay.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

      final JPanel pane = new JPanel(new BorderLayout());
      pane.setOpaque(true);
      pane.setBackground(VICTORY_BANNER_BG);
      pane.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
      overlay.setContentPane(pane);

      final Runnable closeAndReturn = () -> {
        GameBoard.this.dismissVictoryBanner();
        if (this.onVictoryReturnToSelect != null) {
          this.onVictoryReturnToSelect.run();
        }
      };

      final JLabel label = new JLabel("Hurraa! Completed!", SwingConstants.CENTER);
      label.setFont(LangTrainerFonts.MONO_NL_EXTRA_BOLD.atPoints(VICTORY_BANNER_FONT_PT));
      label.setForeground(VICTORY_BANNER_FG);
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

      this.victoryDismissTimer = new Timer(10_000, event -> closeAndReturn.run());
      this.victoryDismissTimer.setRepeats(false);
      this.victoryDismissTimer.start();

      SwingUtilities.invokeLater(() -> overlay.setVisible(true));
    }

    private void togglePause() {
      if (this.animator == null) {
        return;
      }
      this.paused = !this.paused;
      this.refreshPauseIcon();
      this.repaintSkyFrame();
    }

    private void refreshPauseIcon() {
      this.btnPause.setIcon(this.paused ? ICON_FLY_PLAY : ICON_FLY_PAUSE);
      this.btnPause.setToolTipText(this.paused ? "Resume" : "Pause");
    }

    private Timer makeFrameAnimatorTimer() {
      final Timer timer = new Timer(TIMER_MS, this::tick);
      timer.setCoalesce(false);
      return timer;
    }

    /**
     * Prefer synchronous paint: async {@code repaint()} can lag on X11 until input wakes the EDT.
     */
    private void repaintSkyFrame() {
      if (this.sky.isShowing()) {
        final Rectangle vr = this.sky.getVisibleRect();
        if (vr.width > 0 && vr.height > 0) {
          this.sky.paintImmediately(vr.x, vr.y, vr.width, vr.height);
          return;
        }
      }
      this.sky.repaint();
    }

    private void tick(final ActionEvent e) {
      if (this.paused || this.dialog == null || this.gameFrozenForPhraseBanner) {
        return;
      }
      if (this.explodeFramesLeft > 0) {
        this.explodeFramesLeft--;
        if (this.explodeFramesLeft == 0) {
          this.advanceAfterHit();
        }
        this.repaintSkyFrame();
        return;
      }
      if (this.showingAnswer) {
        return;
      }
      final float delta = TIMER_MS / 1000f / FLIGHT_SECONDS;
      this.heliProgress += delta;
      this.sky.advanceClouds();
      if (this.heliProgress >= 1f) {
        this.onMissedDeadline();
      }
      this.repaintSkyFrame();
    }

    private void onMissedDeadline() {
      this.sfx.play(FlyGameSfx.CAT_ANGRY);
      if (this.leitner != null) {
        this.leitner.registerFailure(this.currentLineOrdinal);
      }
      if (this.animator != null) {
        this.animator.stop();
        this.animator = null;
      }
      final DialogLine line = this.dialog.lines().get(this.currentLineOrdinal);
      final String expected = this.userTypesA ? line.a() : line.b();
      this.answerBannerText = expected.toUpperCase(Locale.ROOT);
      this.showingAnswer = true;
      this.input.setText("");
      this.repaintSkyFrame();
      this.answerDismissTimer = new Timer(ANSWER_SHOW_MS, ev -> this.resumeSameWord());
      this.answerDismissTimer.setRepeats(false);
      this.answerDismissTimer.start();
    }

    private void resumeSameWord() {
      this.restartCurrentWordLearnCycle();
    }

    private void trySubmit() {
      if (this.dialog == null || this.explodeFramesLeft > 0 || this.showingAnswer) {
        return;
      }
      final DialogLine line = this.dialog.lines().get(this.currentLineOrdinal);
      final String expected = this.userTypesA ? line.a() : line.b();
      final String typed = this.input.getText();
      if (TypingComparisonUtils.isCloseEnough(typed, expected)) {
        this.sfx.play(FlyGameSfx.EXPLOSION);
        this.explodeFramesLeft = EXPLODE_FRAMES;
        this.input.setText("");
      } else {
        this.sfx.play(FlyGameSfx.MISFIRE);
        if (this.leitner != null) {
          this.leitner.registerFailure(this.currentLineOrdinal);
        }
      }
      this.repaintSkyFrame();
    }

    private void advanceAfterHit() {
      final boolean allDone =
          this.leitner == null || this.leitner.registerCorrect(this.currentLineOrdinal);
      if (allDone) {
        this.sfx.play(FlyGameSfx.ENDGAME);
        this.shutdownSession();
        this.showVictoryBannerOverlay();
        return;
      }
      this.currentLineOrdinal = this.leitner.nextOrdinalToFly();
      this.heliProgress = 0f;
      this.refreshStatus();
      this.sfx.play(FlyGameSfx.AIR_START);
      SwingUtilities.invokeLater(this.input::requestFocusInWindow);
    }

    private void refreshStatus() {
      if (this.dialog == null) {
        this.status.setText(" ");
        return;
      }
      if (this.leitner == null) {
        this.status.setText(this.dialog.menuName());
        return;
      }
      this.status.setText(
          String.format(
              Locale.ROOT,
              "%s · Bucket %d/%d · %d left",
              this.dialog.menuName(),
              this.leitner.currentBucketOneBased(),
              this.leitner.bucketCount(),
              this.leitner.wordsRemainingInActiveBucket()));
    }

    String currentPrompt() {
      if (this.dialog == null) {
        return "";
      }
      final DialogLine line = this.dialog.lines().get(this.currentLineOrdinal);
      final String raw = this.userTypesA ? line.b() : line.a();
      return raw.toUpperCase(Locale.ROOT);
    }

    String currentExpected() {
      if (this.dialog == null) {
        return "";
      }
      final DialogLine line = this.dialog.lines().get(this.currentLineOrdinal);
      final String raw = this.userTypesA ? line.a() : line.b();
      return raw.toUpperCase(Locale.ROOT);
    }

    float heliProgress() {
      return this.heliProgress;
    }

    int explodeFramesLeft() {
      return this.explodeFramesLeft;
    }

    boolean showingAnswer() {
      return this.showingAnswer;
    }

    String answerBannerText() {
      return this.answerBannerText;
    }

    /**
     * Uppercase form of the field text for on-screen typing hint, aim logic, and overlays. Raw
     * text stays in the field for {@code inputEqu}; {@link TypingComparisonUtils} compares answers
     * case-insensitively.
     */
    String typedForAim() {
      return this.input.getText().toUpperCase(Locale.ROOT);
    }

    boolean isPaused() {
      return this.paused;
    }

    private static final class Cloud {
      final double y;
      final double speed;
      double x;

      Cloud(final double x, final double y, final double speed) {
        this.x = x;
        this.y = y;
        this.speed = speed;
      }
    }

    private final class SkyCanvas extends JPanel {

      private final List<Cloud> clouds = new ArrayList<>();
      private final Random cloudRandom = new Random();
      private Image heliImage;
      private Image cloudImage;
      private Image aimImage;
      private int cachedImgScale;
      private int cachedAimPx;

      SkyCanvas() {
        this.setOpaque(false);
      }

      private static int aimRasterPixels(final int panelH) {
        final int arm = Math.max(18, Math.min(40, panelH / 18));
        return Math.min(128, arm * 2 + 12);
      }

      private static float scaleFont(final int panelW, final float minPt, final float maxPt) {
        return Math.min(maxPt, Math.max(minPt, panelW / 28f));
      }

      private static String flyVisibleTypingPrefix(
          final FontMetrics fm, final String line, final int maxLineW, final int interCharGapPx) {
        if (line.isEmpty()
            || SkyCanvas.overlayLineWidthWithInterCharGaps(fm, line, interCharGapPx) <= maxLineW) {
          return line;
        }
        final String ell = "…";
        String prefix = line;
        while (prefix.length() > 1
            && SkyCanvas.overlayLineWidthWithInterCharGaps(fm, prefix + ell, interCharGapPx)
            > maxLineW) {
          prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
      }

      private static int overlayLineWidthWithInterCharGaps(
          final FontMetrics fm, final String s, final int interCharGapPx) {
        if (s == null || s.isEmpty()) {
          return 0;
        }
        int px = 0;
        int i = 0;
        boolean first = true;
        while (i < s.length()) {
          final int len = Character.charCount(s.codePointAt(i));
          if (!first) {
            px += interCharGapPx;
          }
          first = false;
          px += fm.stringWidth(s.substring(i, i + len));
          i += len;
        }
        return px;
      }

      private static void drawOverlayLineWithInterCharGaps(
          final Graphics2D g2,
          final String s,
          final int x,
          final int y,
          final FontMetrics fm,
          final int interCharGapPx) {
        if (s == null || s.isEmpty()) {
          return;
        }
        int cx = x;
        int i = 0;
        boolean first = true;
        while (i < s.length()) {
          final int len = Character.charCount(s.codePointAt(i));
          if (!first) {
            cx += interCharGapPx;
          }
          first = false;
          final String piece = s.substring(i, i + len);
          g2.drawString(piece, cx, y);
          cx += fm.stringWidth(piece);
          i += len;
        }
      }

      private static List<String> wrapText(final FontMetrics fm, final String text,
                                           final int maxWidth) {
        final List<String> lines = new ArrayList<>();
        final String[] words = text.split("\\s+");
        StringBuilder row = new StringBuilder();
        for (final String word : words) {
          if (fm.stringWidth(word) > maxWidth) {
            if (!row.isEmpty()) {
              lines.add(row.toString());
              row = new StringBuilder();
            }
            lines.addAll(SkyCanvas.breakLongWord(fm, word, maxWidth));
            continue;
          }
          final String trial = row.isEmpty() ? word : row + " " + word;
          if (fm.stringWidth(trial) <= maxWidth) {
            row = new StringBuilder(trial);
          } else {
            if (!row.isEmpty()) {
              lines.add(row.toString());
            }
            row = new StringBuilder(word);
          }
        }
        if (!row.isEmpty()) {
          lines.add(row.toString());
        }
        if (lines.isEmpty()) {
          lines.add("");
        }
        return lines;
      }

      private static List<String> breakLongWord(final FontMetrics fm, final String word,
                                                final int maxWidth) {
        final List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < word.length()) {
          int j = i + 1;
          while (j <= word.length() && fm.stringWidth(word.substring(i, j)) <= maxWidth) {
            j++;
          }
          if (j == i + 1) {
            j++;
          }
          parts.add(word.substring(i, j - 1));
          i = j - 1;
        }
        return parts;
      }

      void initClouds() {
        this.clouds.clear();
        for (int i = 0; i < 5; i++) {
          this.clouds.add(new Cloud(
              0.15 + this.cloudRandom.nextDouble() * 0.95,
              0.08 + this.cloudRandom.nextDouble() * 0.35,
              0.0007 + this.cloudRandom.nextDouble() * 0.0009));
        }
      }

      /**
       * Drift clouds right → left (opposite to the helicopter). {@link Cloud#speed} is a small
       * positive fraction of panel width advanced per animation tick.
       */
      void advanceClouds() {
        final float driftScale = TIMER_MS / CLOUD_DRIFT_REF_MS;
        for (final Cloud c : this.clouds) {
          c.x -= c.speed * driftScale;
          if (c.x < -0.28) {
            c.x = 1.02 + this.cloudRandom.nextDouble() * 0.45;
          }
        }
      }

      private void ensureRasterSize() {
        final int w = Math.max(1, this.getWidth());
        final int target = Math.max(56, w / 10);
        if (this.heliImage == null || Math.abs(this.cachedImgScale - target) > 16) {
          this.cachedImgScale = target;
          final int planeH = Math.max(24, (int) Math.round(target * 155.52629 / 323.33826));
          this.heliImage =
              ImageResourceLoader.loadImage("/fly_game/images/plane.svg", target, planeH);
          this.cloudImage =
              ImageResourceLoader.loadImage("/fly_game/images/fly-game-cloud.svg", target * 4 / 3,
                  target / 2);
        }
      }

      private void ensureAimRasterSize(final int panelH) {
        final int target = SkyCanvas.aimRasterPixels(panelH);
        if (this.aimImage == null || Math.abs(this.cachedAimPx - target) > 8) {
          this.cachedAimPx = target;
          this.aimImage = ImageResourceLoader.loadImage("/fly_game/images/aim.svg", target, target);
        }
      }

      @Override
      protected void paintComponent(final Graphics g) {
        super.paintComponent(g);
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
          ImageResourceLoader.applyHighQualityDrawingHints(g2);
          final int w = this.getWidth();
          final int h = this.getHeight();
          if (w <= 0 || h <= 0) {
            return;
          }
          g2.setColor(SKY);
          g2.fillRect(0, 0, w, h);

          this.ensureRasterSize();
          final int cw = this.cloudImage.getWidth(null);
          final int ch = this.cloudImage.getHeight(null);
          for (final Cloud c : this.clouds) {
            final int x = (int) (c.x * w);
            final int y = (int) (c.y * h);
            g2.drawImage(this.cloudImage, x, y, cw, ch, null);
          }

          final float t = GameBoard.this.heliProgress();
          final int margin = this.heliImage.getWidth(null) / 2;
          final int hx = (int) (-margin + t * (w + 2 * margin));
          final int hy = (int) (h * 0.72 - t * h * 0.28);
          final int hw = this.heliImage.getWidth(null);
          final int hh = this.heliImage.getHeight(null);

          g2.drawImage(this.heliImage, hx - hw / 2, hy - hh / 2, null);

          final String prompt = GameBoard.this.currentPrompt();
          if (!prompt.isEmpty()) {
            g2.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(SkyCanvas.scaleFont(w, 14f, 28f)));
            final FontMetrics fm = g2.getFontMetrics();
            final int pw = fm.stringWidth(prompt);
            final int px = Math.max(8, Math.min(w - pw - 8, hx - pw / 2));
            final int py = Math.max(fm.getHeight() + 8, hy - hh / 2 - 10);
            g2.setColor(new Color(255, 255, 255, 220));
            g2.drawString(prompt, px + 2, py + 2);
            g2.setColor(new Color(12, 42, 82));
            g2.drawString(prompt, px, py);
          }

          if (!GameBoard.this.showingAnswer()) {
            this.drawTypedAnswerOverlay(g2, w, h);
          }

          this.drawAim(g2, w, h, hx, hy);

          if (GameBoard.this.explodeFramesLeft() > 0) {
            this.drawExplosion(g2, hx, hy, hw, hh);
          }

          if (GameBoard.this.showingAnswer()) {
            this.drawAnswerOverlay(g2, w, h);
          }
          if (GameBoard.this.isPaused()) {
            this.drawPausedVeil(g2, w, h);
          }
        } finally {
          g2.dispose();
        }
      }

      private void drawTypedAnswerOverlay(final Graphics2D g2, final int w, final int h) {
        if (GameBoard.this.dialog == null) {
          return;
        }
        final DialogLine line =
            GameBoard.this.dialog.lines().get(GameBoard.this.currentLineOrdinal);
        final String expected = GameBoard.this.userTypesA ? line.a() : line.b();
        final String merged = GameBoard.this.input.getText();
        final String displayFull = FlyTypingSlotFormatter.overlayDisplayUpper(expected, merged);
        final float fontPt = SkyCanvas.scaleFont(w, 26f, FLY_INPUT_OVERLAY_FONT_PT);
        g2.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(fontPt));
        final FontMetrics fm = g2.getFontMetrics();
        final int maxLineW = Math.max(40, w - 32);
        final String visiblePrefix =
            SkyCanvas.flyVisibleTypingPrefix(
                fm, displayFull, maxLineW, FLY_OVERLAY_INTER_CHAR_GAP_PX);
        final boolean truncated = visiblePrefix.length() < displayFull.length();
        final String draw = truncated ? visiblePrefix + "…" : displayFull;
        final int tw =
            SkyCanvas.overlayLineWidthWithInterCharGaps(fm, draw, FLY_OVERLAY_INTER_CHAR_GAP_PX);
        final int tx = (w - tw) / 2;
        final int ty = (int) (h * 0.11);
        if (!draw.isEmpty()) {
          g2.setColor(INPUT_TEXT_COLOR_SHADOW);
          SkyCanvas.drawOverlayLineWithInterCharGaps(
              g2, draw, tx + 2, ty + 2, fm, FLY_OVERLAY_INTER_CHAR_GAP_PX);
          g2.setColor(INPUT_TEXT_COLOR);
          SkyCanvas.drawOverlayLineWithInterCharGaps(
              g2, draw, tx, ty, fm, FLY_OVERLAY_INTER_CHAR_GAP_PX);
        }
      }

      private void drawAim(
          final Graphics2D g2,
          final int w,
          final int h,
          final int heliCx,
          final int heliCy) {
        final String typed = GameBoard.this.typedForAim();
        final String expected = GameBoard.this.currentExpected();
        final int maxRadius =
            Math.min(Math.min(w * 2 / 5, 340), Math.min(h * 2 / 5, 340));
        final int aimX;
        final int aimY;
        if (GameBoard.this.explodeFramesLeft() > 0) {
          aimX = heliCx;
          aimY = heliCy;
        } else if (typed.isEmpty()) {
          aimX = w / 2;
          aimY = h / 2;
        } else if (TypingComparisonUtils.isCloseEnough(typed, expected)) {
          aimX = heliCx;
          aimY = heliCy;
        } else {
          final int edits = TypingComparisonUtils.editDistance(typed, expected);
          final int signX = (expected.hashCode() & 1) != 0 ? 1 : -1;
          final int signY = (typed.hashCode() & 1) != 0 ? 1 : -1;
          final long mix = (long) typed.hashCode() * 31L + expected.hashCode();
          final double theta = (Math.floorMod(mix, 899) + 1) / 900.0 * (Math.PI / 2);
          final double radiusPx =
              Math.min(Math.max(edits * 30.0, edits > 0 ? 40.0 : 20.0), maxRadius);
          aimX = heliCx + (int) Math.round(signX * radiusPx * Math.cos(theta));
          aimY = heliCy + (int) Math.round(signY * radiusPx * Math.sin(theta));
        }
        this.ensureAimRasterSize(h);
        final int aw = this.aimImage.getWidth(null);
        final int ah = this.aimImage.getHeight(null);
        final int halfW = aw / 2;
        final int halfH = ah / 2;
        final int drawX = Math.max(halfW + 2, Math.min(w - halfW - 2, aimX));
        final int drawY = Math.max(halfH + 2, Math.min(h - halfH - 2, aimY));
        g2.drawImage(this.aimImage, drawX - halfW, drawY - halfH, null);
      }

      private void drawExplosion(final Graphics2D g2, final int hx, final int hy, final int hw,
                                 final int hh) {
        final float p =
            1f - (float) GameBoard.this.explodeFramesLeft() / (float) FlyGameModule.EXPLODE_FRAMES;
        final int r = (int) (Math.max(hw, hh) * (0.4 + p * 1.4));
        g2.setComposite(
            AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f * (1f - p * 0.7f)));
        g2.setColor(new Color(255, 200, 40));
        g2.fill(new Ellipse2D.Double(hx - r / 2.0, hy - r / 2.0, r, r));
        g2.setColor(new Color(255, 80, 0));
        g2.fill(new Ellipse2D.Double(hx - r / 3.0, hy - r / 3.0, r * 2 / 3.0, r * 2 / 3.0));
        g2.setComposite(AlphaComposite.SrcOver);
      }

      private void drawAnswerOverlay(final Graphics2D g2, final int w, final int h) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
        g2.setColor(new Color(10, 30, 70));
        g2.fillRect(0, 0, w, h);
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(Color.WHITE);
        g2.setFont(
            LangTrainerFonts.MONO_NL_BOLD.atPoints(SkyCanvas.scaleFont(w, 18f, 36f)));
        final String msg = "Answer: " + GameBoard.this.answerBannerText();
        final FontMetrics fm = g2.getFontMetrics();
        final int maxLineW = Math.max(120, w - 80);
        int y = h / 2 - fm.getHeight();
        for (final String line : SkyCanvas.wrapText(fm, msg, maxLineW)) {
          g2.drawString(line, (w - fm.stringWidth(line)) / 2, y);
          y += fm.getHeight() + 4;
        }
        g2.setFont(LangTrainerFonts.MONO_NL_REGULAR.atPoints(SkyCanvas.scaleFont(w, 14f, 22f)));
        final FontMetrics fm2 = g2.getFontMetrics();
        final String sub = "Next flight in a moment…";
        g2.drawString(sub, (w - fm2.stringWidth(sub)) / 2, y + fm2.getHeight());
      }

      private void drawPausedVeil(final Graphics2D g2, final int w, final int h) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, w, h);
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(Color.WHITE);
        g2.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(32f));
        final String t = "PAUSED";
        final FontMetrics fm = g2.getFontMetrics();
        g2.drawString(t, (w - fm.stringWidth(t)) / 2, h / 2);
      }
    }
  }
}
