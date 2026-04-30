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
import com.igormaznitsa.langtrainer.engine.LangResourceJson;
import com.igormaznitsa.langtrainer.engine.LangTrainerResourceAccess;
import com.igormaznitsa.langtrainer.engine.ResourceListSelectPanel;
import com.igormaznitsa.langtrainer.ui.LangTrainerFonts;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import javax.swing.filechooser.FileNameExtensionFilter;

public final class CrosswordModule extends AbstractLangTrainerModule {

  private static final String CARD_SELECT = "select";
  private static final String CARD_WORK = "work";
  private static final int BOARD_SIZE = 25;
  private static final int MAX_START_WORDS_TO_TRY = 10;
  private static final int MIN_WORDS_IN_CROSSWORD = 3;
  private static final double CROSSWORD_FILL_RATIO = 0.92d;
  private static final int TRANSLATION_TO_BOARD_GAP_PX = 6;
  private static final Color EMPTY_CELL_BG = new Color(225, 225, 225);
  private static final Color FILLED_CELL_BG = Color.WHITE;
  private static final Color SELECTED_CELL_BG = new Color(187, 222, 251);
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
    this.finishGameAndReveal();
  }

  @Override
  public void onCharClick(final char symbol) {
    if (this.gameFinished || this.revealMode ||
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
    } finally {
      g2.dispose();
    }
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
    final int cellSize = Math.max(1, Math.min(cellByWidth, cellByHeight));
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
    if (this.gameFinished || this.revealMode ||
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
    if (this.gameFinished || this.revealMode) {
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
    if (!this.insideBoard(row, col) || !this.isFillable(row, col)) {
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
    if (this.gameFinished || this.revealMode) {
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
    this.userInput[this.selectedRow][this.selectedCol] = ch;
    this.moveCursorToNextCellInWord();
    this.checkBoardState();
    this.repaintBoard();
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
      this.collectWordCells(placement).forEach(this.wrongCells::add);
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
    final List<WordPair> words = this.extractSingleWordPairs(definition);
    if (words.size() < MIN_WORDS_IN_CROSSWORD) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "Not enough single-word pairs for crossword.",
          "Crossword",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    final List<WordPlacement> generated = this.buildCrossword(words);
    if (generated.size() < MIN_WORDS_IN_CROSSWORD) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "Can't build crossword from selected words.",
          "Crossword",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    this.resetBoardState();
    this.placements = generated;
    this.placeWordsOnBoard(generated);
    this.selectFirstCell();
    this.preferredHorizontalDirection = true;
    this.refreshTranslationLabel();
    this.modeLabel.setText("Use arrows to move and type letters to fill selected cell");
    this.showCard(CARD_WORK);
    this.repaintBoard();
    this.syncEndButtonEnabled();
    SwingUtilities.invokeLater(() -> this.boardPanel.requestFocusInWindow());
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

  private void selectFirstCell() {
    final Point first = this.fillableCells.stream().findFirst().orElse(new Point(0, 0));
    this.selectedRow = first.x;
    this.selectedCol = first.y;
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
    final Map<Character, Long> letterFrequency = this.makeLetterFrequency(words);
    final List<WordPair> rankedWords = this.rankWordsForStart(words, letterFrequency);
    final int startsToTry = Math.min(MAX_START_WORDS_TO_TRY, rankedWords.size());
    List<WordPlacement> best = List.of();
    for (int i = 0; i < startsToTry; i++) {
      final WordPair startWord = rankedWords.get(i);
      final List<WordPlacement> horizontal =
          this.tryBuildFromStart(words, startWord, true, letterFrequency);
      if (horizontal.size() > best.size()) {
        best = horizontal;
      }
      final List<WordPlacement> vertical =
          this.tryBuildFromStart(words, startWord, false, letterFrequency);
      if (vertical.size() > best.size()) {
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
      final Map<Character, Long> letterFrequency) {
    final char[][] board = new char[BOARD_SIZE][BOARD_SIZE];
    final List<WordPlacement> placed = new ArrayList<>();
    final WordPlacement startPlacement = this.makeCenteredPlacement(startWord, horizontal);
    if (!this.canPlace(startPlacement, board)) {
      return List.of();
    }
    this.applyPlacement(startPlacement, board);
    placed.add(startPlacement);

    final List<WordPair> remaining = new ArrayList<>(allWords);
    remaining.remove(startWord);
    while (!remaining.isEmpty()) {
      final PlacementCandidate best =
          this.findBestNextPlacement(remaining, placed, board, letterFrequency);
      if (best == null) {
        break;
      }
      this.applyPlacement(best.placement(), board);
      placed.add(best.placement());
      remaining.remove(best.word());
    }
    return placed;
  }

  private PlacementCandidate findBestNextPlacement(
      final List<WordPair> remaining,
      final List<WordPlacement> placed,
      final char[][] board,
      final Map<Character, Long> letterFrequency) {
    PlacementCandidate best = null;
    for (final WordPair word : remaining) {
      final List<WordPlacement> options = this.findAllPlacements(word, placed, board);
      for (final WordPlacement option : options) {
        final int score = this.scorePlacement(option, board, letterFrequency);
        if (best == null || score > best.score()) {
          best = new PlacementCandidate(word, option, score);
        }
      }
    }
    return best;
  }

  private List<WordPlacement> findAllPlacements(
      final WordPair candidate,
      final List<WordPlacement> alreadyPlaced,
      final char[][] board) {
    final List<WordPlacement> options = new ArrayList<>();
    for (final WordPlacement existing : alreadyPlaced) {
      for (int i = 0; i < existing.word().length(); i++) {
        final char anchor = existing.word().charAt(i);
        for (int j = 0; j < candidate.word().length(); j++) {
          if (candidate.word().charAt(j) != anchor) {
            continue;
          }
          final int row =
              existing.row() + (existing.horizontal() ? 0 : i) - (existing.horizontal() ? j : 0);
          final int col =
              existing.col() + (existing.horizontal() ? i : 0) - (existing.horizontal() ? 0 : j);
          final WordPlacement placement = new WordPlacement(
              row,
              col,
              !existing.horizontal(),
              candidate.word(),
              candidate.translation());
          if (this.canPlace(placement, board)) {
            options.add(placement);
          }
        }
      }
    }
    return options;
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
      if (!this.insideBoard(row, col)) {
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
    return !this.insideBoard(row, col) || board[row][col] == 0;
  }

  private boolean insideBoard(final int row, final int col) {
    return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
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
      return "Completed! Press End game or close module.";
    }
    if (this.allCellsFilled) {
      return "There are errors. Wrong words are highlighted red.";
    }
    return "Use arrows to move and type letters to fill selected cell";
  }

  private boolean hasFillableNeighbor(final int row, final int col) {
    return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE &&
        this.isFillable(row, col);
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




}
