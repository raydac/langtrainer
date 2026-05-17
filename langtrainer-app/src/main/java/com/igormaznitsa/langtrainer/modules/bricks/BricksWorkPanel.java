package com.igormaznitsa.langtrainer.modules.bricks;

import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.DialogLine;
import com.igormaznitsa.langtrainer.ui.LangTrainerFonts;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

final class BricksWorkPanel extends JPanel implements BricksFieldCanvas.FieldHost {

  private static final Color POOL_BRICK_BG = new Color(173, 216, 230);
  private static final Color BUILD_PARTIAL_BG = new Color(255, 249, 196);
  private static final Color BUILD_WRONG_BG = new Color(255, 183, 183);
  private static final Color BUILD_CORRECT_BG = new Color(165, 214, 167);
  private static final Color ZONE_BORDER = new Color(90, 90, 120);
  private static final Color CANVAS_BG = new Color(248, 250, 252);
  private static final Color BUILD_ZONE_BG = new Color(255, 244, 232);
  private static final Color BUILD_ZONE_LINE = new Color(210, 175, 130);
  private static final Color POOL_ZONE_BG = new Color(234, 244, 255);
  private static final Color POOL_ZONE_LINE = new Color(150, 185, 218);
  private static final Color HISTORY_ZONE_BG = new Color(234, 246, 235);
  private static final Color HISTORY_ZONE_LINE = new Color(145, 188, 155);
  private static final Color BRICK_STROKE = new Color(70, 100, 130);
  private static final float BRICK_FONT_PT = 19f;
  private static final float CUE_LABEL_FONT_PT = 22f;
  private static final int MAX_HISTORY_LINES = 40;
  private static final int HISTORY_LINE_VERTICAL_GAP_PX = 6;
  private static final int DRAG_THRESHOLD_PX = 8;
  private static final int COMPLETION_OPEN_SLOT_STEPS = 16;
  private static final int COMPLETION_OPEN_SLOT_TICK_MS = 14;
  private static final int COMPLETION_FLY_STEPS = 22;
  private static final int COMPLETION_FLY_TICK_MS = 16;
  private static final int BRICK_FLOW_H_GAP = BrickImageRenderer.BRICK_FLOW_H_GAP;
  private static final long DRAG_AWT_MASK =
      AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK;

  private final Runnable onExitToSelect;
  private final JLabel cueLabel = new JLabel(" ", SwingConstants.CENTER);
  private final JPanel dropCanvas;
  private final JPanel poolBuildStack;
  private final JPanel historyZone;
  private final JPanel historyBrickList = new JPanel();
  private final JLayeredPane historyStackLayered = new JLayeredPane();
  private final HistoryBottomFadeOverlay historyBottomFade;
  private final JLayeredPane layered = new JLayeredPane();
  private final BricksFieldCanvas fieldCanvas;
  /**
   * Flies solved phrase from build strip into history.
   */
  private final JLabel completionFlyGhost = new JLabel();
  private final Random random = new Random();
  private final List<Integer> buildIds = new ArrayList<>();
  private final List<Integer> poolIds = new ArrayList<>();
  private final Deque<String> historyLines = new ArrayDeque<>();
  private Timer completionFlyTimer;
  private JPanel completionAnimTopSpacer;
  private DialogDefinition definition;
  private boolean userBuildsSideA;
  private List<DialogLine> exercises = List.of();
  private int exerciseIndex;
  private List<String> wordTokens = List.of();
  private String fixedEndSuffix;
  private String cueLineText = "";
  private String targetLineText = "";
  private int pendingBrickId = -1;

  private Point pendingPressScreen;
  private float pendingGrabFracX;
  private float pendingGrabFracY;
  private int draggingId = -1;
  private boolean pendingPromotePosted;  private final AWTEventListener globalDragMouseListener = this::onGlobalMouseWhileDragging;
  private Timer dragGhostPollTimer;
  private JPanel dragRootGlass;
  private Component savedGlassPane;
  private boolean savedGlassWasVisible;
  BricksWorkPanel(final Runnable onExitToSelect) {
    this.onExitToSelect = Objects.requireNonNull(onExitToSelect, "onExitToSelect");
    this.setLayout(new BorderLayout(8, 8));
    this.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    this.cueLabel.setOpaque(true);
    this.cueLabel.setBackground(new Color(245, 248, 252));
    this.cueLabel.setForeground(new Color(25, 45, 85));
    this.cueLabel.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(CUE_LABEL_FONT_PT));
    this.cueLabel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(ZONE_BORDER, 1, true),
        BorderFactory.createEmptyBorder(12, 14, 12, 14)));

    this.fieldCanvas =
        new BricksFieldCanvas(
            POOL_ZONE_BG,
            POOL_ZONE_LINE,
            BUILD_ZONE_BG,
            BUILD_ZONE_LINE,
            POOL_BRICK_BG,
            BUILD_PARTIAL_BG);
    this.fieldCanvas.setHost(this);
    this.fieldCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    this.poolBuildStack = new JPanel(new BorderLayout(0, 0));
    this.poolBuildStack.setOpaque(false);
    this.poolBuildStack.add(this.fieldCanvas, BorderLayout.CENTER);

    this.historyBrickList.setLayout(new BoxLayout(this.historyBrickList, BoxLayout.Y_AXIS));
    this.historyBrickList.setOpaque(false);
    this.historyBrickList.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
    this.historyStackLayered.setOpaque(true);
    this.historyStackLayered.setBackground(HISTORY_ZONE_BG);
    this.historyStackLayered.setLayout(null);
    this.historyBottomFade = new HistoryBottomFadeOverlay(HISTORY_ZONE_BG);
    this.historyStackLayered.add(this.historyBrickList, JLayeredPane.DEFAULT_LAYER);
    this.historyStackLayered.add(this.historyBottomFade, JLayeredPane.PALETTE_LAYER);
    this.historyStackLayered.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(final ComponentEvent e) {
            BricksWorkPanel.this.layoutHistoryStackArea();
          }
        });
    this.historyZone = new JPanel(new BorderLayout(0, 0));
    this.historyZone.setOpaque(true);
    this.historyZone.setBackground(HISTORY_ZONE_BG);
    this.historyZone.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(HISTORY_ZONE_LINE, 1, true),
        BorderFactory.createEmptyBorder(4, 8, 6, 8)));
    this.historyZone.add(this.historyStackLayered, BorderLayout.CENTER);

    this.dropCanvas = new JPanel(new BorderLayout(8, 8));
    this.dropCanvas.setOpaque(true);
    this.dropCanvas.setBackground(CANVAS_BG);
    this.dropCanvas.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(ZONE_BORDER, 1, true),
        BorderFactory.createEmptyBorder(6, 8, 8, 8)));
    this.dropCanvas.add(this.poolBuildStack, BorderLayout.NORTH);
    this.dropCanvas.add(this.historyZone, BorderLayout.CENTER);

    this.layered.setLayout(null);
    this.layered.setPreferredSize(new Dimension(400, 320));
    this.layered.add(this.dropCanvas, JLayeredPane.DEFAULT_LAYER);
    this.completionFlyGhost.setOpaque(false);
    this.completionFlyGhost.setVisible(false);
    this.layered.add(this.completionFlyGhost, JLayeredPane.DRAG_LAYER);

    this.layered.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(final ComponentEvent e) {
            BricksWorkPanel.this.layoutLayeredBody();
          }
        });

    this.add(this.cueLabel, BorderLayout.NORTH);
    this.add(this.layered, BorderLayout.CENTER);
  }

  static List<DialogLine> filterMultiWordTargetLines(
      final List<DialogLine> lines, final boolean targetIsSideA) {
    if (lines == null || lines.isEmpty()) {
      return List.of();
    }
    final List<DialogLine> out = new ArrayList<>();
    for (final DialogLine line : lines) {
      final String target = targetIsSideA ? line.a() : line.b();
      if (BricksPhraseSupport.parsePhrase(target).wordTokens().size() > 1) {
        out.add(line);
      }
    }
    return List.copyOf(out);
  }

  static List<String> splitWords(final String line) {
    return BricksPhraseSupport.parsePhrase(line).wordTokens();
  }

  private static String escapeHtml(final String raw) {
    if (raw == null) {
      return "";
    }
    return raw.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br/>");
  }

  /**
   * {@link JLabel} HTML ignores the component {@link Font}; embed the JetBrains face and weight so
   * the cue line (text to translate) actually renders bold.
   */
  private static String formatCueLabelHtml(final String escapedCueLine) {
    final Font f = LangTrainerFonts.MONO_NL_BOLD.atPoints(CUE_LABEL_FONT_PT);
    final String family = f.getFamily();
    final String familyCss = family.indexOf(' ') >= 0 ? "'%s'".formatted(family) : family;
    return "<html><div style=\"text-align:center;font-family:%s;font-size:%spt;font-weight:bold\">%s</div></html>"
        .formatted(familyCss, Float.toString(CUE_LABEL_FONT_PT), escapedCueLine);
  }

  /**
   * Primary-button release for ending drag or cancelling pending pick. Accepts {@link
   * MouseEvent#NOBUTTON} because some pipelines (including global AWT) clear {@code getButton()}
   * on release.
   */
  static boolean isPrimaryReleaseForDrop(final MouseEvent me) {
    final int b = me.getButton();
    return b == MouseEvent.BUTTON1 || b == MouseEvent.NOBUTTON;
  }

  private static void setHistoryTopSpacerHeight(final JPanel spacer, final int h) {
    final int hh = Math.max(0, h);
    spacer.setPreferredSize(new Dimension(1, hh));
    spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, hh));
    spacer.setMinimumSize(new Dimension(0, hh));
  }

  private static float easeOutCubic(final float t) {
    final float u = 1f - Math.min(1f, Math.max(0f, t));
    return 1f - u * u * u;
  }

  private static int completionSlotTargetY(
      final Point spacerOrigin, final int slotHeight, final int contentHeight) {
    return spacerOrigin.y + Math.max(0, (slotHeight - contentHeight) / 2);
  }

  void resetToIdle() {
    this.cancelPendingOrActiveDrag();
    this.stopCompletionFlyAnimation();
    this.definition = null;
    this.exercises = List.of();
    this.exerciseIndex = 0;
    this.wordTokens = List.of();
    this.fixedEndSuffix = null;
    this.buildIds.clear();
    this.poolIds.clear();
    this.historyLines.clear();
    this.cueLabel.setText(" ");
    this.rebuildHistoryBrickList();
    this.fieldCanvas.bindLists(List.of(), List.of(), List.of(), null);
    this.fieldCanvas.setBuildBrickFill(BUILD_PARTIAL_BG);
    this.repaint();
  }

  void startSession(
      final DialogDefinition definition,
      final boolean userBuildsSideA,
      final List<DialogLine> exerciseLines) {
    this.resetToIdle();
    this.definition = Objects.requireNonNull(definition, "definition");
    this.userBuildsSideA = userBuildsSideA;
    final ArrayList<DialogLine> shuffledLines = new ArrayList<>(exerciseLines);
    Collections.shuffle(shuffledLines, this.random);
    this.exercises = List.copyOf(shuffledLines);
    this.exerciseIndex = 0;
    this.historyLines.clear();
    this.rebuildHistoryBrickList();
    this.loadCurrentExercise();
    SwingUtilities.invokeLater(this::layoutLayeredBody);
  }

  private void layoutLayeredBody() {
    final int w = Math.max(0, this.layered.getWidth());
    final int h = Math.max(0, this.layered.getHeight());
    this.dropCanvas.setBounds(0, 0, w, h);
    if (w > 0 && h > 0) {
      this.dropCanvas.validate();
      this.syncFieldCanvas();
      this.layoutHistoryStackArea();
    }
  }

  private Font brickFont() {
    return LangTrainerFonts.MONO_NL_REGULAR.atPoints(BRICK_FONT_PT);
  }

  private void layoutHistoryStackArea() {
    final int w = Math.max(0, this.historyStackLayered.getWidth());
    final int h = Math.max(0, this.historyStackLayered.getHeight());
    if (w <= 0 || h <= 0) {
      return;
    }
    this.historyBrickList.revalidate();
    final int prefH = Math.max(h, this.historyBrickList.getPreferredSize().height);
    this.historyBrickList.setBounds(0, 0, w, prefH);
    this.historyBottomFade.setBounds(0, 0, w, h);
    this.historyStackLayered.moveToFront(this.historyBottomFade);
  }

  private void loadCurrentExercise() {
    if (this.exercises.isEmpty() || this.exerciseIndex >= this.exercises.size()) {
      return;
    }
    final DialogLine line = this.exercises.get(this.exerciseIndex);
    this.cueLineText = this.userBuildsSideA ? line.b() : line.a();
    this.targetLineText = this.userBuildsSideA ? line.a() : line.b();
    final BricksPhraseSupport.Parts parts = BricksPhraseSupport.parsePhrase(this.targetLineText);
    this.wordTokens = parts.wordTokens();
    this.fixedEndSuffix = parts.fixedEndSuffix();
    this.cueLabel.setText(formatCueLabelHtml(escapeHtml(this.cueLineText)));
    this.buildIds.clear();
    this.poolIds.clear();
    final List<Integer> ids = new ArrayList<>();
    for (int i = 0; i < this.wordTokens.size(); i++) {
      ids.add(i);
    }
    this.shufflePoolIndicesUntilNotTargetOrder(ids);
    this.poolIds.addAll(ids);
    this.syncFieldCanvas();
  }

  /**
   * Pool left-to-right must not already read as the target phrase (by word text, not brick id).
   */
  private void shufflePoolIndicesUntilNotTargetOrder(final List<Integer> ids) {
    if (ids.size() <= 1) {
      return;
    }
    for (int attempt = 0; attempt < 200; attempt++) {
      Collections.shuffle(ids, this.random);
      if (!BricksPhraseSupport.matchesTargetPhraseOrder(ids, this.wordTokens)) {
        return;
      }
    }
    Collections.rotate(ids, 1);
  }

  private void syncFieldCanvas() {
    this.fieldCanvas.bindLists(this.poolIds, this.buildIds, this.wordTokens, this.fixedEndSuffix);
    this.fieldCanvas.setBuildBrickFill(this.buildTileFillColor());
  }

  private Color buildTileFillColor() {
    final int n = this.wordTokens.size();
    final int placed = this.buildIds.size();
    final boolean filled = placed == n;
    final boolean correct = filled && this.isOrderCorrect();
    if (filled && correct) {
      return BUILD_CORRECT_BG;
    }
    if (filled) {
      return BUILD_WRONG_BG;
    }
    return BUILD_PARTIAL_BG;
  }

  private void rebuildHistoryBrickList() {
    this.historyBrickList.removeAll();
    final Font font = this.brickFont();
    final Iterator<String> it = this.historyLines.descendingIterator();
    boolean first = true;
    while (it.hasNext()) {
      final String phrase = it.next();
      if (!first) {
        this.historyBrickList.add(Box.createVerticalStrut(HISTORY_LINE_VERTICAL_GAP_PX));
      }
      first = false;
      this.historyBrickList.add(this.newHistoryRowFromPhrase(font, phrase));
    }
    this.historyBrickList.add(Box.createVerticalGlue());
    this.historyBrickList.revalidate();
    this.historyBrickList.repaint();
    this.pinHistoryScrollToTop();
  }

  /**
   * After prepending rows, re-layout list bounds (top-aligned, bottom clipped) and fade overlay.
   */
  private void pinHistoryScrollToTop() {
    this.historyBrickList.revalidate();
    this.historyStackLayered.revalidate();
    this.historyStackLayered.validate();
    this.layoutHistoryStackArea();
  }

  private JPanel newHistoryRowFromPhrase(final Font font, final String phrase) {
    final JPanel row =
        new JPanel(new FlowLayout(FlowLayout.LEFT, BRICK_FLOW_H_GAP, 0));
    row.setOpaque(false);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    final BricksPhraseSupport.Parts parts = BricksPhraseSupport.parsePhrase(phrase);
    for (final String word : parts.wordTokens()) {
      row.add(new JLabel(BrickImageRenderer.renderIcon(word, BUILD_CORRECT_BG, font)));
    }
    if (parts.fixedEndSuffix() != null) {
      row.add(
          new JLabel(
              BrickImageRenderer.renderIcon(
                  parts.fixedEndSuffix(), BrickImageRenderer.SUFFIX_BRICK_FILL, font)));
    }
    this.applyHistoryRowHeightCap(row);
    return row;
  }

  private void applyHistoryRowHeightCap(final JPanel row) {
    final int rowH = row.getPreferredSize().height;
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowH));
  }

  private boolean isOrderCorrect() {
    return BricksPhraseSupport.matchesTargetPhraseOrder(this.buildIds, this.wordTokens);
  }

  @Override
  public void onFieldBrickPressed(
      final BricksFieldCanvas.BrickPick pick, final Point pressScreen) {
    if (this.draggingId >= 0 || this.pendingBrickId >= 0) {
      return;
    }
    this.pendingBrickId = pick.id();
    this.pendingPressScreen = new Point(pressScreen);
    this.pendingGrabFracX = pick.offsetX() / (float) Math.max(1, pick.brickW());
    this.pendingGrabFracY = pick.offsetY() / (float) Math.max(1, pick.brickH());
    this.attachGlobalDragMouseListener();
  }

  @Override
  public void onFieldReleasedPrimary(final MouseEvent e) {
    if (this.pendingBrickId >= 0 && BricksWorkPanel.isPrimaryReleaseForDrop(e)) {
      this.cancelPendingDrag();
    }
  }

  private void promotePendingToDrag() {
    final int id = this.pendingBrickId;
    if (id < 0) {
      return;
    }
    this.pendingBrickId = -1;
    this.buildIds.remove(Integer.valueOf(id));
    this.poolIds.remove(Integer.valueOf(id));
    this.syncFieldCanvas();
    this.draggingId = id;
    Point screen = this.pointerLocationOrNull();
    if (screen == null) {
      screen = this.pendingPressScreen;
    }
    this.fieldCanvas.setDraggingFromPromote(
        id, this.pendingGrabFracX, this.pendingGrabFracY, screen);
    this.enterActiveDragInputMode();
    SwingUtilities.invokeLater(
        () -> {
          this.validate();
          this.syncGhostToPointerIfDragging();
        });
  }

  /**
   * After the source brick is removed, toolkit-wide {@code MOUSE_RELEASED} is unreliable. A
   * transparent root glass pane receives move/release until {@link #removeRootGlassForActiveDrag()}.
   * <p>The global {@link AWTEventListener} stays installed: if we remove it when the glass pane
   * appears, the first real drag often stops receiving {@code MOUSE_DRAGGED} until the next press
   * because the grab never transfers cleanly from the removed tile to the glass.
   */
  private void enterActiveDragInputMode() {
    final JRootPane root = SwingUtilities.getRootPane(this);
    if (root == null) {
      this.attachGlobalDragMouseListener();
      this.startDragGhostPollTimer();
      return;
    }
    this.savedGlassPane = root.getGlassPane();
    this.savedGlassWasVisible = this.savedGlassPane.isVisible();
    this.dragRootGlass = new JPanel();
    this.dragRootGlass.setOpaque(false);
    this.dragRootGlass.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    this.dragRootGlass.addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseDragged(final MouseEvent e) {
            if (BricksWorkPanel.this.draggingId >= 0) {
              BricksWorkPanel.this.updateFieldDragFromScreen(e.getLocationOnScreen());
            }
          }
        });
    this.dragRootGlass.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseReleased(final MouseEvent e) {
            if (BricksWorkPanel.this.draggingId < 0) {
              return;
            }
            if (!BricksWorkPanel.isPrimaryReleaseForDrop(e)) {
              return;
            }
            BricksWorkPanel.this.finishDragFromScreen(e.getLocationOnScreen());
          }
        });
    root.setGlassPane(this.dragRootGlass);
    this.dragRootGlass.setVisible(true);
    this.startDragGhostPollTimer();
  }

  private void removeRootGlassForActiveDrag() {
    if (this.dragRootGlass == null) {
      return;
    }
    final JRootPane root = SwingUtilities.getRootPane(this);
    if (root != null && root.getGlassPane() == this.dragRootGlass) {
      root.setGlassPane(this.savedGlassPane);
      this.savedGlassPane.setVisible(this.savedGlassWasVisible);
    }
    this.dragRootGlass = null;
    this.savedGlassPane = null;
  }

  private Point pointerLocationOrNull() {
    final PointerInfo pi = MouseInfo.getPointerInfo();
    return pi != null ? pi.getLocation() : null;
  }

  private void schedulePromoteFromPending() {
    if (this.pendingPromotePosted || this.pendingBrickId < 0) {
      return;
    }
    this.pendingPromotePosted = true;
    final int idSnapshot = this.pendingBrickId;
    SwingUtilities.invokeLater(
        () -> {
          this.pendingPromotePosted = false;
          if (this.pendingBrickId != idSnapshot) {
            return;
          }
          this.promotePendingToDrag();
        });
  }

  private void startDragGhostPollTimer() {
    this.stopDragGhostPollTimer();
    this.dragGhostPollTimer =
        new Timer(
            10,
            event -> {
              if (BricksWorkPanel.this.draggingId < 0) {
                return;
              }
              final Point p = BricksWorkPanel.this.pointerLocationOrNull();
              if (p != null) {
                BricksWorkPanel.this.updateFieldDragFromScreen(p);
              }
            });
    this.dragGhostPollTimer.setRepeats(true);
    this.dragGhostPollTimer.start();
  }

  private void stopDragGhostPollTimer() {
    if (this.dragGhostPollTimer != null) {
      this.dragGhostPollTimer.stop();
      this.dragGhostPollTimer = null;
    }
  }

  private void syncGhostToPointerIfDragging() {
    if (this.draggingId < 0) {
      return;
    }
    final PointerInfo pointer = MouseInfo.getPointerInfo();
    if (pointer == null) {
      return;
    }
    this.updateFieldDragFromScreen(pointer.getLocation());
  }

  private void cancelPendingDrag() {
    this.pendingBrickId = -1;
    this.pendingPromotePosted = false;
    this.detachGlobalDragMouseListener();
  }

  private int screenDistanceSq(final Point a, final Point b) {
    final int dx = a.x - b.x;
    final int dy = a.y - b.y;
    return dx * dx + dy * dy;
  }

  private void attachGlobalDragMouseListener() {
    this.detachGlobalDragMouseListener();
    Toolkit.getDefaultToolkit()
        .addAWTEventListener(this.globalDragMouseListener, DRAG_AWT_MASK);
  }

  private void detachGlobalDragMouseListener() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(this.globalDragMouseListener);
  }

  private void onGlobalMouseWhileDragging(final AWTEvent event) {
    if (!(event instanceof MouseEvent me)) {
      return;
    }
    if (this.pendingBrickId >= 0) {
      if (me.getID() == MouseEvent.MOUSE_DRAGGED) {
        if (this.screenDistanceSq(this.pendingPressScreen, me.getLocationOnScreen())
            >= DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
          this.schedulePromoteFromPending();
        }
      } else if (me.getID() == MouseEvent.MOUSE_RELEASED &&
          BricksWorkPanel.isPrimaryReleaseForDrop(me)) {
        this.cancelPendingDrag();
      }
      return;
    }
    if (this.draggingId < 0) {
      return;
    }
    if (me.getID() == MouseEvent.MOUSE_DRAGGED) {
      this.updateFieldDragFromScreen(me.getLocationOnScreen());
    } else if (me.getID() == MouseEvent.MOUSE_RELEASED &&
        BricksWorkPanel.isPrimaryReleaseForDrop(me)) {
      this.finishDragFromScreen(me.getLocationOnScreen());
    }
  }

  private void updateFieldDragFromScreen(final Point screenPoint) {
    if (!this.layered.isShowing()) {
      return;
    }
    this.fieldCanvas.updateDragPointerFromScreen(screenPoint);
  }

  private void finishDragFromScreen(final Point screenPoint) {
    if (this.draggingId < 0) {
      return;
    }
    final int id = this.draggingId;
    final BricksFieldCanvas.DropResolution drop =
        this.fieldCanvas.resolveDropFromScreen(screenPoint);
    this.draggingId = -1;
    this.teardownGhost();
    if (drop.intoPool()) {
      this.poolIds.add(id);
    } else {
      final int k = Math.min(drop.buildInsertIndex(), this.buildIds.size());
      this.buildIds.add(k, id);
    }
    this.syncFieldCanvas();
    this.scheduleAdvanceIfSolved();
  }

  private void teardownGhost() {
    this.removeRootGlassForActiveDrag();
    this.stopDragGhostPollTimer();
    this.detachGlobalDragMouseListener();
    this.fieldCanvas.clearDragging();
  }

  private void cancelPendingOrActiveDrag() {
    if (this.pendingBrickId >= 0) {
      this.cancelPendingDrag();
    }
    if (this.draggingId >= 0) {
      this.cancelDragSilently();
    }
  }

  private void cancelDragSilently() {
    if (this.draggingId < 0) {
      return;
    }
    final int id = this.draggingId;
    this.draggingId = -1;
    this.teardownGhost();
    if (!this.buildIds.contains(id) && !this.poolIds.contains(id)) {
      this.poolIds.add(id);
    }
    this.syncFieldCanvas();
  }

  private void scheduleAdvanceIfSolved() {
    if (this.poolIds.isEmpty()
        && this.buildIds.size() == this.wordTokens.size()
        && this.isOrderCorrect()) {
      final Timer timer =
          new Timer(
              450,
              event -> {
                ((Timer) event.getSource()).stop();
                BricksWorkPanel.this.startCompletionFlyThenAdvance();
              });
      timer.setRepeats(false);
      timer.start();
    }
  }

  private void stopCompletionFlyAnimation() {
    if (this.completionFlyTimer != null) {
      this.completionFlyTimer.stop();
      this.completionFlyTimer = null;
    }
    this.parkCompletionGhostLabel();
    this.discardCompletionTopSpacerIfPresent();
  }

  private void discardCompletionTopSpacerIfPresent() {
    if (this.completionAnimTopSpacer == null) {
      return;
    }
    this.historyBrickList.remove(this.completionAnimTopSpacer);
    this.completionAnimTopSpacer = null;
    this.ensureHistoryTrailingGlue();
    this.historyBrickList.revalidate();
    this.historyBrickList.repaint();
  }

  private void removeHistoryTrailingGlueIfPresent() {
    final int n = this.historyBrickList.getComponentCount();
    if (n <= 0) {
      return;
    }
    final Component last = this.historyBrickList.getComponent(n - 1);
    if (last instanceof Box.Filler) {
      this.historyBrickList.remove(n - 1);
    }
  }

  private void ensureHistoryTrailingGlue() {
    final int n = this.historyBrickList.getComponentCount();
    if (n == 0 || !(this.historyBrickList.getComponent(n - 1) instanceof Box.Filler)) {
      this.historyBrickList.add(Box.createVerticalGlue());
    }
  }

  private void parkCompletionGhostLabel() {
    this.completionFlyGhost.setVisible(false);
    this.completionFlyGhost.setIcon(null);
    this.completionFlyGhost.setBounds(0, 0, 0, 0);
    this.layered.moveToBack(this.completionFlyGhost);
  }

  private void runCompletionEaseMoveTimer(
      final int x0,
      final int y0,
      final int x1,
      final int y1,
      final int w,
      final int h,
      final Consumer<Point> setTopLeft,
      final Runnable onComplete) {
    final int[] step = {0};
    this.completionFlyTimer =
        new Timer(
            COMPLETION_FLY_TICK_MS,
            ev -> {
              step[0]++;
              final float t = Math.min(1f, (float) step[0] / (float) COMPLETION_FLY_STEPS);
              final float e = easeOutCubic(t);
              setTopLeft.accept(
                  new Point(
                      Math.round(x0 + (x1 - x0) * e),
                      Math.round(y0 + (y1 - y0) * e)));
              this.layered.repaint();
              if (step[0] >= COMPLETION_FLY_STEPS) {
                ((Timer) ev.getSource()).stop();
                this.completionFlyTimer = null;
                onComplete.run();
              }
            });
    this.completionFlyTimer.setRepeats(true);
    this.completionFlyTimer.start();
  }

  private Point completionSpacerOriginInLayered() {
    return SwingUtilities.convertPoint(this.completionAnimTopSpacer, 0, 0, this.layered);
  }

  private void startCompletionFlyThenAdvance() {
    this.stopCompletionFlyAnimation();
    if (!this.layered.isShowing()
        || !this.fieldCanvas.isShowing()
        || !this.historyBrickList.isShowing()) {
      this.advanceAfterSuccess();
      return;
    }
    final List<String> words = new ArrayList<>(this.buildIds.size());
    for (final int id : this.buildIds) {
      words.add(this.wordTokens.get(id));
    }
    final Font font = this.fieldCanvas.layoutFont();
    final ImageIcon rowIcon =
        BrickImageRenderer.renderRowIcon(words, this.fixedEndSuffix, BUILD_CORRECT_BG, font);
    final int iw = Math.max(1, rowIcon.getIconWidth());
    final int ih = Math.max(1, rowIcon.getIconHeight());
    final Point start = this.completionFlyStartTopLeftInLayered(iw, ih);
    final int rowH = Math.max(1, this.fieldCanvas.buildBricksUnionInCanvas().height);
    this.runOpenHistoryTopSlotThenFly(
        rowH, () -> this.startCompletionFlyGhostIntoTopGap(rowIcon, iw, ih, start.x, start.y));
  }

  /**
   * Top-left for the rendered-row ghost: image’s left edge aligns with the leftmost brick (row
   * composite draws from x = 0), vertically centered on the brick cluster.
   */
  private Point completionFlyStartTopLeftInLayered(final int iw, final int ih) {
    final Rectangle u = this.fieldCanvas.buildBricksUnionInCanvas();
    if (u.width <= 0 || u.height <= 0) {
      final Point origin =
          SwingUtilities.convertPoint(this.fieldCanvas, 0, 0, this.layered);
      final int sh = Math.max(1, this.fieldCanvas.getHeight());
      return new Point(origin.x + BRICK_FLOW_H_GAP, origin.y + (sh - ih) / 2);
    }
    final Point unionInLayered =
        SwingUtilities.convertPoint(this.fieldCanvas, u.x, u.y, this.layered);
    return new Point(unionInLayered.x, unionInLayered.y + (u.height - ih) / 2);
  }

  private void runOpenHistoryTopSlotThenFly(final int rowH, final Runnable onOpened) {
    this.pinHistoryScrollToTop();
    this.removeHistoryTrailingGlueIfPresent();
    final JPanel spacer = new JPanel();
    spacer.setOpaque(false);
    spacer.setAlignmentX(Component.LEFT_ALIGNMENT);
    this.completionAnimTopSpacer = spacer;
    this.historyBrickList.add(spacer, 0);
    this.ensureHistoryTrailingGlue();
    setHistoryTopSpacerHeight(spacer, 0);
    this.historyBrickList.revalidate();
    this.historyBrickList.repaint();
    final int slotH = Math.max(1, rowH);
    final int[] step = {0};
    this.completionFlyTimer =
        new Timer(
            COMPLETION_OPEN_SLOT_TICK_MS,
            ev -> {
              step[0]++;
              final float t =
                  Math.min(1f, (float) step[0] / (float) COMPLETION_OPEN_SLOT_STEPS);
              final float e = easeOutCubic(t);
              setHistoryTopSpacerHeight(spacer, Math.round(slotH * e));
              this.historyBrickList.revalidate();
              this.historyBrickList.repaint();
              if (step[0] >= COMPLETION_OPEN_SLOT_STEPS) {
                ((Timer) ev.getSource()).stop();
                this.completionFlyTimer = null;
                setHistoryTopSpacerHeight(spacer, slotH);
                this.historyBrickList.revalidate();
                onOpened.run();
              }
            });
    this.completionFlyTimer.setRepeats(true);
    this.completionFlyTimer.start();
  }

  private void startCompletionFlyGhostIntoTopGap(
      final ImageIcon rowIcon, final int iw, final int ih, final int x0, final int y0) {
    if (this.completionAnimTopSpacer == null || !this.completionAnimTopSpacer.isShowing()) {
      this.discardCompletionTopSpacerIfPresent();
      this.advanceAfterSuccess();
      return;
    }
    this.buildIds.clear();
    this.syncFieldCanvas();
    final Point sp0 = this.completionSpacerOriginInLayered();
    final int slotH = Math.max(1, this.completionAnimTopSpacer.getHeight());
    final int x1 = sp0.x + BRICK_FLOW_H_GAP;
    final int y1 = BricksWorkPanel.completionSlotTargetY(sp0, slotH, ih);
    this.completionFlyGhost.setIcon(rowIcon);
    this.completionFlyGhost.setBounds(x0, y0, iw, ih);
    this.completionFlyGhost.setVisible(true);
    this.layered.moveToFront(this.completionFlyGhost);
    this.layered.setLayer(this.completionFlyGhost, JLayeredPane.DRAG_LAYER);
    this.runCompletionEaseMoveTimer(
        x0,
        y0,
        x1,
        y1,
        iw,
        ih,
        p -> this.completionFlyGhost.setBounds(p.x, p.y, iw, ih),
        () -> {
          this.parkCompletionGhostLabel();
          this.completionAnimTopSpacer = null;
          this.advanceAfterSuccess();
        });
  }

  private void advanceAfterSuccess() {
    this.historyLines.addLast(this.targetLineText);
    while (this.historyLines.size() > MAX_HISTORY_LINES) {
      this.historyLines.removeFirst();
    }
    this.rebuildHistoryBrickList();
    this.exerciseIndex++;
    if (this.exerciseIndex >= this.exercises.size()) {
      this.pinHistoryScrollToTop();
      JOptionPane.showMessageDialog(
          this,
          "You have completed all phrases in this session.",
          "Done",
          JOptionPane.INFORMATION_MESSAGE);
      this.resetToIdle();
      this.onExitToSelect.run();
      return;
    }
    this.loadCurrentExercise();
  }

  /**
   * Bottom-weighted vertical ramp: transparent over recent rows, then fades to solid zone colour
   * so older history lines soften out without a scrollbar.
   */
  private static final class HistoryBottomFadeOverlay extends JPanel {

    private final Color zoneBg;

    private HistoryBottomFadeOverlay(final Color zoneBg) {
      this.zoneBg = Objects.requireNonNull(zoneBg, "zoneBg");
      this.setOpaque(false);
    }

    @Override
    public boolean contains(final int x, final int y) {
      return false;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      super.paintComponent(g);
      final int w = this.getWidth();
      final int h = this.getHeight();
      if (w < 2 || h < 2) {
        return;
      }
      final float y0 = h * 0.42f;
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      final int r = this.zoneBg.getRed();
      final int gr = this.zoneBg.getGreen();
      final int b = this.zoneBg.getBlue();
      final LinearGradientPaint paint =
          new LinearGradientPaint(
              new Point2D.Float(0f, y0),
              new Point2D.Float(0f, h),
              new float[] {0f, 1f},
              new Color[] {
                  new Color(r, gr, b, 0),
                  new Color(r, gr, b, 248),
              });
      g2.setPaint(paint);
      g2.fillRect(0, 0, w, h);
      g2.dispose();
    }
  }


}
