package com.igormaznitsa.langtrainer.modules.crossword;

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
import com.igormaznitsa.langtrainer.modules.dialog.InputEquivalenceSupport;
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
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class CrosswordModule extends AbstractLangTrainerModule {

  private static final String CARD_SELECT = "select";
  private static final String CARD_WORK = "work";
  private static final int BOARD_SIZE = 25;
  private static final int MAX_START_WORDS_TO_TRY = 10;
  private static final int SEARCH_BEAM_WIDTH = 12;
  private static final int SEARCH_BRANCH_LIMIT = 18;
  private static final int SEARCH_CANDIDATES_PER_WORD = 2;
  private static final long SEARCH_TIME_BUDGET_NS = 5_000_000_000L;
  private static final double SEARCH_TIME_BUDGET_SECONDS = SEARCH_TIME_BUDGET_NS / 1_000_000_000.0d;
  private static final int SEARCH_PROGRESS_UPDATE_PERIOD_MS = 150;
  private static final int MIN_WORDS_IN_CROSSWORD = 3;
  private static final double CROSSWORD_FILL_RATIO = 0.92d;
  private static final int TRANSLATION_TO_BOARD_GAP_PX = 6;
  private static final Color EMPTY_CELL_BG = new Color(225, 225, 225);
  private static final Color FILLED_CELL_BG = Color.WHITE;
  private static final Color SELECTED_CELL_BG = new Color(187, 222, 251);
  private static final Color SELECTED_AIM_COLOR = new Color(198, 40, 40);
  private static final Color START_CELL_BG = new Color(250, 235, 250);
  private static final Color ALL_CORRECT_BG = new Color(200, 240, 200);
  private static final Color WRONG_WORD_BG = new Color(255, 215, 215);
  private static final Color MISSING_CHAR_BG = new Color(255, 245, 170);
  private static final Color OUTER_CELL_BORDER_COLOR = Color.BLACK;
  private static final Font CELL_FONT = LangTrainerFonts.MONO_NL_BOLD.atPoints(20f);
  private static final Icon ICON_END_GAME =
      ImageResourceLoader.loadIcon("/crossword/images/crossword-end-game.svg", 24, 24);

  private final DefaultListModel<DialogListEntry> listModel = new DefaultListModel<>();
  private final JPanel rootPanel = new JPanel(new CardLayout());
  private final javax.swing.JLabel translationLabel =
      new javax.swing.JLabel(" ", SwingConstants.CENTER);  private final JPanel boardHost = this.makeBoardHost();
  private final javax.swing.JLabel modeLabel = new javax.swing.JLabel(" ", SwingConstants.CENTER);  private final JPanel boardPanel = this.makeBoardPanel();
  private final char[][] solution = new char[BOARD_SIZE][BOARD_SIZE];
  private final char[][] userInput = new char[BOARD_SIZE][BOARD_SIZE];
  private final Set<Point> fillableCells = new HashSet<>();
  private final Set<Point> startCells = new HashSet<>();
  private final Set<Point> wrongCells = new HashSet<>();
  private File lastOpenDirectory;
  private JList<DialogListEntry> selectionList;
  private JButton endGameButton;
  private List<WordPlacement> placements = List.of();
  private boolean inputInLangA;
  private boolean gameFinished;
  private boolean revealMode;
  private boolean allCellsFilled;
  private int selectedRow;
  private int selectedCol;
  private boolean preferredHorizontalDirection = true;
  private WordPlacement lockedTypingWord;
  private boolean generationInProgress;
  private long generationStartedAtNs;
  private Timer generationProgressTimer;
  private SwingWorker<List<WordPlacement>, Void> generationWorker;
  private List<InputEquivalenceRow> activeInputEquivalenceRules = List.of();
  public CrosswordModule() {
    ClasspathLangResourceIndex.loadShared(
            CrosswordModule.class, this, "Can't load crossword resources")
        .forEach(d -> this.listModel.addElement(new DialogListEntry(d, false)));
    this.rootPanel.add(this.makeSelectPanel(), CARD_SELECT);
    this.rootPanel.add(this.makeWorkPanel(), CARD_WORK);
    this.showCard(CARD_SELECT);
  }

  @Override
  public String getName() {
    return "Crossword";
  }

  @Override
  public String getDescription() {
    return "Build words in a generated crossword by translation hints";
  }

  @Override
  public Icon getImage() {
    return ImageResourceLoader.loadIcon("/crossword/images/module-crossword.svg", 128, 128);
  }

  @Override
  public JComponent createControlForm() {
    return this.rootPanel;
  }

  @Override
  public boolean isResourceAllowed(final JsonObject resourceDescription) {
    return LangTrainerResourceAccess.visibleToModule(
        resourceDescription, LangTrainerModuleId.CROSSWORD);
  }

  @Override
  public List<KeyboardLanguage> getSupportedLanguages() {
    return KeyboardLanguage.VIRTUAL_BOARD_ALL;
  }

  @Override
  public void onActivation() {
    this.showCard(CARD_SELECT);
    SwingUtilities.invokeLater(() -> {
      if (this.selectionList != null) {
        this.selectionList.requestFocusInWindow();
      }
    });
    this.syncEndButtonEnabled();
  }

  @Override
  public void onClose() {
    this.cancelGenerationIfRunning();
    this.finishGameAndReveal();
  }

  @Override
  public void onCharClick(final char symbol) {
    if (this.generationInProgress || this.gameFinished || this.revealMode ||
        !this.isFillable(this.selectedRow, this.selectedCol)) {
      return;
    }
    if (!Character.isLetter(symbol)) {
      return;
    }
    this.applyChar(Character.toUpperCase(symbol));
  }

  @Override
  public void populateMainToolbar(final JPanel eastToolbar) {
    final JButton endButton = new JButton();
    endButton.setIcon(ICON_END_GAME);
    endButton.setText("");
    endButton.setToolTipText("Reveal crossword and finish current game");
    endButton.setFocusPainted(false);
    endButton.addActionListener(event -> this.finishGameAndReveal());
    eastToolbar.add(endButton);
    this.endGameButton = endButton;
    this.syncEndButtonEnabled();
  }

  private JPanel makeSelectPanel() {
    final ResourceListSelectPanel.Result view = ResourceListSelectPanel.build(
        this.listModel,
        ResourceListSelectPanel.Appearance.FLY_GAME,
        "Select crossword resource",
        "Choose language and start",
        "Open from file",
        this::chooseLanguageAndStart,
        this::openFromFile);
    this.selectionList = view.list();
    return view.panel();
  }

  private JPanel makeWorkPanel() {
    final JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    this.translationLabel.setOpaque(false);
    this.translationLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
    this.translationLabel.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(34f));
    this.translationLabel.setForeground(new Color(20, 20, 20));

    this.modeLabel.setOpaque(true);
    this.modeLabel.setBackground(new Color(227, 242, 253));
    this.modeLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    this.modeLabel.setFont(LangTrainerFonts.MONO_NL_REGULAR.atPoints(18f));

    panel.add(this.modeLabel, BorderLayout.NORTH);

    this.boardPanel.setBorder(BorderFactory.createLineBorder(new Color(120, 144, 156), 2));
    this.boardPanel.setBackground(EMPTY_CELL_BG);
    this.boardPanel.setFocusable(true);
    this.boardPanel.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(final KeyEvent event) {
        CrosswordModule.this.processKeyPressed(event);
      }

      @Override
      public void keyTyped(final KeyEvent event) {
        CrosswordModule.this.processKeyTyped(event);
      }
    });
    this.boardPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent event) {
        CrosswordModule.this.processBoardMouseClick(event.getX(), event.getY());
      }
    });

    this.boardHost.add(this.boardPanel);
    final JPanel centerStack = new JPanel(new BorderLayout(0, 0));
    centerStack.setOpaque(false);
    centerStack.add(this.translationLabel, BorderLayout.NORTH);
    centerStack.add(this.boardHost, BorderLayout.CENTER);
    panel.add(centerStack, BorderLayout.CENTER);
    return panel;
  }

  private JPanel makeBoardHost() {
    return new JPanel(null) {
      @Override
      public void doLayout() {
        CrosswordModule.this.layoutBoardByHostSize(this.getWidth(), this.getHeight());
      }

      @Override
      protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        CrosswordModule.this.layoutBoardByHostSize(this.getWidth(), this.getHeight());
      }
    };
  }

  private JPanel makeBoardPanel() {
    return new JPanel() {
      @Override
      protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        CrosswordModule.this.paintCrosswordBoard(graphics);
      }
    };
  }

  private void paintCrosswordBoard(final Graphics graphics) {
    final Graphics2D g2 = (Graphics2D) graphics.create();
    try {
      this.applyCrosswordQualityHints(g2);
      final Set<GridEdge> edges = new HashSet<>();
      final PaintViewport viewport = this.resolvePaintViewport();
      if (viewport == null) {
        return;
      }
      final Font drawFont = this.resolveCellFontForViewport(viewport);
      g2.setFont(drawFont);
      final FontMetrics metrics = g2.getFontMetrics(drawFont);
      for (int row = 0; row < BOARD_SIZE; row++) {
        for (int col = 0; col < BOARD_SIZE; col++) {
          if (!this.isFillable(row, col)) {
            continue;
          }
          final CellRect cell = this.resolveCellRect(row, col, viewport);
          this.paintCellBackground(g2, row, col, cell);
          this.paintCellText(g2, row, col, cell, metrics);
          this.collectCellEdges(edges, row, col);
        }
      }
      this.paintGridEdges(g2, edges, viewport);
      this.paintSelectedCellAim(g2, viewport);
    } finally {
      g2.dispose();
    }
  }

  private void paintSelectedCellAim(final Graphics2D g2, final PaintViewport viewport) {
    if (this.gameFinished || this.revealMode ||
        !this.isFillable(this.selectedRow, this.selectedCol)) {
      return;
    }
    final CellRect cell = this.resolveCellRect(this.selectedRow, this.selectedCol, viewport);
    final int centerX = cell.x() + cell.width() / 2;
    final int centerY = cell.y() + cell.height() / 2;
    final int radius = Math.max(10, (int) Math.round(cell.width() * 0.74d));
    final int tickInset = Math.max(1, (int) Math.round(cell.width() * 0.1d));
    final int tickLength = Math.max(6, (int) Math.round(cell.width() * 0.38d));
    final int strokeWidth = Math.max(2, cell.width() / 14);
    final java.awt.Stroke oldStroke = g2.getStroke();
    final int diagonalInner = (int) Math.round((radius + tickInset) / Math.sqrt(2.0d));
    final int diagonalOuter = (int) Math.round((radius + tickInset + tickLength) / Math.sqrt(2.0d));

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(SELECTED_AIM_COLOR);
    g2.drawLine(
        centerX - diagonalInner,
        centerY - diagonalInner,
        centerX - diagonalOuter,
        centerY - diagonalOuter);
    g2.drawLine(
        centerX + diagonalInner,
        centerY - diagonalInner,
        centerX + diagonalOuter,
        centerY - diagonalOuter);
    g2.drawLine(
        centerX - diagonalInner,
        centerY + diagonalInner,
        centerX - diagonalOuter,
        centerY + diagonalOuter);
    g2.drawLine(
        centerX + diagonalInner,
        centerY + diagonalInner,
        centerX + diagonalOuter,
        centerY + diagonalOuter);
    g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
    g2.setStroke(oldStroke);
  }

  private Font resolveCellFontForViewport(final PaintViewport viewport) {
    final float baseSize = Math.max(12f, viewport.cellSize() * 0.62f);
    return CELL_FONT.deriveFont(baseSize);
  }

  private void applyCrosswordQualityHints(final Graphics2D g2) {
    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
        RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
  }

  private CellRect resolveCellRect(final int row, final int col, final PaintViewport viewport) {
    final int x = viewport.offsetX() + (col - viewport.minCol()) * viewport.cellSize();
    final int y = viewport.offsetY() + (row - viewport.minRow()) * viewport.cellSize();
    return new CellRect(x, y, viewport.cellSize(), viewport.cellSize());
  }

  private void paintCellBackground(final Graphics2D g2, final int row, final int col,
                                   final CellRect cell) {
    final boolean selected =
        !this.gameFinished && !this.revealMode && row == this.selectedRow &&
            col == this.selectedCol;
    final char entered = this.userInput[row][col];
    final char expected = this.solution[row][col];
    final boolean wrong = this.allCellsFilled && this.wrongCells.contains(new Point(row, col));
    final Color baseColor;
    if (this.revealMode) {
      if (entered == 0) {
        baseColor = MISSING_CHAR_BG;
      } else if (entered == expected) {
        baseColor = ALL_CORRECT_BG;
      } else {
        baseColor = WRONG_WORD_BG;
      }
    } else if (this.gameFinished && this.allCellsFilled) {
      baseColor = ALL_CORRECT_BG;
    } else {
      baseColor = wrong ? WRONG_WORD_BG : this.startCells.contains(new Point(row, col))
                                          ? START_CELL_BG
                                          : FILLED_CELL_BG;
    }
    g2.setColor(selected ? SELECTED_CELL_BG : baseColor);
    g2.fillRect(cell.x(), cell.y(), cell.width(), cell.height());
  }

  private void paintCellText(
      final Graphics2D g2,
      final int row,
      final int col,
      final CellRect cell,
      final FontMetrics metrics) {
    final char user = this.userInput[row][col];
    final String text;
    if (this.revealMode) {
      text = String.valueOf(this.solution[row][col]);
    } else if (this.gameFinished && user == 0) {
      text = String.valueOf(this.solution[row][col]);
    } else {
      text = user == 0 ? "" : String.valueOf(user);
    }
    if (text.isEmpty()) {
      return;
    }
    final int textW = metrics.stringWidth(text);
    final int textX = cell.x() + Math.max(0, (cell.width() - textW) / 2);
    final int textY =
        cell.y() + Math.max(0, (cell.height() - metrics.getHeight()) / 2) + metrics.getAscent();
    g2.setColor(Color.BLACK);
    g2.drawString(text, textX, textY);
  }

  private void collectCellEdges(final Set<GridEdge> edges, final int row, final int col) {
    edges.add(GridEdge.of(col, row, col + 1, row));
    edges.add(GridEdge.of(col, row + 1, col + 1, row + 1));
    edges.add(GridEdge.of(col, row, col, row + 1));
    edges.add(GridEdge.of(col + 1, row, col + 1, row + 1));
  }

  private void paintGridEdges(
      final Graphics2D g2,
      final Set<GridEdge> edges,
      final PaintViewport viewport) {
    g2.setColor(OUTER_CELL_BORDER_COLOR);
    for (final GridEdge edge : edges) {
      final int x1 = this.toPixelX(edge.x1(), viewport);
      final int y1 = this.toPixelY(edge.y1(), viewport);
      final int x2 = this.toPixelX(edge.x2(), viewport);
      final int y2 = this.toPixelY(edge.y2(), viewport);
      g2.drawLine(x1, y1, x2, y2);
    }
  }

  private int toPixelX(final int gridX, final PaintViewport viewport) {
    return viewport.offsetX() + (gridX - viewport.minCol()) * viewport.cellSize();
  }

  private int toPixelY(final int gridY, final PaintViewport viewport) {
    return viewport.offsetY() + (gridY - viewport.minRow()) * viewport.cellSize();
  }

  private PaintViewport resolvePaintViewport() {
    if (this.fillableCells.isEmpty()) {
      return null;
    }
    int minRow = BOARD_SIZE;
    int maxRow = -1;
    int minCol = BOARD_SIZE;
    int maxCol = -1;
    for (final Point point : this.fillableCells) {
      minRow = Math.min(minRow, point.x);
      maxRow = Math.max(maxRow, point.x);
      minCol = Math.min(minCol, point.y);
      maxCol = Math.max(maxCol, point.y);
    }
    final int usedRows = maxRow - minRow + 1;
    final int usedCols = maxCol - minCol + 1;
    final int drawW = Math.max(1, (int) (this.boardPanel.getWidth() * CROSSWORD_FILL_RATIO));
    final int drawH = Math.max(1, (int) (this.boardPanel.getHeight() * CROSSWORD_FILL_RATIO));
    final int cellByWidth = Math.max(1, drawW / usedCols);
    final int cellByHeight = Math.max(1, drawH / usedRows);
    final int cellSize = Math.min(cellByWidth, cellByHeight);
    final int actualW = usedCols * cellSize;
    final int actualH = usedRows * cellSize;
    final int offsetX = (this.boardPanel.getWidth() - actualW) / 2;
    final int offsetY = (this.boardPanel.getHeight() - actualH) / 2;
    return new PaintViewport(minRow, minCol, cellSize, offsetX, offsetY);
  }

  private void layoutBoardByHostSize(final int hostWidth, final int hostHeight) {
    final int targetSide = (int) (Math.min(hostWidth, hostHeight) * 0.8d);
    if (targetSide <= 0) {
      return;
    }
    final int side = Math.max(BOARD_SIZE, targetSide - (targetSide % BOARD_SIZE));
    final int x = (hostWidth - side) / 2;
    final int y = Math.max(0, Math.min(TRANSLATION_TO_BOARD_GAP_PX, hostHeight - side));
    this.boardPanel.setBounds(x, y, side, side);
  }

  private void processKeyTyped(final KeyEvent event) {
    if (this.generationInProgress || this.gameFinished || this.revealMode ||
        !this.isFillable(this.selectedRow, this.selectedCol)) {
      return;
    }
    final char ch = event.getKeyChar();
    if (!Character.isLetter(ch)) {
      return;
    }
    this.applyChar(Character.toUpperCase(ch));
    event.consume();
  }

  private void processBoardMouseClick(final int x, final int y) {
    if (this.generationInProgress || this.gameFinished || this.revealMode) {
      return;
    }
    final PaintViewport viewport = this.resolvePaintViewport();
    if (viewport == null) {
      return;
    }
    final int relativeX = x - viewport.offsetX();
    final int relativeY = y - viewport.offsetY();
    if (relativeX < 0 || relativeY < 0) {
      return;
    }
    final int col = viewport.minCol() + relativeX / viewport.cellSize();
    final int row = viewport.minRow() + relativeY / viewport.cellSize();
    if (this.notInsideBoard(row, col) || !this.isFillable(row, col)) {
      return;
    }
    this.selectedRow = row;
    this.selectedCol = col;
    this.lockedTypingWord = null;
    this.repaintBoard();
    this.boardPanel.requestFocusInWindow();
  }

  private void processKeyPressed(final KeyEvent event) {
    if (this.fillableCells.isEmpty()) {
      return;
    }
    if (this.generationInProgress || this.gameFinished || this.revealMode) {
      return;
    }
    if (event.getKeyCode() == KeyEvent.VK_BACK_SPACE || event.getKeyCode() == KeyEvent.VK_DELETE) {
      if (this.isFillable(this.selectedRow, this.selectedCol)) {
        this.userInput[this.selectedRow][this.selectedCol] = 0;
        this.checkBoardState();
        this.repaintBoard();
      }
      event.consume();
      return;
    }
    this.moveSelection(event.getKeyCode());
  }

  private void moveSelection(final int keyCode) {
    final int deltaRow = keyCode == KeyEvent.VK_UP ? -1 : keyCode == KeyEvent.VK_DOWN ? 1 : 0;
    final int deltaCol = keyCode == KeyEvent.VK_LEFT ? -1 : keyCode == KeyEvent.VK_RIGHT ? 1 : 0;
    if (deltaRow == 0 && deltaCol == 0) {
      return;
    }
    this.rememberPreferredDirection(deltaRow, deltaCol);
    this.lockedTypingWord = null;
    this.selectNextFillableCell(deltaRow, deltaCol);
    this.repaintBoard();
  }

  private void rememberPreferredDirection(final int deltaRow, final int deltaCol) {
    if (deltaCol != 0) {
      this.preferredHorizontalDirection = true;
    } else if (deltaRow != 0) {
      this.preferredHorizontalDirection = false;
    }
  }

  private void selectNextFillableCell(final int deltaRow, final int deltaCol) {
    int row = this.selectedRow;
    int col = this.selectedCol;
    for (int guard = 0; guard < BOARD_SIZE * BOARD_SIZE; guard++) {
      row = (row + deltaRow + BOARD_SIZE) % BOARD_SIZE;
      col = (col + deltaCol + BOARD_SIZE) % BOARD_SIZE;
      if (this.isFillable(row, col)) {
        this.selectedRow = row;
        this.selectedCol = col;
        return;
      }
    }
  }

  private void applyChar(final char ch) {
    this.ensureLockedTypingWord();
    this.userInput[this.selectedRow][this.selectedCol] = this.resolveCrosswordInputChar(ch);
    this.moveCursorToNextCellInWord();
    this.checkBoardState();
    this.repaintBoard();
  }

  private char resolveCrosswordInputChar(final char typedChar) {
    final char expectedChar = this.solution[this.selectedRow][this.selectedCol];
    final Optional<String> mapped = InputEquivalenceSupport.matchInputEquivalence(
        String.valueOf(typedChar),
        String.valueOf(expectedChar),
        this.activeInputEquivalenceRules);
    if (mapped.isEmpty() || mapped.get().isEmpty()) {
      return typedChar;
    }
    return Character.toUpperCase(mapped.get().charAt(0));
  }

  private void ensureLockedTypingWord() {
    if (this.lockedTypingWord != null
        && this.cellBelongsToWord(this.selectedRow, this.selectedCol, this.lockedTypingWord)) {
      return;
    }
    this.lockedTypingWord = this.resolveWordForCell(this.selectedRow, this.selectedCol);
  }

  private void moveCursorToNextCellInWord() {
    final WordPlacement word = this.lockedTypingWord != null
        ? this.lockedTypingWord
        : this.resolveWordForCell(this.selectedRow, this.selectedCol);
    if (word == null) {
      return;
    }
    final int offset =
        word.horizontal() ? this.selectedCol - word.col() : this.selectedRow - word.row();
    final int nextOffset = offset + 1;
    if (nextOffset >= word.word().length()) {
      return;
    }
    final int nextRow = word.row() + (word.horizontal() ? 0 : nextOffset);
    final int nextCol = word.col() + (word.horizontal() ? nextOffset : 0);
    if (!this.isFillable(nextRow, nextCol)) {
      return;
    }
    this.selectedRow = nextRow;
    this.selectedCol = nextCol;
  }

  private void checkBoardState() {
    this.allCellsFilled = this.fillableCells.stream()
        .allMatch(point -> this.userInput[point.x][point.y] != 0);
    if (!this.allCellsFilled) {
      this.wrongCells.clear();
      this.gameFinished = false;
      this.revealMode = false;
      return;
    }
    this.wrongCells.clear();
    for (final WordPlacement placement : this.placements) {
      if (this.isWordCorrect(placement)) {
        continue;
      }
      this.wrongCells.addAll(this.collectWordCells(placement));
    }
    this.gameFinished = this.wrongCells.isEmpty();
    this.revealMode = false;
    this.syncEndButtonEnabled();
  }

  private boolean isWordCorrect(final WordPlacement placement) {
    for (int i = 0; i < placement.word().length(); i++) {
      final int row = placement.row() + (placement.horizontal() ? 0 : i);
      final int col = placement.col() + (placement.horizontal() ? i : 0);
      if (this.userInput[row][col] != this.solution[row][col]) {
        return false;
      }
    }
    return true;
  }

  private List<Point> collectWordCells(final WordPlacement placement) {
    final List<Point> cells = new ArrayList<>(placement.word().length());
    for (int i = 0; i < placement.word().length(); i++) {
      final int row = placement.row() + (placement.horizontal() ? 0 : i);
      final int col = placement.col() + (placement.horizontal() ? i : 0);
      cells.add(new Point(row, col));
    }
    return cells;
  }

  private void chooseLanguageAndStart(final DialogDefinition definition) {
    final JComboBox<String> chooser =
        new JComboBox<>(new String[] {definition.langA(), definition.langB()});
    final int option = JOptionPane.showConfirmDialog(
        this.rootPanel,
        chooser,
        "Choose target input language",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (option != JOptionPane.OK_OPTION) {
      return;
    }
    final String selected = (String) chooser.getSelectedItem();
    this.inputInLangA = definition.langA().equals(selected);
    this.startCrossword(definition);
  }

  private void startCrossword(final DialogDefinition definition) {
    if (this.generationInProgress) {
      return;
    }
    this.activeInputEquivalenceRules = definition.inputEqu();
    final List<WordPair> words = this.extractSingleWordPairs(definition);
    if (words.size() < MIN_WORDS_IN_CROSSWORD) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "Not enough single-word pairs for crossword.",
          "Crossword",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    this.startCrosswordGeneration(words);
  }

  private void startCrosswordGeneration(final List<WordPair> words) {
    this.generationInProgress = true;
    this.generationStartedAtNs = System.nanoTime();
    this.resetBoardState();
    this.placements = List.of();
    this.translationLabel.setText("Searching best crossword variant...");
    this.modeLabel.setText(
        String.format(Locale.ROOT, "Generating crossword... %.1f / %.1f s", 0.0d,
            SEARCH_TIME_BUDGET_SECONDS));
    this.rootPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    this.showCard(CARD_WORK);
    this.boardPanel.repaint();
    this.syncEndButtonEnabled();
    this.startGenerationProgressTimer();

    this.generationWorker = new SwingWorker<>() {
      @Override
      protected List<WordPlacement> doInBackground() {
        return CrosswordModule.this.buildCrossword(words);
      }

      @Override
      protected void done() {
        CrosswordModule.this.finishGenerationProgress();
        try {
          if (this.isCancelled()) {
            CrosswordModule.this.showCard(CARD_SELECT);
            return;
          }
          final List<WordPlacement> generated = this.get();
          if (generated.size() < MIN_WORDS_IN_CROSSWORD) {
            JOptionPane.showMessageDialog(
                CrosswordModule.this.rootPanel,
                "Can't build crossword from selected words.",
                "Crossword",
                JOptionPane.WARNING_MESSAGE);
            CrosswordModule.this.showCard(CARD_SELECT);
            return;
          }
          CrosswordModule.this.applyGeneratedCrossword(generated);
        } catch (final InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
          CrosswordModule.this.showCard(CARD_SELECT);
        } catch (final CancellationException cancelledException) {
          CrosswordModule.this.showCard(CARD_SELECT);
        } catch (final ExecutionException executionException) {
          final Throwable cause = executionException.getCause() == null
              ? executionException
              : executionException.getCause();
          JOptionPane.showMessageDialog(
              CrosswordModule.this.rootPanel,
              cause.getMessage(),
              "Can't build crossword",
              JOptionPane.ERROR_MESSAGE);
          CrosswordModule.this.showCard(CARD_SELECT);
        }
      }
    };
    this.generationWorker.execute();
  }

  private void applyGeneratedCrossword(final List<WordPlacement> generated) {
    this.resetBoardState();
    this.placements = generated;
    this.placeWordsOnBoard(generated);
    this.selectStartOfLongestWord();
    this.preferredHorizontalDirection = true;
    this.refreshTranslationLabel();
    this.modeLabel.setText("Use arrows to move and type letters to fill selected cell");
    this.showCard(CARD_WORK);
    this.repaintBoard();
    this.syncEndButtonEnabled();
    SwingUtilities.invokeLater(this.boardPanel::requestFocusInWindow);
  }

  private void startGenerationProgressTimer() {
    final Timer previousTimer = this.generationProgressTimer;
    if (previousTimer != null) {
      previousTimer.stop();
    }
    final Timer timer = new Timer(SEARCH_PROGRESS_UPDATE_PERIOD_MS, event -> {
      final double elapsed = (System.nanoTime() - this.generationStartedAtNs) / 1_000_000_000.0d;
      final double boundedElapsed = Math.min(SEARCH_TIME_BUDGET_SECONDS, elapsed);
      this.modeLabel.setText(
          String.format(Locale.ROOT, "Generating crossword... %.1f / %.1f s", boundedElapsed,
              SEARCH_TIME_BUDGET_SECONDS));
    });
    timer.setRepeats(true);
    timer.start();
    this.generationProgressTimer = timer;
  }

  private void finishGenerationProgress() {
    this.generationInProgress = false;
    this.rootPanel.setCursor(Cursor.getDefaultCursor());
    final Timer timer = this.generationProgressTimer;
    if (timer != null) {
      timer.stop();
      this.generationProgressTimer = null;
    }
    this.generationWorker = null;
  }

  private void cancelGenerationIfRunning() {
    final SwingWorker<List<WordPlacement>, Void> worker = this.generationWorker;
    if (worker != null) {
      worker.cancel(true);
    }
    this.finishGenerationProgress();
  }

  private void resetBoardState() {
    for (int row = 0; row < BOARD_SIZE; row++) {
      Arrays.fill(this.solution[row], (char) 0);
      Arrays.fill(this.userInput[row], (char) 0);
    }
    this.fillableCells.clear();
    this.startCells.clear();
    this.wrongCells.clear();
    this.gameFinished = false;
    this.revealMode = false;
    this.allCellsFilled = false;
    this.preferredHorizontalDirection = true;
    this.lockedTypingWord = null;
  }

  private void placeWordsOnBoard(final List<WordPlacement> generated) {
    generated.forEach(this::placeSingleWord);
  }

  private void placeSingleWord(final WordPlacement placement) {
    final String word = placement.word();
    for (int i = 0; i < word.length(); i++) {
      final int row = placement.row() + (placement.horizontal() ? 0 : i);
      final int col = placement.col() + (placement.horizontal() ? i : 0);
      this.solution[row][col] = word.charAt(i);
      this.fillableCells.add(new Point(row, col));
    }
    this.startCells.add(new Point(placement.row(), placement.col()));
  }

  private void selectStartOfLongestWord() {
    final Point selected = this.placements.stream()
        .max(
            Comparator.comparingInt((WordPlacement placement) -> placement.word().length())
                .thenComparingInt(WordPlacement::row)
                .thenComparingInt(WordPlacement::col))
        .map(placement -> new Point(placement.row(), placement.col()))
        .orElseGet(() -> this.fillableCells.stream().findFirst().orElse(new Point(0, 0)));
    this.selectedRow = selected.x;
    this.selectedCol = selected.y;
  }

  private List<WordPair> extractSingleWordPairs(final DialogDefinition definition) {
    return definition.lines().stream()
        .map(this::toWordPair)
        .filter(Objects::nonNull)
        .filter(pair -> this.isSingleWord(pair.word()))
        .toList();
  }

  private WordPair toWordPair(final DialogLine line) {
    final String rawWord = this.inputInLangA ? line.a() : line.b();
    final String rawTranslation = this.inputInLangA ? line.b() : line.a();
    if (rawWord == null || rawTranslation == null) {
      return null;
    }
    final String word = rawWord.trim().toUpperCase(Locale.ROOT);
    final String translation = rawTranslation.trim();
    if (word.isEmpty() || translation.isEmpty()) {
      return null;
    }
    return new WordPair(word, translation);
  }

  private boolean isSingleWord(final String word) {
    if (word.isBlank() || word.contains(" ")) {
      return false;
    }
    return word.chars().allMatch(ch -> Character.isLetter(ch) || ch == '-');
  }

  private List<WordPlacement> buildCrossword(final List<WordPair> words) {
    if (words.isEmpty()) {
      return List.of();
    }
    final long startedAt = System.nanoTime();
    final Map<Character, Long> letterFrequency = this.makeLetterFrequency(words);
    final List<WordPair> rankedWords = this.rankWordsForStart(words, letterFrequency);
    final int startsToTry = Math.min(MAX_START_WORDS_TO_TRY, rankedWords.size());
    List<WordPlacement> best = List.of();
    for (int i = 0; i < startsToTry; i++) {
      if (System.nanoTime() - startedAt >= SEARCH_TIME_BUDGET_NS) {
        break;
      }
      final WordPair startWord = rankedWords.get(i);
      final List<WordPlacement> horizontal =
          this.tryBuildFromStart(words, startWord, true, letterFrequency, startedAt);
      if (this.isBetterPlacementSet(horizontal, best)) {
        best = horizontal;
      }
      if (System.nanoTime() - startedAt >= SEARCH_TIME_BUDGET_NS) {
        break;
      }
      final List<WordPlacement> vertical =
          this.tryBuildFromStart(words, startWord, false, letterFrequency, startedAt);
      if (this.isBetterPlacementSet(vertical, best)) {
        best = vertical;
      }
      if (best.size() == words.size()) {
        break;
      }
    }
    return best;
  }

  private List<WordPlacement> tryBuildFromStart(
      final List<WordPair> allWords,
      final WordPair startWord,
      final boolean horizontal,
      final Map<Character, Long> letterFrequency,
      final long startedAt) {
    final char[][] board = new char[BOARD_SIZE][BOARD_SIZE];
    final List<WordPlacement> placed = new ArrayList<>();
    final Map<Character, Set<AnchorCell>> boardAnchors = new HashMap<>();
    final WordPlacement startPlacement = this.makeCenteredPlacement(startWord, horizontal);
    if (!this.canPlace(startPlacement, board)) {
      return List.of();
    }
    this.applyPlacement(startPlacement, board);
    placed.add(startPlacement);
    this.addPlacementAnchors(startPlacement, boardAnchors);

    final List<WordPair> remaining = new ArrayList<>(allWords);
    remaining.remove(startWord);
    List<SearchState> beam = List.of(new SearchState(
        board,
        placed,
        remaining,
        boardAnchors,
        this.evaluateStateScore(placed, board)));
    List<WordPlacement> best = placed;

    while (!beam.isEmpty() && System.nanoTime() - startedAt < SEARCH_TIME_BUDGET_NS) {
      final List<SearchState> nextBeam = new ArrayList<>();
      for (final SearchState state : beam) {
        if (state.remaining().isEmpty()) {
          continue;
        }
        final List<PlacementCandidate> candidates = this.findPlacementCandidates(
            state.remaining(),
            state.board(),
            letterFrequency,
            state.boardAnchors(),
            SEARCH_BRANCH_LIMIT);
        if (candidates.isEmpty()) {
          continue;
        }
        for (final PlacementCandidate candidate : candidates) {
          final char[][] nextBoard = this.copyBoard(state.board());
          final Map<Character, Set<AnchorCell>> nextAnchors =
              this.copyAnchors(state.boardAnchors());
          final List<WordPlacement> nextPlaced = new ArrayList<>(state.placed());
          final List<WordPair> nextRemaining = new ArrayList<>(state.remaining());
          this.applyPlacement(candidate.placement(), nextBoard);
          nextPlaced.add(candidate.placement());
          this.addPlacementAnchors(candidate.placement(), nextAnchors);
          nextRemaining.remove(candidate.word());
          final SearchState nextState = new SearchState(
              nextBoard,
              nextPlaced,
              nextRemaining,
              nextAnchors,
              this.evaluateStateScore(nextPlaced, nextBoard));
          nextBeam.add(nextState);
          if (this.isBetterPlacementSet(nextPlaced, best)) {
            best = nextPlaced;
          }
        }
      }
      if (nextBeam.isEmpty()) {
        break;
      }
      beam = nextBeam.stream()
          .sorted(Comparator.comparingInt(SearchState::score).reversed())
          .limit(SEARCH_BEAM_WIDTH)
          .toList();
    }
    return best;
  }

  private List<PlacementCandidate> findPlacementCandidates(
      final List<WordPair> remaining,
      final char[][] board,
      final Map<Character, Long> letterFrequency,
      final Map<Character, Set<AnchorCell>> boardAnchors,
      final int limit) {
    final List<CandidateChoice> candidates = new ArrayList<>();
    for (final WordPair word : remaining) {
      final List<WordPlacement> options = this.findAllPlacements(word, board, boardAnchors);
      if (options.isEmpty()) {
        continue;
      }
      final int optionCount = options.size();
      options.stream()
          .map(option -> new CandidateChoice(
              new PlacementCandidate(word, option,
                  this.scorePlacement(option, board, letterFrequency)),
              optionCount,
              this.countIntersections(option, board)))
          .sorted(this::compareCandidateChoices)
          .limit(SEARCH_CANDIDATES_PER_WORD)
          .forEach(candidates::add);
    }
    return candidates.stream()
        .sorted(this::compareCandidateChoices)
        .limit(limit)
        .map(CandidateChoice::candidate)
        .toList();
  }

  private int compareCandidateChoices(final CandidateChoice left, final CandidateChoice right) {
    final int byOptions = Integer.compare(left.optionCount(), right.optionCount());
    if (byOptions != 0) {
      return byOptions;
    }
    final int byIntersections = Integer.compare(right.intersections(), left.intersections());
    if (byIntersections != 0) {
      return byIntersections;
    }
    return Integer.compare(right.candidate().score(), left.candidate().score());
  }

  private char[][] copyBoard(final char[][] source) {
    final char[][] copy = new char[BOARD_SIZE][BOARD_SIZE];
    for (int row = 0; row < BOARD_SIZE; row++) {
      System.arraycopy(source[row], 0, copy[row], 0, BOARD_SIZE);
    }
    return copy;
  }

  private Map<Character, Set<AnchorCell>> copyAnchors(
      final Map<Character, Set<AnchorCell>> source) {
    final Map<Character, Set<AnchorCell>> copy = new HashMap<>();
    source.forEach((key, value) -> copy.put(key, new HashSet<>(value)));
    return copy;
  }

  private int evaluateStateScore(final List<WordPlacement> placed, final char[][] board) {
    if (placed.isEmpty()) {
      return Integer.MIN_VALUE;
    }
    final Bounds bounds = this.resolveBounds(board);
    if (bounds == null) {
      return Integer.MIN_VALUE;
    }
    final int area = bounds.height() * bounds.width();
    final int perimeter = bounds.height() + bounds.width();
    return placed.size() * 1_000_000 - area * 200 - perimeter * 20;
  }

  private Bounds resolveBounds(final char[][] board) {
    int minRow = BOARD_SIZE;
    int maxRow = -1;
    int minCol = BOARD_SIZE;
    int maxCol = -1;
    for (int row = 0; row < BOARD_SIZE; row++) {
      for (int col = 0; col < BOARD_SIZE; col++) {
        if (board[row][col] == 0) {
          continue;
        }
        minRow = Math.min(minRow, row);
        maxRow = Math.max(maxRow, row);
        minCol = Math.min(minCol, col);
        maxCol = Math.max(maxCol, col);
      }
    }
    if (maxRow < 0) {
      return null;
    }
    return new Bounds(minRow, maxRow, minCol, maxCol);
  }

  private boolean isBetterPlacementSet(
      final List<WordPlacement> candidate, final List<WordPlacement> currentBest) {
    if (candidate.size() != currentBest.size()) {
      return candidate.size() > currentBest.size();
    }
    if (candidate.isEmpty()) {
      return false;
    }
    final int candidateArea = this.resolveBoundingArea(candidate);
    final int bestArea = this.resolveBoundingArea(currentBest);
    if (candidateArea != bestArea) {
      return candidateArea < bestArea;
    }
    return this.totalWordLength(candidate) > this.totalWordLength(currentBest);
  }

  private int resolveBoundingArea(final List<WordPlacement> placements) {
    int minRow = BOARD_SIZE;
    int maxRow = -1;
    int minCol = BOARD_SIZE;
    int maxCol = -1;
    for (final WordPlacement placement : placements) {
      final int endRow =
          placement.row() + (placement.horizontal() ? 0 : placement.word().length() - 1);
      final int endCol =
          placement.col() + (placement.horizontal() ? placement.word().length() - 1 : 0);
      minRow = Math.min(minRow, placement.row());
      maxRow = Math.max(maxRow, endRow);
      minCol = Math.min(minCol, placement.col());
      maxCol = Math.max(maxCol, endCol);
    }
    if (maxRow < 0) {
      return Integer.MAX_VALUE;
    }
    return (maxRow - minRow + 1) * (maxCol - minCol + 1);
  }

  private int totalWordLength(final List<WordPlacement> placements) {
    return placements.stream().mapToInt(placement -> placement.word().length()).sum();
  }

  private List<WordPlacement> findAllPlacements(
      final WordPair candidate,
      final char[][] board,
      final Map<Character, Set<AnchorCell>> boardAnchors) {
    final List<WordPlacement> options = new ArrayList<>();
    final Set<PlacementKey> seen = new HashSet<>();
    final Map<Character, List<Integer>> candidateLetterIndexes =
        this.makeLetterIndexes(candidate.word());
    for (final Map.Entry<Character, List<Integer>> entry : candidateLetterIndexes.entrySet()) {
      final Set<AnchorCell> anchors = boardAnchors.getOrDefault(entry.getKey(), Set.of());
      if (anchors.isEmpty()) {
        continue;
      }
      for (final int letterIndex : entry.getValue()) {
        for (final AnchorCell anchor : anchors) {
          final boolean horizontal = !anchor.horizontal();
          final int row = horizontal ? anchor.row() : anchor.row() - letterIndex;
          final int col = horizontal ? anchor.col() - letterIndex : anchor.col();
          final PlacementKey placementKey = new PlacementKey(row, col, horizontal);
          if (!seen.add(placementKey)) {
            continue;
          }
          final WordPlacement placement =
              new WordPlacement(row, col, horizontal, candidate.word(), candidate.translation());
          if (this.canPlace(placement, board)) {
            options.add(placement);
          }
        }
      }
    }
    return options;
  }

  private Map<Character, List<Integer>> makeLetterIndexes(final String word) {
    final Map<Character, List<Integer>> indexes = new HashMap<>();
    for (int i = 0; i < word.length(); i++) {
      final char ch = word.charAt(i);
      indexes.computeIfAbsent(ch, x -> new ArrayList<>()).add(i);
    }
    return indexes;
  }

  private void addPlacementAnchors(
      final WordPlacement placement, final Map<Character, Set<AnchorCell>> boardAnchors) {
    for (int i = 0; i < placement.word().length(); i++) {
      final int row = placement.row() + (placement.horizontal() ? 0 : i);
      final int col = placement.col() + (placement.horizontal() ? i : 0);
      final char ch = placement.word().charAt(i);
      final AnchorCell anchor = new AnchorCell(row, col, placement.horizontal());
      boardAnchors.computeIfAbsent(ch, x -> new HashSet<>()).add(anchor);
    }
  }

  private int scorePlacement(
      final WordPlacement placement,
      final char[][] board,
      final Map<Character, Long> letterFrequency) {
    int intersections = 0;
    int centerDistance = 0;
    int letterConnectivity = 0;
    final int center = BOARD_SIZE / 2;
    for (int i = 0; i < placement.word().length(); i++) {
      final int row = placement.row() + (placement.horizontal() ? 0 : i);
      final int col = placement.col() + (placement.horizontal() ? i : 0);
      if (board[row][col] != 0) {
        intersections++;
      }
      centerDistance += Math.abs(row - center) + Math.abs(col - center);
      letterConnectivity += letterFrequency.getOrDefault(placement.word().charAt(i), 0L).intValue();
    }
    return intersections * 10_000 + letterConnectivity * 10 - centerDistance;
  }

  private int countIntersections(final WordPlacement placement, final char[][] board) {
    int intersections = 0;
    for (int i = 0; i < placement.word().length(); i++) {
      final int row = placement.row() + (placement.horizontal() ? 0 : i);
      final int col = placement.col() + (placement.horizontal() ? i : 0);
      if (board[row][col] != 0) {
        intersections++;
      }
    }
    return intersections;
  }

  private WordPlacement makeCenteredPlacement(final WordPair word, final boolean horizontal) {
    final int row = horizontal ? BOARD_SIZE / 2 : BOARD_SIZE / 2 - word.word().length() / 2;
    final int col = horizontal ? BOARD_SIZE / 2 - word.word().length() / 2 : BOARD_SIZE / 2;
    return new WordPlacement(row, col, horizontal, word.word(), word.translation());
  }

  private List<WordPair> rankWordsForStart(
      final List<WordPair> words,
      final Map<Character, Long> letterFrequency) {
    return words.stream()
        .sorted(
            Comparator.comparingInt(
                    (WordPair word) -> this.wordConnectivityScore(word, letterFrequency))
                .reversed())
        .toList();
  }

  private int wordConnectivityScore(final WordPair word,
                                    final Map<Character, Long> letterFrequency) {
    return word.word().chars()
        .map(ch -> letterFrequency.getOrDefault((char) ch, 0L).intValue())
        .sum();
  }

  private Map<Character, Long> makeLetterFrequency(final List<WordPair> words) {
    return words.stream()
        .map(WordPair::word)
        .map(String::chars)
        .flatMapToInt(Function.identity())
        .mapToObj(ch -> (char) ch)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
  }

  private boolean canPlace(final WordPlacement placement, final char[][] board) {
    final String word = placement.word();
    for (int i = 0; i < word.length(); i++) {
      final int row = placement.row() + (placement.horizontal() ? 0 : i);
      final int col = placement.col() + (placement.horizontal() ? i : 0);
      if (this.notInsideBoard(row, col)) {
        return false;
      }
      final char existing = board[row][col];
      if (existing != 0 && existing != word.charAt(i)) {
        return false;
      }
      if (existing == 0 && !this.neighborsAreClean(row, col, placement.horizontal(), board)) {
        return false;
      }
    }
    final int beforeRow = placement.row() - (placement.horizontal() ? 0 : 1);
    final int beforeCol = placement.col() - (placement.horizontal() ? 1 : 0);
    final int afterRow = placement.row() + (placement.horizontal() ? 0 : word.length());
    final int afterCol = placement.col() + (placement.horizontal() ? word.length() : 0);
    return this.isEmptyOrOutside(beforeRow, beforeCol, board) &&
        this.isEmptyOrOutside(afterRow, afterCol, board);
  }

  private boolean neighborsAreClean(
      final int row, final int col, final boolean horizontal, final char[][] board) {
    if (horizontal) {
      return this.isEmptyOrOutside(row - 1, col, board) &&
          this.isEmptyOrOutside(row + 1, col, board);
    }
    return this.isEmptyOrOutside(row, col - 1, board) && this.isEmptyOrOutside(row, col + 1, board);
  }

  private boolean isEmptyOrOutside(final int row, final int col, final char[][] board) {
    return this.notInsideBoard(row, col) || board[row][col] == 0;
  }

  private boolean notInsideBoard(final int row, final int col) {
    return row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE;
  }

  private void applyPlacement(final WordPlacement placement, final char[][] board) {
    for (int i = 0; i < placement.word().length(); i++) {
      final int row = placement.row() + (placement.horizontal() ? 0 : i);
      final int col = placement.col() + (placement.horizontal() ? i : 0);
      board[row][col] = placement.word().charAt(i);
    }
  }

  private void repaintBoard() {
    this.refreshTranslationLabel();
    this.modeLabel.setText(this.resolveModeLabelText());
    this.boardPanel.repaint();
  }

  private String resolveModeLabelText() {
    if (this.revealMode) {
      return "End game: green correct, red incorrect, yellow missing.";
    }
    if (this.gameFinished) {
      return "Completed! You can close the module!";
    }
    if (this.allCellsFilled) {
      return "There are errors. Wrong words are highlighted red.";
    }
    return "Use arrows to move and type letters to fill selected cell";
  }

  private boolean isFillable(final int row, final int col) {
    return this.solution[row][col] != 0;
  }

  private void refreshTranslationLabel() {
    final WordPlacement word = this.resolveWordForCell(this.selectedRow, this.selectedCol);
    if (word == null) {
      this.translationLabel.setText("No word selected");
      return;
    }
    this.translationLabel.setText(
        "Translation: " + word.translation().toUpperCase(Locale.ROOT));
  }

  private WordPlacement resolveWordForCell(final int row, final int col) {
    final List<WordPlacement> matched = this.placements.stream()
        .filter(placement -> this.cellBelongsToWord(row, col, placement))
        .toList();
    if (matched.isEmpty()) {
      return null;
    }
    final List<WordPlacement> startedHere = matched.stream()
        .filter(placement -> placement.row() == row && placement.col() == col)
        .toList();
    if (startedHere.size() == 1) {
      return startedHere.get(0);
    }
    if (startedHere.size() > 1) {
      final List<WordPlacement> preferredStarted = startedHere.stream()
          .filter(placement -> placement.horizontal() == this.preferredHorizontalDirection)
          .toList();
      if (!preferredStarted.isEmpty()) {
        return preferredStarted.get(0);
      }
      return startedHere.get(0);
    }
    final List<WordPlacement> directionMatched = matched.stream()
        .filter(placement -> placement.horizontal() == this.preferredHorizontalDirection)
        .toList();
    if (!directionMatched.isEmpty()) {
      return directionMatched.get(0);
    }
    return matched.get(0);
  }

  private boolean cellBelongsToWord(final int row, final int col, final WordPlacement placement) {
    if (placement.horizontal()) {
      return row == placement.row() && col >= placement.col() &&
          col < placement.col() + placement.word().length();
    }
    return col == placement.col() && row >= placement.row() &&
        row < placement.row() + placement.word().length();
  }

  private void finishGameAndReveal() {
    if (this.placements.isEmpty()) {
      return;
    }
    this.allCellsFilled = true;
    this.gameFinished = false;
    this.revealMode = true;
    this.repaintBoard();
    this.syncEndButtonEnabled();
  }

  private void syncEndButtonEnabled() {
    if (this.endGameButton != null) {
      this.endGameButton.setEnabled(
          !this.placements.isEmpty() && !this.gameFinished && !this.revealMode);
    }
  }

  private void openFromFile(final JList<DialogListEntry> list) {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Crossword JSON (*.json)", "json"));
    chooser.setAcceptAllFileFilterUsed(false);
    if (this.lastOpenDirectory != null) {
      chooser.setCurrentDirectory(this.lastOpenDirectory);
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
        this.lastOpenDirectory = parent;
      }
    } catch (final Exception ex) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          ex.getMessage(),
          "Can't open file",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void showCard(final String card) {
    final CardLayout layout = (CardLayout) this.rootPanel.getLayout();
    layout.show(this.rootPanel, card);
  }

  private record WordPair(String word, String translation) {
  }

  private record WordPlacement(int row, int col, boolean horizontal, String word,
                               String translation) {
  }

  private record PlacementCandidate(WordPair word, WordPlacement placement, int score) {
  }

  private record CandidateChoice(
      PlacementCandidate candidate, int optionCount, int intersections) {
  }

  private record SearchState(
      char[][] board,
      List<WordPlacement> placed,
      List<WordPair> remaining,
      Map<Character, Set<AnchorCell>> boardAnchors,
      int score) {
  }

  private record Bounds(int minRow, int maxRow, int minCol, int maxCol) {
    private int width() {
      return this.maxCol - this.minCol + 1;
    }

    private int height() {
      return this.maxRow - this.minRow + 1;
    }
  }

  private record CellRect(int x, int y, int width, int height) {
  }

  private record PaintViewport(int minRow, int minCol, int cellSize, int offsetX, int offsetY) {
  }

  private record GridEdge(int x1, int y1, int x2, int y2) {
    private static GridEdge of(final int x1, final int y1, final int x2, final int y2) {
      if (x1 < x2 || (x1 == x2 && y1 <= y2)) {
        return new GridEdge(x1, y1, x2, y2);
      }
      return new GridEdge(x2, y2, x1, y1);
    }
  }

  private record AnchorCell(int row, int col, boolean horizontal) {
  }

  private record PlacementKey(int row, int col, boolean horizontal) {
  }




}
