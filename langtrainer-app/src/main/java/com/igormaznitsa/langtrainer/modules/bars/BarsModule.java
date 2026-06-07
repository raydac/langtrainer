package com.igormaznitsa.langtrainer.modules.bars;

import static java.util.Objects.requireNonNull;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;
import com.igormaznitsa.langtrainer.engine.ClasspathLangResourceIndex;
import com.igormaznitsa.langtrainer.engine.ClasspathResourceIndexTree;
import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.DialogLine;
import com.igormaznitsa.langtrainer.engine.DialogListEntry;
import com.igormaznitsa.langtrainer.engine.ExternalResourceSupport;
import com.igormaznitsa.langtrainer.engine.ImageResourceLoader;
import com.igormaznitsa.langtrainer.engine.LangTrainerResourceAccess;
import com.igormaznitsa.langtrainer.engine.ResourceListModelMaterializer;
import com.igormaznitsa.langtrainer.engine.ResourceListSelectPanel;
import com.igormaznitsa.langtrainer.engine.TextDirectionSupport;
import com.igormaznitsa.langtrainer.ui.CompletionBanner;
import com.igormaznitsa.langtrainer.ui.LangTrainerFonts;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public final class BarsModule extends AbstractLangTrainerModule {

  private static final String CARD_SELECT = "select";
  private static final String CARD_WORK = "work";

  private final DefaultListModel<DialogListEntry> listModel = new DefaultListModel<>();
  private final Set<String> expandedClasspathFolders = new HashSet<>();
  private final JPanel rootPanel = new JPanel(new CardLayout());
  private final BarsWorkPanel workPanel = new BarsWorkPanel();
  private final ClasspathResourceIndexTree classpathResourceTree;
  private ClasspathResourceIndexTree externalResourceTree = ClasspathResourceIndexTree.empty();
  private JList<DialogListEntry> selectionList;
  private ResourceListSelectPanel.Result resourceSelectView;
  private File lastOpenDir;
  private SwingWorker<?, ?> externalSyncWorker;

  public BarsModule() {
    this.classpathResourceTree =
        ClasspathLangResourceIndex.loadSharedTree(BarsModule.class, this, "Can't load resources");
    this.externalResourceTree = ExternalResourceSupport.loadLocalTree(this);
    this.rebuildResourceListModel();
    this.rootPanel.add(this.makeSelectPanel(), CARD_SELECT);
    this.rootPanel.add(this.workPanel, CARD_WORK);
    this.showSelectCard();
  }

  @Override
  public boolean isVirtualKeyboardToolbarButtonShown() {
    return false;
  }

  @Override
  public String getName() {
    return "Bars";
  }

  @Override
  public String getDescription() {
    return "Drag paired phrase bars into matching rows";
  }

  @Override
  public Icon getImage() {
    return ImageResourceLoader.loadIcon("/bars/images/module-bars.svg", 128, 128);
  }

  @Override
  public JComponent createControlForm() {
    return this.rootPanel;
  }

  @Override
  public boolean isResourceAllowed(final DialogDefinition resourceDefinition) {
    return LangTrainerResourceAccess.visibleToModule(resourceDefinition, LangTrainerModuleId.BARS);
  }

  @Override
  public void onActivation() {
    this.workPanel.reset();
    this.reloadExternalResourcesFromDisk();
    this.showSelectCard();
  }

  @Override
  public void onClose() {
    this.cancelExternalResourceSync();
    this.workPanel.reset();
  }

  private JPanel makeSelectPanel() {
    final ResourceListSelectPanel.Result view = ResourceListSelectPanel.build(
        this.listModel,
        ResourceListSelectPanel.Appearance.FLY_GAME,
        "Select bars resource",
        "Start",
        "Open from file",
        this::startBars,
        this::openFromFile,
        this::onClasspathFolderRowClicked,
        this::loadExternalResources);
    this.resourceSelectView = view;
    this.selectionList = view.list();
    return view.panel();
  }

  private void rebuildResourceListModel() {
    ResourceListModelMaterializer.materializeMergedTrees(
        this.listModel,
        this.expandedClasspathFolders,
        this.classpathResourceTree,
        this.externalResourceTree);
    ExternalResourceSupport.materializeOpenedFileRows(this, this.listModel);
  }

  private void onClasspathFolderRowClicked(final DialogListEntry.DialogFolderRow folder) {
    final String key = folder.pathKey();
    if (this.expandedClasspathFolders.contains(key)) {
      this.expandedClasspathFolders.remove(key);
    } else {
      this.expandedClasspathFolders.add(key);
    }
    this.rebuildResourceListModel();
    final int rowIndex = DialogListEntry.indexOfFolderPathKey(this.listModel, key);
    if (this.selectionList != null && rowIndex >= 0) {
      this.selectionList.setSelectedIndex(rowIndex);
    }
  }

  private void loadExternalResources() {
    this.cancelExternalResourceSync();
    this.externalSyncWorker = ExternalResourceSupport.syncAndLoadAsync(
        this,
        this.resourceSelectView,
        tree -> this.externalResourceTree = tree,
        this::rebuildResourceListModel);
  }

  private void cancelExternalResourceSync() {
    if (this.externalSyncWorker != null) {
      this.externalSyncWorker.cancel(true);
      this.externalSyncWorker = null;
    }
  }

  private void reloadExternalResourcesFromDisk() {
    this.externalResourceTree = ExternalResourceSupport.loadLocalTree(this);
    this.rebuildResourceListModel();
  }

  private void openFromFile(final JList<DialogListEntry> list) {
    ExternalResourceSupport.openResourceFromFile(
            this.rootPanel, this.lastOpenDir, "JSON phrase list (*.json)", "Can't open file")
        .ifPresent(resource -> {
          ExternalResourceSupport.mergeOpenedResource(
              this.listModel,
              list,
              resource.definition(),
              this::rebuildResourceListModel);
          resource.parentDirectory().ifPresent(parent -> this.lastOpenDir = parent);
        });
  }

  private void startBars(final DialogDefinition definition) {
    final List<DialogLine> lines = BarsWorkPanel.validLines(definition.lines());
    if (lines.size() < BarsWorkPanel.STAGE_SIZE) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "Bars needs at least 7 complete phrase pairs.",
          "Nothing to practice",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    this.workPanel.start(definition, lines);
    this.showWorkCard();
  }

  private void showSelectCard() {
    this.showCard(CARD_SELECT);
    SwingUtilities.invokeLater(() -> {
      if (this.selectionList != null) {
        this.selectionList.requestFocusInWindow();
      }
    });
  }

  private void showWorkCard() {
    this.showCard(CARD_WORK);
  }

  private void showCard(final String card) {
    ((CardLayout) this.rootPanel.getLayout()).show(this.rootPanel, card);
  }

  private static final class BarsWorkPanel extends JPanel {

    private static final String TOP_STATUS = "status";
    private static final String TOP_NEXT = "next";
    private static final int STAGE_SIZE = 7;
    private static final int STAGE_ADVANCE = 6;
    private static final Color STATUS_BG = new Color(245, 248, 252);
    private static final Color COMPLETE_BG = new Color(200, 240, 200);
    private static final Color NEXT_BG = new Color(46, 125, 50);
    private static final Color NEXT_BORDER = new Color(27, 94, 32);

    private final JPanel topPanel = new JPanel(new CardLayout());
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton nextButton = new JButton("NEXT");
    private final Random random = new Random();
    private final BarBoardPanel boardPanel = new BarBoardPanel(this::onStageSolved);
    private List<DialogLine> lines = List.of();
    private int stageStart;
    private int stageEnd;
    private BarsWorkPanel() {
      super(new BorderLayout(8, 8));
      this.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
      this.statusLabel.setOpaque(true);
      this.statusLabel.setBackground(STATUS_BG);
      this.statusLabel.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(18f));
      this.statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
      this.styleNextButton();
      this.nextButton.addActionListener(event -> this.loadNextStage());

      this.topPanel.setOpaque(false);
      this.topPanel.add(this.statusLabel, TOP_STATUS);
      this.topPanel.add(this.nextButton, TOP_NEXT);
      this.add(this.topPanel, BorderLayout.NORTH);
      this.add(this.boardPanel, BorderLayout.CENTER);
    }

    private static List<DialogLine> validLines(final List<DialogLine> rawLines) {
      if (rawLines == null || rawLines.isEmpty()) {
        return List.of();
      }
      return rawLines.stream()
          .filter(line -> line.a() != null && !line.a().isBlank())
          .filter(line -> line.b() != null && !line.b().isBlank())
          .toList();
    }

    private void styleNextButton() {
      this.nextButton.setOpaque(true);
      this.nextButton.setContentAreaFilled(true);
      this.nextButton.setFocusPainted(false);
      this.nextButton.setBackground(NEXT_BG);
      this.nextButton.setForeground(Color.WHITE);
      this.nextButton.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(24f));
      this.nextButton.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(NEXT_BORDER, 3, true),
          BorderFactory.createEmptyBorder(10, 56, 10, 56)));
      this.nextButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void reset() {
      this.lines = List.of();
      this.stageStart = 0;
      this.stageEnd = 0;
      this.statusLabel.setBackground(STATUS_BG);
      this.statusLabel.setText(" ");
      this.showTop(TOP_STATUS);
      this.boardPanel.reset();
    }

    private void start(final DialogDefinition definition, final List<DialogLine> sourceLines) {
      this.reset();
      final ArrayList<DialogLine> shuffled = new ArrayList<>(sourceLines);
      Collections.shuffle(shuffled, this.random);
      this.lines = List.copyOf(shuffled);
      this.boardPanel.setDirections(
          TextDirectionSupport.isRightToLeft(definition, true),
          TextDirectionSupport.isRightToLeft(definition, false));
      this.loadStage(0);
    }

    private void loadNextStage() {
      if (this.stageEnd >= this.lines.size()) {
        this.showCompleted();
        return;
      }
      this.loadStage(Math.min(this.stageStart + STAGE_ADVANCE, this.lines.size() - STAGE_SIZE));
    }

    private void loadStage(final int requestedStart) {
      this.stageStart = Math.max(0, requestedStart);
      this.stageEnd = Math.min(this.lines.size(), this.stageStart + STAGE_SIZE);
      if (this.stageEnd - this.stageStart < STAGE_SIZE) {
        this.stageStart = Math.max(0, this.lines.size() - STAGE_SIZE);
        this.stageEnd = this.lines.size();
      }
      this.statusLabel.setBackground(STATUS_BG);
      this.statusLabel.setText("Match each left bar with its translation on the right");
      this.showTop(TOP_STATUS);
      this.boardPanel.startStage(this.makeStageEntries());
    }

    private List<BarEntry> makeStageEntries() {
      final ArrayList<BarEntry> entries = new ArrayList<>(STAGE_SIZE);
      for (int i = this.stageStart; i < this.stageEnd; i++) {
        entries.add(new BarEntry(i, this.lines.get(i).a().strip(), this.lines.get(i).b().strip()));
      }
      return List.copyOf(entries);
    }

    private void onStageSolved() {
      this.statusLabel.setBackground(COMPLETE_BG);
      if (this.stageEnd >= this.lines.size()) {
        this.showCompleted();
        return;
      }
      this.showTop(TOP_NEXT);
    }

    private void showCompleted() {
      this.statusLabel.setText("COMPLETED");
      this.showTop(TOP_STATUS);
      this.boardPanel.showCompleted();
    }

    private void showTop(final String card) {
      ((CardLayout) this.topPanel.getLayout()).show(this.topPanel, card);
    }


  }

  private static final class BarBoardPanel extends JPanel {

    private static final Color LEFT_LANE_BG = new Color(128, 232, 238);
    private static final Color RIGHT_LANE_BG = new Color(255, 183, 77);
    private static final Color LEFT_BAR_BG = new Color(225, 245, 254);
    private static final Color RIGHT_BAR_BG = new Color(255, 249, 196);
    private static final Color BAR_BORDER = new Color(55, 71, 79);
    private static final Color DRAG_BORDER = new Color(21, 101, 192);
    private static final int OUTER_GAP = 18;
    private static final int LANE_GAP = 12;
    private static final int BAR_GAP = 8;

    private final Runnable onSolved;
    private final Map<Integer, BarEntry> entriesById = new HashMap<>();
    private final List<Integer> leftOrder = new ArrayList<>();
    private final List<Integer> rightOrder = new ArrayList<>();
    private final Random random = new Random();
    private boolean leftRightToLeft;
    private boolean rightRightToLeft;
    private boolean stageSolved;
    private boolean completed;
    private int dragColumn = -1;
    private int dragId = -1;
    private int dragPointerY;
    private int dragGrabOffsetY;

    private BarBoardPanel(final Runnable onSolved) {
      this.onSolved = requireNonNull(onSolved, "onSolved");
      this.setOpaque(true);
      this.setBackground(new Color(248, 250, 252));
      this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      final MouseAdapter mouse = new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent event) {
          BarBoardPanel.this.onMousePressed(event.getPoint());
        }

        @Override
        public void mouseDragged(final MouseEvent event) {
          BarBoardPanel.this.onMouseDragged(event.getY());
        }

        @Override
        public void mouseReleased(final MouseEvent event) {
          BarBoardPanel.this.onMouseReleased(event.getY());
        }
      };
      this.addMouseListener(mouse);
      this.addMouseMotionListener(mouse);
    }

    private void setDirections(final boolean leftRightToLeft, final boolean rightRightToLeft) {
      this.leftRightToLeft = leftRightToLeft;
      this.rightRightToLeft = rightRightToLeft;
    }

    private void reset() {
      this.entriesById.clear();
      this.leftOrder.clear();
      this.rightOrder.clear();
      this.stageSolved = false;
      this.completed = false;
      this.cancelDrag();
      this.repaint();
    }

    private void startStage(final List<BarEntry> entries) {
      this.reset();
      entries.forEach(entry -> {
        this.entriesById.put(entry.id(), entry);
        this.leftOrder.add(entry.id());
      });
      Collections.shuffle(this.leftOrder, this.random);
      this.rightOrder.addAll(this.makeUnmatchedRightOrder());
      this.repaint();
    }

    private List<Integer> makeUnmatchedRightOrder() {
      final ArrayList<Integer> order = new ArrayList<>(this.leftOrder);
      if (order.size() < 2) {
        return order;
      }
      for (int attempt = 0; attempt < 32; attempt++) {
        Collections.shuffle(order, this.random);
        if (!this.hasAlignedBars(order)) {
          return order;
        }
      }
      order.clear();
      order.addAll(this.leftOrder);
      Collections.rotate(order, 1 + this.random.nextInt(order.size() - 1));
      return order;
    }

    private boolean hasAlignedBars(final List<Integer> candidateRightOrder) {
      for (int i = 0; i < this.leftOrder.size(); i++) {
        if (this.leftOrder.get(i).equals(candidateRightOrder.get(i))) {
          return true;
        }
      }
      return false;
    }

    private void showCompleted() {
      this.completed = true;
      this.stageSolved = true;
      this.cancelDrag();
      this.repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
      super.paintComponent(graphics);
      final Graphics2D g2 = (Graphics2D) graphics.create();
      try {
        this.applyQuality(g2);
        final Layout layout = this.resolveLayout();
        this.paintLane(g2, layout.leftLane(), LEFT_LANE_BG, this.leftOrder, 0);
        this.paintLane(g2, layout.rightLane(), RIGHT_LANE_BG, this.rightOrder, 1);
        if (this.completed) {
          this.paintCompletedBanner(g2);
        }
      } finally {
        g2.dispose();
      }
    }

    private void applyQuality(final Graphics2D g2) {
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private Layout resolveLayout() {
      final int w = Math.max(1, this.getWidth());
      final int h = Math.max(1, this.getHeight());
      final int laneW = Math.max(1, (w - OUTER_GAP * 2 - LANE_GAP) / 2);
      final int laneH = Math.max(1, h - OUTER_GAP * 2);
      final Rectangle left = new Rectangle(OUTER_GAP, OUTER_GAP, laneW, laneH);
      return new Layout(left, new Rectangle(left.x + laneW + LANE_GAP, OUTER_GAP, laneW, laneH));
    }

    private void paintLane(
        final Graphics2D g2,
        final Rectangle lane,
        final Color background,
        final List<Integer> order,
        final int column) {
      g2.setColor(background);
      g2.fillRoundRect(lane.x, lane.y, lane.width, lane.height, 16, 16);
      g2.setColor(new Color(70, 90, 105));
      g2.setStroke(new BasicStroke(2f));
      g2.drawRoundRect(lane.x, lane.y, lane.width, lane.height, 16, 16);
      final BarMetrics metrics = this.resolveBarMetrics(lane, order.size());
      g2.setFont(metrics.font());
      for (int i = 0; i < order.size(); i++) {
        final int id = order.get(i);
        if (this.dragId == id && this.dragColumn == column) {
          continue;
        }
        this.paintBar(g2, this.barRect(lane, metrics, i), id, column, false);
      }
      if (this.dragColumn == column && this.dragId >= 0) {
        this.paintBar(g2, this.dragBarRect(lane, metrics), this.dragId, column, true);
      }
    }

    private BarMetrics resolveBarMetrics(final Rectangle lane, final int count) {
      final int barsAreaH = Math.max(1, lane.height - BAR_GAP * (count + 1));
      final int barH = Math.max(20, barsAreaH / Math.max(1, count));
      final float fontPt = Math.max(12f, Math.min(28f, barH * 0.40f));
      return new BarMetrics(
          barH, LangTrainerFonts.SYSTEM_MONOSPACED.atPoints(fontPt).deriveFont(Font.BOLD, fontPt));
    }

    private Rectangle barRect(final Rectangle lane, final BarMetrics metrics, final int index) {
      return new Rectangle(
          lane.x + BAR_GAP,
          lane.y + BAR_GAP + index * (metrics.barHeight() + BAR_GAP),
          Math.max(1, lane.width - BAR_GAP * 2),
          metrics.barHeight());
    }

    private Rectangle dragBarRect(final Rectangle lane, final BarMetrics metrics) {
      final int y = Math.max(
          lane.y + BAR_GAP,
          Math.min(lane.y + lane.height - BAR_GAP - metrics.barHeight(),
              this.dragPointerY - this.dragGrabOffsetY));
      return new Rectangle(lane.x + BAR_GAP, y, lane.width - BAR_GAP * 2, metrics.barHeight());
    }

    private void paintBar(
        final Graphics2D g2,
        final Rectangle rect,
        final int id,
        final int column,
        final boolean dragging) {
      g2.setColor(this.barBackground(column));
      g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
      g2.setColor(dragging ? DRAG_BORDER : BAR_BORDER);
      g2.setStroke(new BasicStroke(dragging ? 3f : 2f));
      g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
      this.paintBarText(g2, rect, this.textFor(id, column), column == 0);
    }

    private Color barBackground(final int column) {
      if (this.stageSolved) {
        return BarsWorkPanel.COMPLETE_BG;
      }
      return column == 0 ? LEFT_BAR_BG : RIGHT_BAR_BG;
    }

    private void paintBarText(
        final Graphics2D g2, final Rectangle rect, final String rawText, final boolean leftColumn) {
      final boolean rtl = leftColumn ? this.leftRightToLeft : this.rightRightToLeft;
      final FontMetrics fm = g2.getFontMetrics();
      final String text = this.fitText(
          TextDirectionSupport.bidiEmbedding(rawText, rtl), fm, Math.max(1, rect.width - 18));
      final int textW = fm.stringWidth(text);
      final int x = rect.x + Math.max(0, (rect.width - textW) / 2);
      final int y = rect.y + Math.max(0, (rect.height - fm.getHeight()) / 2) + fm.getAscent();
      g2.setColor(Color.BLACK);
      g2.drawString(text, x, y);
    }

    private String fitText(final String text, final FontMetrics fm, final int maxWidth) {
      if (fm.stringWidth(text) <= maxWidth) {
        return text;
      }
      String fitted = text;
      while (!fitted.isEmpty() && fm.stringWidth(fitted + "...") > maxWidth) {
        fitted = fitted.substring(0, fitted.length() - 1);
      }
      return fitted.isEmpty() ? "..." : fitted + "...";
    }

    private String textFor(final int id, final int column) {
      final BarEntry entry = this.entriesById.get(id);
      return column == 0 ? entry.left() : entry.right();
    }

    private void paintCompletedBanner(final Graphics2D g2) {
      CompletionBanner.paintCentered(g2, this, OUTER_GAP);
    }

    private void onMousePressed(final Point point) {
      if (this.stageSolved || this.completed) {
        return;
      }
      final Layout layout = this.resolveLayout();
      this.tryStartDrag(point, layout.leftLane(), this.leftOrder, 0);
      if (this.dragId < 0) {
        this.tryStartDrag(point, layout.rightLane(), this.rightOrder, 1);
      }
    }

    private void tryStartDrag(
        final Point point, final Rectangle lane, final List<Integer> order, final int column) {
      if (!lane.contains(point)) {
        return;
      }
      final BarMetrics metrics = this.resolveBarMetrics(lane, order.size());
      for (int i = 0; i < order.size(); i++) {
        final Rectangle rect = this.barRect(lane, metrics, i);
        if (rect.contains(point)) {
          this.dragColumn = column;
          this.dragId = order.get(i);
          this.dragPointerY = point.y;
          this.dragGrabOffsetY = point.y - rect.y;
          this.repaint();
          return;
        }
      }
    }

    private void onMouseDragged(final int y) {
      if (this.dragId < 0) {
        return;
      }
      this.dragPointerY = y;
      this.swapDraggedBarWithPointerTarget(y);
      this.repaint();
    }

    private void onMouseReleased(final int y) {
      if (this.dragId < 0) {
        return;
      }
      this.dragPointerY = y;
      this.swapDraggedBarWithPointerTarget(y);
      this.cancelDrag();
      this.stageSolved = this.isSolved();
      if (this.stageSolved) {
        this.onSolved.run();
      }
      this.repaint();
    }

    private void swapDraggedBarWithPointerTarget(final int y) {
      final List<Integer> order = this.dragColumn == 0 ? this.leftOrder : this.rightOrder;
      final int currentIndex = order.indexOf(this.dragId);
      if (currentIndex < 0) {
        return;
      }
      final int targetIndex = this.pointerTargetIndex(this.dragColumn, order, y);
      if (targetIndex != currentIndex) {
        Collections.swap(order, currentIndex, targetIndex);
      }
    }

    private int pointerTargetIndex(final int column, final List<Integer> order, final int y) {
      final Layout layout = this.resolveLayout();
      final Rectangle lane = column == 0 ? layout.leftLane() : layout.rightLane();
      final BarMetrics metrics = this.resolveBarMetrics(lane, order.size());
      return Math.max(0,
          Math.min(order.size() - 1, (y - lane.y) / (metrics.barHeight() + BAR_GAP)));
    }

    private void cancelDrag() {
      this.dragColumn = -1;
      this.dragId = -1;
      this.dragPointerY = 0;
      this.dragGrabOffsetY = 0;
    }

    private boolean isSolved() {
      if (this.leftOrder.size() != this.rightOrder.size() || this.leftOrder.isEmpty()) {
        return false;
      }
      for (int i = 0; i < this.leftOrder.size(); i++) {
        if (!this.leftOrder.get(i).equals(this.rightOrder.get(i))) {
          return false;
        }
      }
      return true;
    }
  }

  private record BarEntry(int id, String left, String right) {
  }

  private record Layout(Rectangle leftLane, Rectangle rightLane) {
  }

  private record BarMetrics(int barHeight, Font font) {
  }
}
