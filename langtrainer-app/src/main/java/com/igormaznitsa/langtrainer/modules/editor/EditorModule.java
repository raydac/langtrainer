package com.igormaznitsa.langtrainer.modules.editor;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.DialogJsonSerializer;
import com.igormaznitsa.langtrainer.engine.DialogLine;
import com.igormaznitsa.langtrainer.engine.ImageResourceLoader;
import com.igormaznitsa.langtrainer.engine.InputEquivalenceRow;
import com.igormaznitsa.langtrainer.engine.LangResourceJson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

public final class EditorModule extends AbstractLangTrainerModule {

  private static final Color PANEL_BG = new Color(236, 242, 249);
  private static final Color ACCENT = new Color(25, 45, 85);
  /**
   * Matches the visual weight of other modules’ work areas (e.g. dialog input zones).
   */
  private static final float CONTENT_FONT_PT = 19f;
  private static volatile File workDirectory;
  private final Font contentFont;

  private final JPanel rootPanel = new JPanel(new BorderLayout(0, 10));
  private final JTextField fieldTitle = new JTextField();
  private final JTextArea fieldDescription = makeGrowingTextArea();
  private final JTextField fieldLangA = new JTextField();
  private final JTextField fieldLangB = new JTextField();
  private final DefaultTableModel linesModel =
      new DefaultTableModel(new Object[] {"Id", "A", "B"}, 0) {
        @Override
        public Class<?> getColumnClass(final int columnIndex) {
          return columnIndex == 0 ? Integer.class : String.class;
        }

        @Override
        public boolean isCellEditable(final int row, final int column) {
          return column != 0;
        }
      };
  private final JTable linesTable = new JTable(this.linesModel);
  /**
   * One row per {@link InputEquivalenceRow}; Key/Value cells are comma-separated token lists (e.g. {@code e,E}).
   */
  private final DefaultTableModel equivPairModel =
      new DefaultTableModel(new Object[] {"Id", "Key", "Value"}, 0) {
        @Override
        public Class<?> getColumnClass(final int columnIndex) {
          return columnIndex == 0 ? Integer.class : String.class;
        }

        @Override
        public boolean isCellEditable(final int row, final int column) {
          return column != 0;
        }
      };
  private final JTable equivPairTable = new JTable(this.equivPairModel);

  private Path currentFilePath;

  public EditorModule() {
    this.contentFont = this.rootPanel.getFont().deriveFont(Font.PLAIN, CONTENT_FONT_PT);
    this.fieldDescription.setRows(2);
    this.fieldDescription.setColumns(40);
    this.fieldDescription.setLineWrap(true);
    this.fieldDescription.setWrapStyleWord(true);
    styleTextFields();
    configureLinesTable();
    configureEquivPairTable();
    buildUi();
    newDocument();
  }

  /**
   * Text area that expands with its {@link JScrollPane} viewport when the window is resized (default
   * {@link JTextArea} keeps a fixed preferred height and ignores extra vertical space).
   */
  private static JTextArea makeGrowingTextArea() {
    final JTextArea area =
        new JTextArea() {
          @Override
          public boolean getScrollableTracksViewportWidth() {
            return true;
          }

          @Override
          public boolean getScrollableTracksViewportHeight() {
            return true;
          }
        };
    area.setMinimumSize(new Dimension(0, 0));
    return area;
  }

  private static void shrinkWrap(final JComponent c) {
    c.setMinimumSize(new Dimension(0, 0));
  }

  /**
   * Scrolls the table's viewport so {@code row} is in view (after move up/down, etc.).
   */
  private static void ensureTableRowVisible(final JTable table, final int row) {
    if (row < 0 || row >= table.getRowCount()) {
      return;
    }
    SwingUtilities.invokeLater(
        () -> {
          Rectangle visible = table.getCellRect(row, 0, true);
          final int lastCol = table.getColumnCount() - 1;
          if (lastCol > 0) {
            visible = visible.union(table.getCellRect(row, lastCol, true));
          }
          table.scrollRectToVisible(visible);
        });
  }

  private static void stylePrimary(final JButton button, final Color bg) {
    button.setFont(button.getFont().deriveFont(Font.BOLD, 15f));
    button.setForeground(Color.WHITE);
    button.setBackground(bg);
    button.setOpaque(true);
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  private static JLabel label(final String text) {
    final JLabel l = new JLabel(text);
    l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
    l.setForeground(ACCENT);
    return l;
  }

  private static void rememberWorkDir(final File file) {
    final File parent = file.getParentFile();
    if (parent != null) {
      workDirectory = parent;
    }
  }

  private static String cellString(final Object cell) {
    if (cell == null) {
      return "";
    }
    return String.valueOf(cell).strip();
  }

  private static String joinCommaSeparated(final List<String> parts) {
    return String.join(",", parts);
  }

  private static List<String> splitCommaSeparated(final String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    final List<String> out = new ArrayList<>();
    for (final String p : raw.split(",")) {
      final String s = p.strip();
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return List.copyOf(out);
  }

  /**
   * Key/Value cells: comma-separated tokens; spaces inside a token are allowed (e.g. {@code A,B, C, D}).
   * Empty segments from extra commas are invalid ({@code A,,B}, leading/trailing comma).
   */
  private static Optional<String> validateEquivTokenListSyntax(final String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.of("must not be empty or blank");
    }
    for (final String part : raw.split(",", -1)) {
      if (part.strip().isEmpty()) {
        return Optional.of(
            "invalid comma-separated list (empty segment); examples: \"A\", \"A,B\", \"a, b, c\"");
      }
    }
    return Optional.empty();
  }

  private static void requireValidEquivTokens(final int rowId, final String column,
                                              final String raw) {
    validateEquivTokenListSyntax(raw)
        .ifPresent(
            msg -> {
              throw new IllegalStateException(
                  "Input equivalence rules: row Id " + rowId + " (" + column + "): " + msg);
            });
  }

  private void validateLinesForSave() {
    for (int i = 0; i < this.linesModel.getRowCount(); i++) {
      final int id = i + 1;
      final String a = cellString(this.linesModel.getValueAt(i, 1));
      final String b = cellString(this.linesModel.getValueAt(i, 2));
      if (a.isEmpty()) {
        throw new IllegalStateException(
            "Lines: row Id " + id + ": column A must not be empty or blank.");
      }
      if (b.isEmpty()) {
        throw new IllegalStateException(
            "Lines: row Id " + id + ": column B must not be empty or blank.");
      }
    }
  }

  private void validateEquivPairsForSave() {
    for (int i = 0; i < this.equivPairModel.getRowCount(); i++) {
      final int id = i + 1;
      final String keyStr = cellString(this.equivPairModel.getValueAt(i, 1));
      final String valStr = cellString(this.equivPairModel.getValueAt(i, 2));
      if (keyStr.isEmpty() && valStr.isEmpty()) {
        continue;
      }
      if (keyStr.isEmpty() || valStr.isEmpty()) {
        throw new IllegalStateException(
            "Input equivalence rules: row Id "
                + id
                + ": Key and Value must both be filled, or both left empty.");
      }
      requireValidEquivTokens(id, "Key", keyStr);
      requireValidEquivTokens(id, "Value", valStr);
    }
  }

  private void stopEquivTableEditing() {
    if (this.equivPairTable.isEditing()) {
      final var editor = this.equivPairTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
  }

  private void stopLinesTableEditing() {
    if (this.linesTable.isEditing()) {
      final var editor = this.linesTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
  }

  private List<InputEquivalenceRow> inputEquivalenceRowsFromTable() {
    final List<InputEquivalenceRow> out = new ArrayList<>();
    for (int i = 0; i < this.equivPairModel.getRowCount(); i++) {
      final String keyStr = cellString(this.equivPairModel.getValueAt(i, 1));
      final String valStr = cellString(this.equivPairModel.getValueAt(i, 2));
      if (keyStr.isEmpty() && valStr.isEmpty()) {
        continue;
      }
      out.add(new InputEquivalenceRow(splitCommaSeparated(keyStr), splitCommaSeparated(valStr)));
    }
    return out;
  }

  private void fillEquivTableFromRules(final List<InputEquivalenceRow> rules) {
    this.equivPairModel.setRowCount(0);
    if (rules.isEmpty()) {
      this.equivPairModel.addRow(new Object[] {0, "", ""});
    } else {
      for (final InputEquivalenceRow row : rules) {
        this.equivPairModel.addRow(
            new Object[] {0, joinCommaSeparated(row.key()), joinCommaSeparated(row.value())});
      }
    }
    refreshEquivPairIds();
    this.equivPairTable.setEnabled(true);
    this.equivPairTable.getTableHeader().setEnabled(true);
  }

  private void configureEquivPairTable() {
    final Font f = this.contentFont;
    this.equivPairTable.setFont(f);
    this.equivPairTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.equivPairTable.setRowHeight(Math.max(32, (int) (f.getSize2D() + 14f)));
    final JTableHeader header = this.equivPairTable.getTableHeader();
    header.setFont(f.deriveFont(Font.BOLD));
    header.setReorderingAllowed(false);
    ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);

    final DefaultTableCellRenderer strRenderer = new DefaultTableCellRenderer();
    strRenderer.setFont(f);
    this.equivPairTable.setDefaultRenderer(String.class, strRenderer);
    final DefaultTableCellRenderer idRenderer = new DefaultTableCellRenderer();
    idRenderer.setHorizontalAlignment(SwingConstants.CENTER);
    idRenderer.setFont(f);
    this.equivPairTable.getColumnModel().getColumn(0).setCellRenderer(idRenderer);
    final JTextField cellField = new JTextField();
    cellField.setFont(f);
    cellField.setMargin(new Insets(4, 8, 4, 8));
    this.equivPairTable.setDefaultEditor(String.class, new DefaultCellEditor(cellField));
    this.equivPairTable.setFillsViewportHeight(true);
    this.equivPairTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    this.equivPairTable.getColumnModel().getColumn(0).setPreferredWidth(44);
    this.equivPairTable.getColumnModel().getColumn(1).setPreferredWidth(200);
    this.equivPairTable.getColumnModel().getColumn(2).setPreferredWidth(200);
  }

  private void refreshEquivPairIds() {
    for (int i = 0; i < this.equivPairModel.getRowCount(); i++) {
      this.equivPairModel.setValueAt(i + 1, i, 0);
    }
  }

  private void styleTextFields() {
    final Font f = this.contentFont;
    this.fieldTitle.setFont(f);
    this.fieldDescription.setFont(f);
    this.fieldLangA.setFont(f);
    this.fieldLangB.setFont(f);
    this.fieldTitle.setMargin(new Insets(6, 10, 6, 10));
    this.fieldLangA.setMargin(new Insets(6, 10, 6, 10));
    this.fieldLangB.setMargin(new Insets(6, 10, 6, 10));
    this.fieldDescription.setMargin(new Insets(8, 10, 8, 10));
  }

  private void configureLinesTable() {
    final Font f = this.contentFont;
    this.linesTable.setFont(f);
    this.linesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.linesTable.setRowHeight(Math.max(36, (int) (f.getSize2D() + 18f)));
    final JTableHeader header = this.linesTable.getTableHeader();
    header.setFont(f.deriveFont(Font.BOLD));
    header.setReorderingAllowed(false);
    ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);

    final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
    renderer.setFont(f);
    this.linesTable.setDefaultRenderer(Object.class, renderer);
    this.linesTable.setDefaultRenderer(String.class, renderer);
    final DefaultTableCellRenderer idRenderer = new DefaultTableCellRenderer();
    idRenderer.setHorizontalAlignment(SwingConstants.CENTER);
    idRenderer.setFont(f);
    this.linesTable.getColumnModel().getColumn(0).setCellRenderer(idRenderer);
    final JTextField cellField = new JTextField();
    cellField.setFont(f);
    cellField.setMargin(new Insets(4, 8, 4, 8));
    this.linesTable.setDefaultEditor(String.class, new DefaultCellEditor(cellField));
    this.linesTable.setFillsViewportHeight(true);
    this.linesTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    this.linesTable.getColumnModel().getColumn(0).setPreferredWidth(44);
    this.linesTable.getColumnModel().getColumn(1).setPreferredWidth(280);
    this.linesTable.getColumnModel().getColumn(2).setPreferredWidth(280);
  }

  private void refreshLineIds() {
    for (int i = 0; i < this.linesModel.getRowCount(); i++) {
      this.linesModel.setValueAt(i + 1, i, 0);
    }
  }

  private void buildUi() {
    this.rootPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
    this.rootPanel.setBackground(PANEL_BG);

    final JLabel heading = new JLabel("Editor — dialog JSON", SwingConstants.CENTER);
    heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26f));
    heading.setForeground(ACCENT);
    heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

    final JPanel generalBlock = new JPanel(new GridBagLayout());
    generalBlock.setOpaque(false);
    shrinkWrap(generalBlock);
    generalBlock.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(new Color(90, 120, 160), 2, true),
        "General",
        0,
        0,
        this.fieldTitle.getFont().deriveFont(Font.BOLD, 16f),
        ACCENT));
    generalBlock.setPreferredSize(new Dimension(300, 0));

    final JScrollPane descScroll = new JScrollPane(this.fieldDescription);
    shrinkWrap(descScroll);
    descScroll.setPreferredSize(new Dimension(200, 96));

    final Insets cellPad = new Insets(4, 4, 4, 4);
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = cellPad;

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.gridheight = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    generalBlock.add(label("Title (menuName)"), gbc);

    gbc.gridy = 1;
    gbc.weightx = 100.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(this.fieldTitle, gbc);

    gbc.gridy = 2;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    generalBlock.add(label("Description"), gbc);

    gbc.gridy = 3;
    gbc.weightx = 1.0;
    gbc.weighty = 100.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.CENTER;
    generalBlock.add(descScroll, gbc);

    gbc.gridy = 4;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    generalBlock.add(label("Language A"), gbc);

    gbc.gridy = 5;
    gbc.weightx = 100.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(this.fieldLangA, gbc);

    gbc.gridy = 6;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(label("Language B"), gbc);

    gbc.gridy = 7;
    gbc.weightx = 1.0;
    gbc.weighty = 100.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(this.fieldLangB, gbc);

    gbc.gridy = 8;
    gbc.weighty = 1000.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    generalBlock.add(Box.createVerticalGlue(), gbc);

    final JPanel linesWrap = new JPanel(new BorderLayout(8, 8));
    linesWrap.setOpaque(false);
    shrinkWrap(linesWrap);
    linesWrap.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(new Color(100, 130, 170), 2, true),
        "Lines (phrases)",
        0,
        0,
        this.fieldTitle.getFont().deriveFont(Font.BOLD, 16f),
        ACCENT));
    final JScrollPane linesScroll = new JScrollPane(this.linesTable);
    shrinkWrap(linesScroll);
    linesWrap.add(linesScroll, BorderLayout.CENTER);
    linesWrap.add(lineButtons(), BorderLayout.SOUTH);

    final JLabel pairHint =
        new JLabel(
            "Each row is one equivalence rule. Key and Value are comma-separated tokens (e.g. e,E and e,E,ё,Ё).",
            SwingConstants.LEADING);
    pairHint.setFont(pairHint.getFont().deriveFont(Font.PLAIN, 14f));
    pairHint.setForeground(new Color(55, 71, 79));
    pairHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
    final JScrollPane pairScroll = new JScrollPane(this.equivPairTable);
    shrinkWrap(pairScroll);
    pairScroll.getViewport().setBackground(Color.WHITE);
    final JPanel equivTableWrap = new JPanel(new BorderLayout(0, 6));
    equivTableWrap.setOpaque(true);
    equivTableWrap.setBackground(Color.WHITE);
    equivTableWrap.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
    shrinkWrap(equivTableWrap);
    equivTableWrap.add(pairHint, BorderLayout.NORTH);
    equivTableWrap.add(pairScroll, BorderLayout.CENTER);

    final JPanel equivBlock = new JPanel(new BorderLayout());
    equivBlock.setOpaque(false);
    shrinkWrap(equivBlock);
    equivBlock.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(new Color(120, 100, 160), 2, true),
        "Input equivalence rules",
        0,
        0,
        this.fieldTitle.getFont().deriveFont(Font.BOLD, 16f),
        new Color(74, 20, 140)));
    equivBlock.add(equivTableWrap, BorderLayout.CENTER);
    equivBlock.add(equivButtons(), BorderLayout.SOUTH);

    final JSplitPane linesEquivSplit =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, linesWrap, equivBlock);
    linesEquivSplit.setResizeWeight(0.5);
    linesEquivSplit.setContinuousLayout(true);
    linesEquivSplit.setOneTouchExpandable(true);
    linesEquivSplit.setBorder(BorderFactory.createEmptyBorder());
    linesEquivSplit.setOpaque(false);
    shrinkWrap(linesEquivSplit);

    final JSplitPane mainSplit =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, generalBlock, linesEquivSplit);
    mainSplit.setResizeWeight(0.0);
    mainSplit.setContinuousLayout(true);
    mainSplit.setOneTouchExpandable(true);
    mainSplit.setBorder(BorderFactory.createEmptyBorder());
    mainSplit.setOpaque(false);

    final JPanel center = new JPanel(new BorderLayout());
    center.setOpaque(false);
    shrinkWrap(center);
    center.add(heading, BorderLayout.NORTH);
    center.add(mainSplit, BorderLayout.CENTER);

    final JPanel newDocRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    newDocRow.setOpaque(false);
    final JButton newDoc = new JButton("New document");
    stylePrimary(newDoc, new Color(93, 64, 55));
    newDoc.addActionListener(e -> {
      if (confirmLoseChanges()) {
        newDocument();
      }
    });
    newDocRow.add(newDoc);

    this.rootPanel.add(newDocRow, BorderLayout.NORTH);
    this.rootPanel.add(center, BorderLayout.CENTER);
  }

  private boolean confirmLoseChanges() {
    return JOptionPane.showConfirmDialog(
        this.rootPanel,
        "Discard current content and start a new document?",
        "New document",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE)
        == JOptionPane.OK_OPTION;
  }

  private JPanel lineButtons() {
    final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    p.setOpaque(false);
    final JButton add = new JButton("Add line");
    final JButton remove = new JButton("Remove");
    final JButton up = new JButton("Move up");
    final JButton down = new JButton("Move down");
    stylePrimary(add, new Color(46, 125, 50));
    stylePrimary(remove, new Color(198, 40, 40));
    stylePrimary(up, new Color(25, 118, 210));
    stylePrimary(down, new Color(25, 118, 210));
    add.addActionListener(e -> {
      this.linesModel.addRow(new Object[] {0, "", ""});
      refreshLineIds();
      final int last = this.linesModel.getRowCount() - 1;
      this.linesTable.setRowSelectionInterval(last, last);
    });
    remove.addActionListener(e -> removeSelectedLine());
    up.addActionListener(e -> moveLine(-1));
    down.addActionListener(e -> moveLine(1));
    p.add(add);
    p.add(remove);
    p.add(up);
    p.add(down);
    return p;
  }

  private JPanel equivButtons() {
    final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    p.setOpaque(false);
    final JButton add = new JButton("Add rule");
    final JButton remove = new JButton("Remove rule");
    final JButton up = new JButton("Move rule up");
    final JButton down = new JButton("Move rule down");
    stylePrimary(add, new Color(106, 27, 154));
    stylePrimary(remove, new Color(198, 40, 40));
    stylePrimary(up, new Color(123, 31, 162));
    stylePrimary(down, new Color(123, 31, 162));
    add.addActionListener(e -> {
      this.equivPairModel.addRow(new Object[] {0, "", ""});
      refreshEquivPairIds();
      final int last = this.equivPairModel.getRowCount() - 1;
      this.equivPairTable.setRowSelectionInterval(last, last);
    });
    remove.addActionListener(e -> removeSelectedEquiv());
    up.addActionListener(e -> moveEquiv(-1));
    down.addActionListener(e -> moveEquiv(1));
    p.add(add);
    p.add(remove);
    p.add(up);
    p.add(down);
    return p;
  }

  private void removeSelectedLine() {
    final int r = this.linesTable.getSelectedRow();
    if (r < 0) {
      return;
    }
    if (this.linesModel.getRowCount() == 1) {
      stopLinesTableEditing();
      this.linesModel.setValueAt("", r, 1);
      this.linesModel.setValueAt("", r, 2);
      return;
    }
    stopLinesTableEditing();
    this.linesModel.removeRow(r);
    refreshLineIds();
  }

  private void moveLine(final int delta) {
    final int r = this.linesTable.getSelectedRow();
    final int n = this.linesModel.getRowCount();
    final int to = r + delta;
    if (r < 0 || to < 0 || to >= n) {
      return;
    }
    for (int c = 1; c <= 2; c++) {
      final Object a = this.linesModel.getValueAt(r, c);
      final Object b = this.linesModel.getValueAt(to, c);
      this.linesModel.setValueAt(b, r, c);
      this.linesModel.setValueAt(a, to, c);
    }
    refreshLineIds();
    this.linesTable.setRowSelectionInterval(to, to);
    ensureTableRowVisible(this.linesTable, to);
  }

  private void removeSelectedEquiv() {
    if (this.equivPairModel.getRowCount() == 0) {
      return;
    }
    final int idx = this.equivPairTable.getSelectedRow();
    if (idx < 0) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "Select a rule row in the table.",
          "No rule selected",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    this.equivPairModel.removeRow(idx);
    if (this.equivPairModel.getRowCount() == 0) {
      this.equivPairModel.addRow(new Object[] {0, "", ""});
    }
    refreshEquivPairIds();
    final int n = this.equivPairModel.getRowCount();
    if (n > 0) {
      final int sel = Math.min(idx, n - 1);
      this.equivPairTable.setRowSelectionInterval(sel, sel);
    }
  }

  private void moveEquiv(final int delta) {
    if (this.equivPairModel.getRowCount() == 0) {
      return;
    }
    final int idx = this.equivPairTable.getSelectedRow();
    if (idx < 0) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "Select a rule row in the table.",
          "No rule selected",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    final int to = idx + delta;
    if (to < 0 || to >= this.equivPairModel.getRowCount()) {
      return;
    }
    for (int c = 1; c <= 2; c++) {
      final Object a = this.equivPairModel.getValueAt(idx, c);
      final Object b = this.equivPairModel.getValueAt(to, c);
      this.equivPairModel.setValueAt(b, idx, c);
      this.equivPairModel.setValueAt(a, to, c);
    }
    refreshEquivPairIds();
    this.equivPairTable.setRowSelectionInterval(to, to);
    ensureTableRowVisible(this.equivPairTable, to);
  }

  private void newDocument() {
    this.currentFilePath = null;
    this.fieldTitle.setText("");
    this.fieldDescription.setText("");
    this.fieldLangA.setText("");
    this.fieldLangB.setText("");
    this.linesModel.setRowCount(0);
    this.linesModel.addRow(new Object[] {0, "", ""});
    refreshLineIds();
    clearEquivRules();
    this.linesTable.setRowSelectionInterval(0, 0);
  }

  private void clearEquivRules() {
    stopEquivTableEditing();
    fillEquivTableFromRules(List.of());
    this.equivPairTable.setEnabled(true);
  }

  private void applyDefinition(final DialogDefinition def) {
    this.fieldTitle.setText(def.menuName());
    this.fieldDescription.setText(def.description());
    this.fieldLangA.setText(def.langA());
    this.fieldLangB.setText(def.langB());
    this.linesModel.setRowCount(0);
    for (final DialogLine line : def.lines()) {
      this.linesModel.addRow(new Object[] {0, line.a(), line.b()});
    }
    refreshLineIds();
    stopEquivTableEditing();
    fillEquivTableFromRules(def.inputEqu());
    if (this.linesModel.getRowCount() > 0) {
      this.linesTable.setRowSelectionInterval(0, 0);
    }
  }

  private DialogDefinition readDefinitionFromUi() {
    stopLinesTableEditing();
    stopEquivTableEditing();
    if (this.linesModel.getRowCount() == 0) {
      throw new IllegalStateException("Add at least one dialog line.");
    }
    validateLinesForSave();
    validateEquivPairsForSave();
    final List<DialogLine> lines = new ArrayList<>();
    for (int i = 0; i < this.linesModel.getRowCount(); i++) {
      final String a = String.valueOf(this.linesModel.getValueAt(i, 1) == null
          ? ""
          : this.linesModel.getValueAt(i, 1));
      final String b = String.valueOf(this.linesModel.getValueAt(i, 2) == null
          ? ""
          : this.linesModel.getValueAt(i, 2));
      lines.add(new DialogLine(a, b));
    }
    return new DialogDefinition(
        this.fieldTitle.getText().strip(),
        this.fieldDescription.getText().strip(),
        this.fieldLangA.getText().strip(),
        this.fieldLangB.getText().strip(),
        lines,
        inputEquivalenceRowsFromTable());
  }

  private void loadFromUser() {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
    chooser.setAcceptAllFileFilterUsed(false);
    if (workDirectory != null) {
      chooser.setCurrentDirectory(workDirectory);
    }
    if (chooser.showOpenDialog(this.rootPanel) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    final File f = chooser.getSelectedFile();
    if (f == null) {
      return;
    }
    rememberWorkDir(f);
    try {
      final String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
      final DialogDefinition def = LangResourceJson.parse(text);
      this.currentFilePath = f.toPath();
      applyDefinition(def);
    } catch (final Exception ex) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          ex.getMessage() == null ? String.valueOf(ex) : ex.getMessage(),
          "Can't load file",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void saveToUser() {
    final DialogDefinition def;
    try {
      def = readDefinitionFromUi();
      LangResourceJson.parse(DialogJsonSerializer.toPrettyJson(def));
    } catch (final Exception ex) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          ex.getMessage() == null ? String.valueOf(ex) : ex.getMessage(),
          "Invalid content",
          JOptionPane.ERROR_MESSAGE);
      return;
    }
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
    chooser.setAcceptAllFileFilterUsed(false);
    if (workDirectory != null) {
      chooser.setCurrentDirectory(workDirectory);
    }
    if (this.currentFilePath != null) {
      chooser.setSelectedFile(this.currentFilePath.toFile());
    }
    if (chooser.showSaveDialog(this.rootPanel) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File out = chooser.getSelectedFile();
    if (out == null) {
      return;
    }
    if (!out.getName().toLowerCase().endsWith(".json")) {
      out = new File(out.getParentFile(), out.getName() + ".json");
    }
    rememberWorkDir(out);
    try {
      Files.writeString(out.toPath(), DialogJsonSerializer.toPrettyJson(def),
          StandardCharsets.UTF_8);
      this.currentFilePath = out.toPath();
    } catch (final IOException ex) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          ex.getMessage() == null ? String.valueOf(ex) : ex.getMessage(),
          "Can't save file",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  @Override
  public void populateMainToolbar(final JPanel eastToolbar) {
    final JButton load =
        new JButton(ImageResourceLoader.loadIcon("/editor/images/editor-load.svg", 24, 24));
    load.setToolTipText("Load JSON file…");
    load.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    load.setFocusPainted(false);
    load.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    load.addActionListener(e -> loadFromUser());
    final JButton save =
        new JButton(ImageResourceLoader.loadIcon("/editor/images/editor-save.svg", 24, 24));
    save.setToolTipText("Save JSON file…");
    save.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    save.setFocusPainted(false);
    save.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    save.addActionListener(e -> saveToUser());
    eastToolbar.add(load);
    eastToolbar.add(save);
  }

  @Override
  public String getName() {
    return "Editor";
  }

  @Override
  public String getDescription() {
    return "Create and edit dialog JSON (lines and input equivalence)";
  }

  @Override
  public Icon getImage() {
    return ImageResourceLoader.loadIcon("/editor/images/module-editor.svg", 128, 128);
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
  public void onActivation() {
    SwingUtilities.invokeLater(() -> this.fieldTitle.requestFocusInWindow());
  }
}
