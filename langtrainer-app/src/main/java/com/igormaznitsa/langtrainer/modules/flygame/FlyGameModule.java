package com.igormaznitsa.langtrainer.modules.flygame;

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
import com.igormaznitsa.langtrainer.engine.LangTrainerApplication;
import com.igormaznitsa.langtrainer.engine.LangTrainerResourceAccess;
import com.igormaznitsa.langtrainer.engine.ResourceListSelectPanel;
import com.igormaznitsa.langtrainer.modules.dialog.InputEquivalenceSupport;
import com.igormaznitsa.langtrainer.text.TypingComparisonUtils;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
import java.util.List;
import java.util.Locale;
import java.util.Random;
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

public final class FlyGameModule extends AbstractLangTrainerModule {

  private static final String CARD_SELECT = "select";
  private static final String CARD_GAME = "game";

  /**
   * ~25 FPS; full-sky repaint each tick — keep modest to limit CPU while typing.
   */
  private static final int TIMER_MS = 50;
  /**
   * Cloud drift was originally tuned per tick at 25 ms; scale so motion stays consistent if {@link #TIMER_MS} changes.
   */
  private static final float CLOUD_DRIFT_REF_MS = 25f;
  private static final float FLIGHT_SECONDS = 20f;
  private static final int EXPLODE_FRAMES = 18;
  private static final int ANSWER_SHOW_MS = 5_000;

  private static final Color INPUT_TEXT_COLOR = Color.ORANGE.darker();
  private static final Color INPUT_TEXT_COLOR_SHADOW = Color.BLACK;

  private final DefaultListModel<DialogListEntry> listModel = new DefaultListModel<>();
  private final JPanel rootPanel = new JPanel(new java.awt.CardLayout());
  private final GameBoard gameBoard = new GameBoard(this);
  private JList<DialogListEntry> selectionList;
  private File lastOpenDir;

  public FlyGameModule() {
    ClasspathLangResourceIndex.loadShared(
            FlyGameModule.class, this, "Can't load fly game word lists")
        .forEach(d -> this.listModel.addElement(new DialogListEntry(d, false)));
    this.rootPanel.add(this.makeSelectPanel(), CARD_SELECT);
    this.rootPanel.add(this.gameBoard, CARD_GAME);
    this.showCard(CARD_SELECT);
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
        this::openFromFile);
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
      final int index = DialogListEntry.addOrReplaceByMenuTitle(
          this.listModel, new DialogListEntry(loaded, true));
      list.setSelectedIndex(index);
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

  private static final class GameBoard extends JPanel {

    private static final Color SKY = new Color(165, 215, 255);

    /**
     * Typed answer drawn on {@link SkyCanvas}; field itself is invisible but keeps focus.
     */
    private static final float FLY_INPUT_OVERLAY_FONT_PT = 40f;
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

    private final FlyGameModule host;
    private final SkyCanvas sky = new SkyCanvas();
    private final JTextField input = new JTextField();
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private final JButton btnClose = new JButton();
    private final JButton btnPause = new JButton();
    private final JToggleButton btnSound = new JToggleButton();
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
      northBar.add(this.btnPause);
      northBar.add(this.btnClose);
      this.status.setForeground(Color.WHITE);
      this.status.setFont(this.status.getFont().deriveFont(Font.BOLD, 16f));

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
      this.input.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(final KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            GameBoard.this.host.requestCloseToMainMenu();
          }
        }
      });
      this.input.addCaretListener(e -> this.repaintSkyFrame());
      this.attachFlyInputEquivalence();

      final JLabel hint = new JLabel("Press Enter to fire", SwingConstants.CENTER);
      hint.setForeground(new Color(240, 248, 255));
      hint.setFont(hint.getFont().deriveFont(Font.PLAIN, FLY_INPUT_HINT_FONT_PT));

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

    void appendChar(final char symbol) {
      this.input.replaceSelection(String.valueOf(symbol));
      this.input.requestFocusInWindow();
    }

    private void attachFlyInputEquivalence() {
      this.input.getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(final DocumentEvent event) {
          if (GameBoard.this.applyingInputEquivalence) {
            return;
          }
          final int start = event.getOffset();
          final int insertLen = event.getLength();
          SwingUtilities.invokeLater(() -> {
            try {
              GameBoard.this.applyingInputEquivalence = true;
              GameBoard.this.applyFlyInputEquivalence(start, insertLen);
            } finally {
              GameBoard.this.applyingInputEquivalence = false;
            }
          });
        }

        @Override
        public void removeUpdate(final DocumentEvent event) {
        }

        @Override
        public void changedUpdate(final DocumentEvent event) {
        }
      });
    }

    private void applyFlyInputEquivalence(final int start, final int insertLen) {
      if (this.dialog == null
          || this.showingAnswer
          || this.explodeFramesLeft > 0
          || insertLen <= 0) {
        return;
      }
      final List<InputEquivalenceRow> rules = this.dialog.inputEqu();
      if (rules.isEmpty()) {
        return;
      }
      final DialogLine line = this.dialog.lines().get(this.currentLineOrdinal);
      final String expectedFull = this.userTypesA ? line.a() : line.b();
      InputEquivalenceSupport.applyAfterInsert(this.input, expectedFull, rules, start, insertLen);
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
      label.setFont(label.getFont().deriveFont(Font.BOLD, VICTORY_BANNER_FONT_PT));
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
      if (this.paused || this.dialog == null) {
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
      if (this.answerDismissTimer != null) {
        this.answerDismissTimer.stop();
        this.answerDismissTimer = null;
      }
      this.showingAnswer = false;
      this.answerBannerText = "";
      this.heliProgress = 0f;
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
          final FontMetrics fm, final String typedUpper, final int maxLineW) {
        if (typedUpper.isEmpty() || fm.stringWidth(typedUpper) <= maxLineW) {
          return typedUpper;
        }
        final String ell = "…";
        String prefix = typedUpper;
        while (prefix.length() > 1 && fm.stringWidth(prefix + ell) > maxLineW) {
          prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
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
            g2.setFont(
                g2.getFont()
                    .deriveFont(Font.BOLD, SkyCanvas.scaleFont(w, 14f, 28f)));
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
        final String typedU = GameBoard.this.typedForAim();
        final String raw = GameBoard.this.input.getText();
        final int safeCaret =
            Math.min(Math.max(0, GameBoard.this.input.getCaretPosition()), raw.length());
        final String beforeCaretU = raw.substring(0, safeCaret).toUpperCase(Locale.ROOT);
        final float fontPt = SkyCanvas.scaleFont(w, 26f, FLY_INPUT_OVERLAY_FONT_PT);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontPt));
        final FontMetrics fm = g2.getFontMetrics();
        final int maxLineW = Math.max(40, w - 32);
        final String visiblePrefix = SkyCanvas.flyVisibleTypingPrefix(fm, typedU, maxLineW);
        final boolean truncated = visiblePrefix.length() < typedU.length();
        final String draw = truncated ? visiblePrefix + "…" : typedU;
        String beforeForBar = beforeCaretU;
        if (truncated && beforeCaretU.length() >= visiblePrefix.length()) {
          beforeForBar = visiblePrefix;
        }
        final int tw = fm.stringWidth(draw);
        final int tx = (w - tw) / 2;
        final int ty = (int) (h * 0.11);
        if (!draw.isEmpty()) {
          g2.setColor(INPUT_TEXT_COLOR_SHADOW);
          g2.drawString(draw, tx + 2, ty + 2);
          g2.setColor(INPUT_TEXT_COLOR);
          g2.drawString(draw, tx, ty);
        }
        final int prefixBeforeCaretW = fm.stringWidth(beforeForBar);
        final int barW = Math.max(4, fm.stringWidth("_"));
        final int barH = Math.max(2, fm.getDescent() / 2 + 1);
        final int barX = tx + prefixBeforeCaretW;
        final int barY = ty + 1;
        g2.setColor(INPUT_TEXT_COLOR_SHADOW);
        g2.fillRect(barX + 2, barY + 2, barW, barH);
        g2.setColor(INPUT_TEXT_COLOR);
        g2.fillRect(barX, barY, barW, barH);
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
        if (typed.isEmpty()) {
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
            g2.getFont()
                .deriveFont(Font.BOLD, SkyCanvas.scaleFont(w, 18f, 36f)));
        final String msg = "Answer: " + GameBoard.this.answerBannerText();
        final FontMetrics fm = g2.getFontMetrics();
        final int maxLineW = Math.max(120, w - 80);
        int y = h / 2 - fm.getHeight();
        for (final String line : SkyCanvas.wrapText(fm, msg, maxLineW)) {
          g2.drawString(line, (w - fm.stringWidth(line)) / 2, y);
          y += fm.getHeight() + 4;
        }
        g2.setFont(
            g2.getFont()
                .deriveFont(Font.PLAIN, SkyCanvas.scaleFont(w, 14f, 22f)));
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
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 32f));
        final String t = "PAUSED";
        final FontMetrics fm = g2.getFontMetrics();
        g2.drawString(t, (w - fm.stringWidth(t)) / 2, h / 2);
      }
    }
  }
}
