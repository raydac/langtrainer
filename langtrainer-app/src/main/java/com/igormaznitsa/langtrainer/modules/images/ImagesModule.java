package com.igormaznitsa.langtrainer.modules.images;

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
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

public final class ImagesModule extends AbstractLangTrainerModule {

  private static final String CARD_SELECT = "select";
  private static final String CARD_WORK = "work";

  private final DefaultListModel<DialogListEntry> listModel = new DefaultListModel<>();
  private final Set<String> expandedClasspathFolders = new HashSet<>();
  private final JPanel rootPanel = new JPanel(new CardLayout());
  private final ImagesWorkPanel workPanel = new ImagesWorkPanel();
  private final ClasspathResourceIndexTree classpathResourceTree;
  private ClasspathResourceIndexTree externalResourceTree = ClasspathResourceIndexTree.empty();
  private JList<DialogListEntry> selectionList;
  private ResourceListSelectPanel.Result resourceSelectView;
  private File lastOpenDir;
  private SwingWorker<?, ?> externalSyncWorker;
  private SwingWorker<PreparedImagesSession, Void> preparedImagesWorker;

  public ImagesModule() {
    this.classpathResourceTree =
        ClasspathLangResourceIndex.loadSharedTree(ImagesModule.class, this, "Can't load resources");
    this.externalResourceTree = ExternalResourceSupport.loadLocalTree(this);
    this.rebuildResourceListModel();
    this.rootPanel.add(this.makeSelectPanel(), CARD_SELECT);
    this.rootPanel.add(this.workPanel, CARD_WORK);
    this.showSelectCard();
  }

  private static String lineText(final DialogLine line, final boolean targetIsSideA) {
    return strippedOrEmpty(targetIsSideA ? line.a() : line.b());
  }

  private static String strippedOrEmpty(final String text) {
    return text == null ? "" : text.strip();
  }

  @Override
  public boolean isVirtualKeyboardToolbarButtonShown() {
    return false;
  }

  @Override
  public String getName() {
    return "Images";
  }

  @Override
  public String getDescription() {
    return "Drag text bars onto matching images";
  }

  @Override
  public Icon getImage() {
    return ImageResourceLoader.loadIcon("/module_images/module-images.svg", 128, 128);
  }

  @Override
  public JComponent createControlForm() {
    return this.rootPanel;
  }

  @Override
  public boolean isResourceAllowed(final DialogDefinition resourceDefinition) {
    return LangTrainerResourceAccess.visibleToModule(resourceDefinition,
        LangTrainerModuleId.IMAGES);
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
    this.cancelPreparedImagesWorker();
    this.workPanel.reset();
  }

  @Override
  public void populateMainToolbar(final JPanel eastToolbar) {
    final JButton helpButton = new JButton("?");
    helpButton.setToolTipText("Show correct image labels");
    helpButton.setFocusPainted(false);
    helpButton.setFocusable(false);
    helpButton.setRequestFocusEnabled(false);
    helpButton.setFont(helpButton.getFont().deriveFont(Font.BOLD, 24.0f));
    helpButton.addActionListener(event -> this.workPanel.flashAnswers());
    eastToolbar.add(helpButton);
  }

  private JPanel makeSelectPanel() {
    final ResourceListSelectPanel.Result view = ResourceListSelectPanel.build(
        this.listModel,
        ResourceListSelectPanel.Appearance.FLY_GAME,
        "Select image resource",
        "Start",
        "Open from file",
        this::chooseTargetLanguageAndStart,
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

  private void chooseTargetLanguageAndStart(final DialogDefinition definition) {
    final JComboBox<String> combo =
        new JComboBox<>(new String[] {definition.langA(), definition.langB()});
    final int opt = JOptionPane.showConfirmDialog(
        this.rootPanel,
        combo,
        "Show image labels in which language?",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (opt == JOptionPane.OK_OPTION) {
      this.prepareImagesAndStart(definition, definition.langA().equals(combo.getSelectedItem()));
    }
  }

  private void prepareImagesAndStart(final DialogDefinition definition,
                                     final boolean targetIsSideA) {
    this.cancelPreparedImagesWorker();
    this.resourceSelectView.setBusy(true);
    this.preparedImagesWorker = new SwingWorker<>() {
      @Override
      protected PreparedImagesSession doInBackground() {
        return new PreparedImagesSession(
            definition,
            targetIsSideA,
            ImagesModule.this.loadImageEntries(definition, targetIsSideA));
      }

      @Override
      protected void done() {
        ImagesModule.this.finishPreparedImagesSession(this);
      }
    };
    this.preparedImagesWorker.execute();
  }

  private void finishPreparedImagesSession(
      final SwingWorker<PreparedImagesSession, Void> worker) {
    if (worker != this.preparedImagesWorker) {
      return;
    }
    this.preparedImagesWorker = null;
    this.resourceSelectView.setBusy(false);
    if (worker.isCancelled()) {
      return;
    }
    try {
      this.startImages(worker.get());
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (final ExecutionException ex) {
      this.showImageLoadFailure(ex.getCause() == null ? ex : ex.getCause());
    }
  }

  private void cancelPreparedImagesWorker() {
    if (this.preparedImagesWorker != null) {
      this.preparedImagesWorker.cancel(true);
      this.preparedImagesWorker = null;
    }
    if (this.resourceSelectView != null) {
      this.resourceSelectView.setBusy(false);
    }
  }

  private void startImages(final PreparedImagesSession session) {
    if (session.entries().isEmpty()) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "This resource has no lines with both selected target text and an embedded image.",
          "Nothing to practice",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    this.workPanel.start(session.definition(), session.targetIsSideA(), session.entries());
    this.showWorkCard();
  }

  private void showImageLoadFailure(final Throwable failure) {
    JOptionPane.showMessageDialog(
        this.rootPanel,
        failure.getMessage() == null ? String.valueOf(failure) : failure.getMessage(),
        "Can't load resource images",
        JOptionPane.ERROR_MESSAGE);
  }

  private List<ImageEntry> loadImageEntries(
      final DialogDefinition definition, final boolean targetIsSideA) {
    final List<DialogLine> lines = definition.lines();
    final List<Optional<BufferedImage>> images = ImageResourceLoader.loadLineImages(definition);
    final ArrayList<ImageEntry> result = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      final String text = lineText(lines.get(i), targetIsSideA);
      if (images.get(i).isPresent() && !text.isBlank()) {
        result.add(new ImageEntry(result.size(), text, images.get(i).orElseThrow()));
      }
    }
    return List.copyOf(result);
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

  private enum DragSource {
    NONE,
    HEAP,
    CARD
  }

  private static final class ImagesWorkPanel extends JPanel {

    private static final String TOP_STATUS = "status";
    private static final String TOP_NEXT = "next";
    private static final int STAGE_SIZE = 6;
    private static final Color STATUS_BG = new Color(245, 248, 252);
    private static final Color COMPLETE_BG = new Color(200, 240, 200);
    private static final Color NEXT_BG = new Color(46, 125, 50);
    private static final Color NEXT_BORDER = new Color(27, 94, 32);

    private final JPanel topPanel = new JPanel(new CardLayout());
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton nextButton = new JButton("NEXT");
    private final Random random = new Random();
    private final ImageBoardPanel boardPanel = new ImageBoardPanel(this::onStageSolved);
    private List<ImageEntry> entries = List.of();
    private int stageStart;
    private int stageEnd;
    private ImagesWorkPanel() {
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
      this.entries = List.of();
      this.stageStart = 0;
      this.stageEnd = 0;
      this.statusLabel.setBackground(STATUS_BG);
      this.statusLabel.setText(" ");
      this.showTop(TOP_STATUS);
      this.boardPanel.reset();
    }

    private void start(
        final DialogDefinition definition,
        final boolean targetIsSideA,
        final List<ImageEntry> sourceEntries) {
      this.reset();
      final ArrayList<ImageEntry> shuffled = new ArrayList<>(sourceEntries);
      Collections.shuffle(shuffled, this.random);
      this.entries = List.copyOf(shuffled);
      this.boardPanel.setRightToLeft(TextDirectionSupport.isRightToLeft(definition, targetIsSideA));
      this.loadStage(0);
    }

    private void flashAnswers() {
      this.boardPanel.flashAnswers();
    }

    private void loadNextStage() {
      if (this.stageEnd >= this.entries.size()) {
        this.showCompleted();
        return;
      }
      this.loadStage(this.nextStageStart());
    }

    private int nextStageStart() {
      final int currentSize = Math.max(1, this.stageEnd - this.stageStart);
      final int repeatCount = 1 + this.random.nextInt(Math.min(2, currentSize));
      final int maxStart =
          Math.max(0, this.entries.size() - Math.min(STAGE_SIZE, this.entries.size()));
      return Math.min(this.stageStart + Math.max(1, currentSize - repeatCount), maxStart);
    }

    private void loadStage(final int requestedStart) {
      this.stageStart = Math.max(0, requestedStart);
      this.stageEnd = Math.min(this.entries.size(), this.stageStart + STAGE_SIZE);
      this.statusLabel.setBackground(STATUS_BG);
      this.statusLabel.setText("Match each text bar with its image");
      this.showTop(TOP_STATUS);
      this.boardPanel.startStage(this.entries.subList(this.stageStart, this.stageEnd));
    }

    private void onStageSolved() {
      this.statusLabel.setBackground(COMPLETE_BG);
      if (this.stageEnd >= this.entries.size()) {
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

  private static final class ImageBoardPanel extends JPanel {

    private static final Color BOARD_BG = new Color(248, 250, 252);
    private static final Color HEAP_BG = new Color(234, 244, 255);
    private static final Color CARD_BG = new Color(255, 255, 255);
    private static final Color CARD_BORDER = new Color(70, 90, 105);
    private static final Color BAR_BG = new Color(128, 232, 238);
    private static final Color BAR_BORDER = new Color(55, 71, 79);
    private static final Color DRAG_BORDER = new Color(21, 101, 192);
    private static final Color ANSWER_BG = new Color(25, 45, 85, 220);
    private static final Color ANSWER_FG = Color.WHITE;
    private static final Color COMPLETED_OVERLAY_BG = new Color(0, 75, 38, 210);
    private static final Color COMPLETED_OVERLAY_FG = new Color(255, 145, 30);
    private static final int OUTER_GAP = 14;
    private static final int HEAP_HEIGHT = 112;
    private static final int GAP = 10;
    private static final int CARD_COLUMNS = 3;
    private static final int BAR_HEIGHT = 38;
    private static final int ANSWER_FLASH_MS = 3500;

    private final Runnable onSolved;
    private final Map<Integer, ImageEntry> entriesById = new HashMap<>();
    private final List<ImageEntry> stageEntries = new ArrayList<>();
    private final List<Integer> heapOrder = new ArrayList<>();
    private final Map<Integer, Integer> cardBars = new HashMap<>();
    private final Map<Integer, PrerenderedCardImage> cardImageCache = new HashMap<>();
    private int heapSlotCount;
    private boolean rightToLeft;
    private boolean stageSolved;
    private boolean completed;
    private boolean answersVisible;
    private Timer answersTimer;
    private int dragId = -1;
    private DragSource dragSource = DragSource.NONE;
    private int dragSourceCard = -1;
    private Point dragPointer = new Point();
    private int dragGrabOffsetX;
    private int dragGrabOffsetY;
    private int dragBarWidth;

    private ImageBoardPanel(final Runnable onSolved) {
      this.onSolved = requireNonNull(onSolved, "onSolved");
      this.setOpaque(true);
      this.setBackground(BOARD_BG);
      this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      final MouseAdapter mouse = new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent event) {
          ImageBoardPanel.this.onMousePressed(event.getPoint());
        }

        @Override
        public void mouseDragged(final MouseEvent event) {
          ImageBoardPanel.this.onMouseDragged(event.getPoint());
        }

        @Override
        public void mouseReleased(final MouseEvent event) {
          ImageBoardPanel.this.onMouseReleased(event.getPoint());
        }
      };
      this.addMouseListener(mouse);
      this.addMouseMotionListener(mouse);
    }

    private void setRightToLeft(final boolean rightToLeft) {
      this.rightToLeft = rightToLeft;
    }

    private void reset() {
      this.stopAnswerFlash();
      this.entriesById.clear();
      this.stageEntries.clear();
      this.heapOrder.clear();
      this.cardBars.clear();
      this.cardImageCache.clear();
      this.heapSlotCount = 0;
      this.stageSolved = false;
      this.completed = false;
      this.cancelDrag();
      this.repaint();
    }

    private void startStage(final List<ImageEntry> entries) {
      this.reset();
      this.stageEntries.addAll(entries);
      entries.forEach(entry -> {
        this.entriesById.put(entry.id(), entry);
        this.heapOrder.add(entry.id());
      });
      this.heapSlotCount = this.heapOrder.size();
      Collections.shuffle(this.heapOrder);
      this.repaint();
    }

    private void showCompleted() {
      this.completed = true;
      this.stageSolved = true;
      this.stopAnswerFlash();
      this.cancelDrag();
      this.repaint();
    }

    private void flashAnswers() {
      if (this.stageEntries.isEmpty() || this.stageSolved || this.completed) {
        return;
      }
      this.stopAnswerFlash();
      this.answersVisible = true;
      this.answersTimer = new Timer(ANSWER_FLASH_MS, event -> this.stopAnswerFlash());
      this.answersTimer.setRepeats(false);
      this.answersTimer.start();
      this.repaint();
    }

    private void stopAnswerFlash() {
      if (this.answersTimer != null) {
        this.answersTimer.stop();
        this.answersTimer = null;
      }
      this.answersVisible = false;
      this.repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
      super.paintComponent(graphics);
      final Graphics2D g2 = (Graphics2D) graphics.create();
      try {
        this.applyQuality(g2);
        final BoardLayout layout = this.resolveLayout();
        this.paintHeap(g2, layout.heap());
        this.paintCards(g2, layout.cards());
        this.paintDraggedBar(g2);
        if (this.completed) {
          this.paintCompletedBanner(g2);
        }
      } finally {
        g2.dispose();
      }
    }

    private void applyQuality(final Graphics2D g2) {
      ImageResourceLoader.applyHighQualityDrawingHints(g2);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private BoardLayout resolveLayout() {
      final int width = Math.max(1, this.getWidth());
      final int height = Math.max(1, this.getHeight());
      final Rectangle heap = new Rectangle(
          OUTER_GAP,
          OUTER_GAP,
          Math.max(1, width - OUTER_GAP * 2),
          HEAP_HEIGHT);
      final Rectangle cardsArea = new Rectangle(
          OUTER_GAP,
          heap.y + heap.height + GAP,
          Math.max(1, width - OUTER_GAP * 2),
          Math.max(1, height - heap.y - heap.height - GAP - OUTER_GAP));
      return new BoardLayout(heap, this.resolveCardRects(cardsArea));
    }

    private List<Rectangle> resolveCardRects(final Rectangle area) {
      final int rows = Math.max(1, (this.stageEntries.size() + CARD_COLUMNS - 1) / CARD_COLUMNS);
      final int cardW = Math.max(1, (area.width - GAP * (CARD_COLUMNS - 1)) / CARD_COLUMNS);
      final int cardH = Math.max(1, (area.height - GAP * (rows - 1)) / rows);
      final ArrayList<Rectangle> result = new ArrayList<>(this.stageEntries.size());
      for (int i = 0; i < this.stageEntries.size(); i++) {
        final int row = i / CARD_COLUMNS;
        final int col = i % CARD_COLUMNS;
        result.add(new Rectangle(
            area.x + col * (cardW + GAP),
            area.y + row * (cardH + GAP),
            cardW,
            cardH));
      }
      return List.copyOf(result);
    }

    private void paintHeap(final Graphics2D g2, final Rectangle heap) {
      g2.setColor(HEAP_BG);
      g2.fillRoundRect(heap.x, heap.y, heap.width, heap.height, 16, 16);
      g2.setColor(CARD_BORDER);
      g2.setStroke(new BasicStroke(2f));
      g2.drawRoundRect(heap.x, heap.y, heap.width, heap.height, 16, 16);
      this.paintHeapHint(g2, heap);
      for (final BarPlacement placement : this.heapBarPlacements(heap)) {
        if (this.isDraggingFromHeap(placement.id())) {
          continue;
        }
        this.paintBar(g2, placement.rect(), placement.id(), false);
      }
    }

    private void paintHeapHint(final Graphics2D g2, final Rectangle heap) {
      if (!this.heapOrder.isEmpty()) {
        return;
      }
      g2.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(18f));
      final FontMetrics fm = g2.getFontMetrics();
      final String text = "Drop text bars here";
      g2.setColor(new Color(70, 90, 105));
      g2.drawString(text, heap.x + (heap.width - fm.stringWidth(text)) / 2,
          heap.y + (heap.height + fm.getAscent()) / 2);
    }

    private List<BarPlacement> heapBarPlacements(final Rectangle heap) {
      if (this.heapOrder.isEmpty()) {
        return List.of();
      }
      final int slotCount = Math.max(this.heapSlotCount, this.heapOrder.size());
      final int columns = Math.min(CARD_COLUMNS, slotCount);
      final int rows = (slotCount + columns - 1) / columns;
      final int barW = Math.max(1, (heap.width - GAP * (columns + 1)) / columns);
      final int barH = Math.max(20, (heap.height - GAP * (rows + 1)) / rows);
      final ArrayList<BarPlacement> result = new ArrayList<>(this.heapOrder.size());
      for (int i = 0; i < this.heapOrder.size(); i++) {
        final int row = i / columns;
        final int col = i % columns;
        result.add(new BarPlacement(
            this.heapOrder.get(i),
            new Rectangle(
                heap.x + GAP + col * (barW + GAP),
                heap.y + GAP + row * (barH + GAP),
                barW,
                barH)));
      }
      return List.copyOf(result);
    }

    private void paintCards(final Graphics2D g2, final List<Rectangle> cards) {
      for (int i = 0; i < cards.size(); i++) {
        this.paintCard(g2, i, cards.get(i));
      }
    }

    private void paintCard(final Graphics2D g2, final int cardIndex, final Rectangle card) {
      g2.setColor(this.stageSolved ? ImagesWorkPanel.COMPLETE_BG : CARD_BG);
      g2.fillRoundRect(card.x, card.y, card.width, card.height, 16, 16);
      g2.setColor(CARD_BORDER);
      g2.setStroke(new BasicStroke(2f));
      g2.drawRoundRect(card.x, card.y, card.width, card.height, 16, 16);
      final ImageEntry entry = this.stageEntries.get(cardIndex);
      this.paintCardImage(g2, entry, this.imageRect(card));
      Optional.ofNullable(this.cardBars.get(cardIndex))
          .filter(id -> !this.isDraggingFromCard(cardIndex, id))
          .ifPresent(id -> this.paintBar(g2, this.cardBarRect(card), id, false));
      if (this.answersVisible) {
        this.paintAnswer(g2, card, entry.id());
      }
    }

    private Rectangle imageRect(final Rectangle card) {
      return new Rectangle(
          card.x + GAP,
          card.y + BAR_HEIGHT + GAP * 2,
          Math.max(1, card.width - GAP * 2),
          Math.max(1, card.height - BAR_HEIGHT - GAP * 3));
    }

    private Rectangle cardBarRect(final Rectangle card) {
      return new Rectangle(card.x + GAP, card.y + GAP, card.width - GAP * 2, BAR_HEIGHT);
    }

    private void paintCardImage(
        final Graphics2D g2, final ImageEntry entry, final Rectangle bounds) {
      final PrerenderedCardImage image = this.prerenderedCardImage(entry, bounds);
      final BufferedImage raster = image.image();
      g2.drawImage(
          raster,
          bounds.x + (bounds.width - raster.getWidth()) / 2,
          bounds.y + (bounds.height - raster.getHeight()) / 2,
          null);
    }

    private PrerenderedCardImage prerenderedCardImage(
        final ImageEntry entry, final Rectangle bounds) {
      return Optional.ofNullable(this.cardImageCache.get(entry.id()))
          .filter(image -> image.fits(bounds))
          .orElseGet(() -> this.rerenderCardImage(entry, bounds));
    }

    private PrerenderedCardImage rerenderCardImage(final ImageEntry entry, final Rectangle bounds) {
      final PrerenderedCardImage image = this.renderCardImage(entry.image(), bounds);
      this.cardImageCache.put(entry.id(), image);
      return image;
    }

    private PrerenderedCardImage renderCardImage(
        final BufferedImage source, final Rectangle bounds) {
      final double scale = Math.min(
          (double) bounds.width / Math.max(1, source.getWidth()),
          (double) bounds.height / Math.max(1, source.getHeight()));
      final int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
      final int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
      final BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g2 = target.createGraphics();
      try {
        ImageResourceLoader.applyHighQualityDrawingHints(g2);
        g2.drawImage(source, 0, 0, width, height, null);
      } finally {
        g2.dispose();
      }
      return new PrerenderedCardImage(bounds.width, bounds.height, target);
    }

    private void paintAnswer(final Graphics2D g2, final Rectangle card, final int id) {
      final Rectangle rect = new Rectangle(
          card.x + GAP,
          card.y + card.height - BAR_HEIGHT - GAP,
          card.width - GAP * 2,
          BAR_HEIGHT);
      g2.setColor(ANSWER_BG);
      g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
      g2.setColor(ANSWER_FG);
      this.paintBarText(g2, rect, this.entriesById.get(id).text(), ANSWER_FG);
    }

    private void paintDraggedBar(final Graphics2D g2) {
      if (this.dragId < 0) {
        return;
      }
      this.paintBar(
          g2,
          new Rectangle(
              this.dragPointer.x - this.dragGrabOffsetX,
              this.dragPointer.y - this.dragGrabOffsetY,
              Math.max(90, this.dragBarWidth),
              BAR_HEIGHT),
          this.dragId,
          true);
    }

    private void paintBar(
        final Graphics2D g2, final Rectangle rect, final int id, final boolean dragging) {
      g2.setColor(BAR_BG);
      g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
      g2.setColor(dragging ? DRAG_BORDER : BAR_BORDER);
      g2.setStroke(new BasicStroke(dragging ? 3f : 2f));
      g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 12, 12);
      this.paintBarText(g2, rect, this.entriesById.get(id).text(), Color.BLACK);
    }

    private void paintBarText(
        final Graphics2D g2, final Rectangle rect, final String rawText, final Color color) {
      final float fontPt = Math.max(12f, Math.min(22f, rect.height * 0.42f));
      g2.setFont(LangTrainerFonts.SYSTEM_MONOSPACED.atPoints(fontPt).deriveFont(Font.BOLD, fontPt));
      final FontMetrics fm = g2.getFontMetrics();
      final String text = this.fitText(
          TextDirectionSupport.bidiEmbedding(rawText, this.rightToLeft),
          fm,
          Math.max(1, rect.width - 18));
      g2.setColor(color);
      g2.drawString(
          text,
          rect.x + Math.max(0, (rect.width - fm.stringWidth(text)) / 2),
          rect.y + Math.max(0, (rect.height - fm.getHeight()) / 2) + fm.getAscent());
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

    private void paintCompletedBanner(final Graphics2D g2) {
      final int width = this.getWidth();
      final int height = this.getHeight();
      final String text = "COMPLETED";
      g2.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(Math.max(36f, width / 15f)));
      final FontMetrics fm = g2.getFontMetrics();
      final int bannerWidth = Math.min(width - OUTER_GAP * 2, Math.max(width * 3 / 4,
          fm.stringWidth(text) + 80));
      final int bannerHeight = fm.getHeight() + 32;
      final Rectangle banner = new Rectangle(
          (width - bannerWidth) / 2,
          (height - bannerHeight) / 2,
          bannerWidth,
          bannerHeight);

      g2.setColor(COMPLETED_OVERLAY_BG);
      g2.fillRoundRect(banner.x, banner.y, banner.width, banner.height, 28, 28);
      g2.setColor(COMPLETED_OVERLAY_FG);
      g2.drawString(
          text,
          banner.x + (banner.width - fm.stringWidth(text)) / 2,
          banner.y + (banner.height - fm.getHeight()) / 2 + fm.getAscent());
    }

    private void onMousePressed(final Point point) {
      if (this.stageSolved || this.completed) {
        return;
      }
      final BoardLayout layout = this.resolveLayout();
      if (this.tryStartCardDrag(point, layout.cards())) {
        return;
      }
      this.tryStartHeapDrag(point, layout.heap());
    }

    private boolean tryStartCardDrag(final Point point, final List<Rectangle> cards) {
      for (int i = 0; i < cards.size(); i++) {
        final Integer id = this.cardBars.get(i);
        final Rectangle rect = this.cardBarRect(cards.get(i));
        if (id != null && rect.contains(point)) {
          this.startDrag(id, DragSource.CARD, i, point, rect);
          return true;
        }
      }
      return false;
    }

    private void tryStartHeapDrag(final Point point, final Rectangle heap) {
      for (final BarPlacement placement : this.heapBarPlacements(heap)) {
        if (placement.rect().contains(point)) {
          this.startDrag(placement.id(), DragSource.HEAP, -1, point, placement.rect());
          return;
        }
      }
    }

    private void startDrag(
        final int id,
        final DragSource source,
        final int sourceCard,
        final Point point,
        final Rectangle rect) {
      this.stopAnswerFlash();
      this.dragId = id;
      this.dragSource = source;
      this.dragSourceCard = sourceCard;
      this.dragPointer = point;
      this.dragGrabOffsetX = point.x - rect.x;
      this.dragGrabOffsetY = point.y - rect.y;
      this.dragBarWidth = rect.width;
      this.repaint();
    }

    private void onMouseDragged(final Point point) {
      if (this.dragId < 0) {
        return;
      }
      this.dragPointer = point;
      this.repaint();
    }

    private void onMouseReleased(final Point point) {
      if (this.dragId < 0) {
        return;
      }
      final BoardLayout layout = this.resolveLayout();
      final int cardIndex = this.cardIndexAt(layout.cards(), point);
      if (cardIndex >= 0) {
        this.dropOnCard(cardIndex);
      } else if (layout.heap().contains(point)) {
        this.dropOnHeap();
      }
      this.cancelDrag();
      this.stageSolved = this.isSolved();
      if (this.stageSolved) {
        this.onSolved.run();
      }
      this.repaint();
    }

    private int cardIndexAt(final List<Rectangle> cards, final Point point) {
      for (int i = 0; i < cards.size(); i++) {
        if (cards.get(i).contains(point)) {
          return i;
        }
      }
      return -1;
    }

    private void dropOnCard(final int cardIndex) {
      final Integer displaced = this.cardBars.get(cardIndex);
      this.removeBarEverywhere(this.dragId);
      if (displaced != null && displaced != this.dragId) {
        this.addToHeap(displaced);
      }
      this.cardBars.put(cardIndex, this.dragId);
    }

    private void dropOnHeap() {
      this.removeBarEverywhere(this.dragId);
      this.addToHeap(this.dragId);
    }

    private void removeBarEverywhere(final int id) {
      this.heapOrder.remove(Integer.valueOf(id));
      this.cardBars.values().removeIf(placedId -> placedId == id);
    }

    private void addToHeap(final int id) {
      if (!this.heapOrder.contains(id)) {
        this.heapOrder.add(id);
      }
    }

    private boolean isDraggingFromHeap(final int id) {
      return this.dragId == id && this.dragSource == DragSource.HEAP;
    }

    private boolean isDraggingFromCard(final int cardIndex, final int id) {
      return this.dragId == id
          && this.dragSource == DragSource.CARD
          && this.dragSourceCard == cardIndex;
    }

    private void cancelDrag() {
      this.dragId = -1;
      this.dragSource = DragSource.NONE;
      this.dragSourceCard = -1;
      this.dragPointer = new Point();
      this.dragGrabOffsetX = 0;
      this.dragGrabOffsetY = 0;
      this.dragBarWidth = 0;
    }

    private boolean isSolved() {
      if (this.stageEntries.isEmpty() || this.cardBars.size() != this.stageEntries.size()) {
        return false;
      }
      for (int i = 0; i < this.stageEntries.size(); i++) {
        if (!Integer.valueOf(this.stageEntries.get(i).id()).equals(this.cardBars.get(i))) {
          return false;
        }
      }
      return true;
    }
  }

  private record ImageEntry(int id, String text, BufferedImage image) {
  }

  private record PreparedImagesSession(
      DialogDefinition definition,
      boolean targetIsSideA,
      List<ImageEntry> entries) {
  }

  private record BoardLayout(Rectangle heap, List<Rectangle> cards) {
  }

  private record BarPlacement(int id, Rectangle rect) {
  }

  private record PrerenderedCardImage(int boundsWidth, int boundsHeight, BufferedImage image) {
    private boolean fits(final Rectangle bounds) {
      return this.boundsWidth() == bounds.width && this.boundsHeight() == bounds.height;
    }
  }
}
