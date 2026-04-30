package com.igormaznitsa.langtrainer.modules.editor;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.DialogLine;
import com.igormaznitsa.langtrainer.engine.ImageResourceLoader;
import com.igormaznitsa.langtrainer.engine.InputEquivalenceRow;
import com.igormaznitsa.langtrainer.engine.LangResourceJson;
import com.igormaznitsa.langtrainer.ui.InputEquivalenceEnglishPresets;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;

public final class EditorModule extends AbstractLangTrainerModule {

  private static final float EDITOR_FONT_MIN_PT = 9f;
  private static final float EDITOR_FONT_MAX_PT = 36f;
  private static final float EDITOR_FONT_STEP_PT = 1f;
  private static final float PRIMARY_BUTTON_EXTRA_PT = 2f;
  /**
   * {@link JTable} default row height does not follow font size; editors are clipped without this padding.
   */
  private static final int TABLE_ROW_PAD_PX = 8;
  private static final int TABLE_ROW_MIN_PX = 22;

  private static volatile File workDirectory;

  private final JPanel rootPanel = new JPanel(new BorderLayout(0, 10));
  private final List<JComponent> toolbarFontTargets = new ArrayList<>();
  private final JTextField linesTableCellField = new JTextField();
  private final JTextField equivTableCellField = new JTextField();
  private float editorFontSizePoints = -1f;
  private JButton newDocumentButton;
  private final JTextField fieldTitle = new JTextField();
  private final JTextArea fieldDescription = EditorModule.makeGrowingTextArea();
  private final JTextField fieldLangA = new JTextField();
  private final JTextField fieldLangB = new JTextField();
  private final JCheckBox checkShuffled = new JCheckBox("Shuffled");

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
    this.fieldDescription.setRows(2);
    this.fieldDescription.setColumns(40);
    this.fieldDescription.setLineWrap(true);
    this.fieldDescription.setWrapStyleWord(true);
    this.configureLinesTable();
    this.configureEquivPairTable();
    this.checkShuffled.setToolTipText(
        "JSON root field \"shuffled\": when true, a module starts with line order randomization on if allowed.");
    this.buildUi();
    this.newDocument();
    this.applyEditorModuleFonts();
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

  private static void stylePrimary(final JButton button, final Color bg, final float sizePt) {
    button.setFont(button.getFont().deriveFont(Font.BOLD, sizePt));
    button.setForeground(Color.WHITE);
    button.setBackground(bg);
    button.setOpaque(true);
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  private static JLabel label(final String text) {
    return new JLabel(text);
  }

  private static void rememberWorkDir(final File file) {
    final File parent = file.getParentFile();
    if (parent != null) {
      EditorModule.workDirectory = parent;
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
      return of("must not be empty or blank");
    }
    for (final String part : raw.split(",", -1)) {
      if (part.strip().isEmpty()) {
        return of(
            "invalid comma-separated list (empty segment); examples: \"A\", \"A,B\", \"a, b, c\"");
      }
    }
    return empty();
  }

  private static void requireValidEquivTokens(final int rowId, final String column,
                                              final String raw) {
    EditorModule.validateEquivTokenListSyntax(raw)
        .ifPresent(
            msg -> {
              throw new IllegalStateException(
                  "Input equivalence rules: row Id " + rowId + " (" + column + "): " + msg);
            });
  }

  private void validateLinesForSave() {
    for (int i = 0; i < this.linesModel.getRowCount(); i++) {
      final int id = i + 1;
      final String a = EditorModule.cellString(this.linesModel.getValueAt(i, 1));
      final String b = EditorModule.cellString(this.linesModel.getValueAt(i, 2));
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
      final String keyStr = EditorModule.cellString(this.equivPairModel.getValueAt(i, 1));
      final String valStr = EditorModule.cellString(this.equivPairModel.getValueAt(i, 2));
      if (keyStr.isEmpty() && valStr.isEmpty()) {
        continue;
      }
      if (keyStr.isEmpty() || valStr.isEmpty()) {
        throw new IllegalStateException(
            "Input equivalence rules: row Id "
                + id
                + ": Key and Value must both be filled, or both left empty.");
      }
      EditorModule.requireValidEquivTokens(id, "Key", keyStr);
      EditorModule.requireValidEquivTokens(id, "Value", valStr);
    }
  }

  private static void stopTableCellEditingIfActive(final JTable table) {
    if (!table.isEditing()) {
      return;
    }
    final TableCellEditor editor = table.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  private void stopEquivTableEditing() {
    EditorModule.stopTableCellEditingIfActive(this.equivPairTable);
  }

  private void stopLinesTableEditing() {
    EditorModule.stopTableCellEditingIfActive(this.linesTable);
  }

  private List<InputEquivalenceRow> inputEquivalenceRowsFromTable() {
    final List<InputEquivalenceRow> out = new ArrayList<>();
    for (int i = 0; i < this.equivPairModel.getRowCount(); i++) {
      final String keyStr = EditorModule.cellString(this.equivPairModel.getValueAt(i, 1));
      final String valStr = EditorModule.cellString(this.equivPairModel.getValueAt(i, 2));
      if (keyStr.isEmpty() && valStr.isEmpty()) {
        continue;
      }
      out.add(
          new InputEquivalenceRow(
              EditorModule.splitCommaSeparated(keyStr), EditorModule.splitCommaSeparated(valStr)));
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
            new Object[] {
                0,
                EditorModule.joinCommaSeparated(row.key()),
                EditorModule.joinCommaSeparated(row.value())
            });
      }
    }
    this.refreshEquivPairIds();
    this.equivPairTable.setEnabled(true);
    this.equivPairTable.getTableHeader().setEnabled(true);
  }

  private void configureEquivPairTable() {
    this.equivPairTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.equivPairTable.getTableHeader().setReorderingAllowed(false);
    this.equivPairTable.setDefaultEditor(
        String.class, new DefaultCellEditor(this.equivTableCellField));
    this.equivPairTable.setFillsViewportHeight(true);
    this.equivPairTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    this.configureIdColumnWidth(this.equivPairTable, 36);
    this.equivPairTable.getColumnModel().getColumn(1).setPreferredWidth(200);
    this.equivPairTable.getColumnModel().getColumn(2).setPreferredWidth(200);
  }

  private void refreshEquivPairIds() {
    for (int i = 0; i < this.equivPairModel.getRowCount(); i++) {
      this.equivPairModel.setValueAt(i + 1, i, 0);
    }
  }

  private void configureLinesTable() {
    this.linesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.linesTable.getTableHeader().setReorderingAllowed(false);
    this.linesTable.setDefaultEditor(
        String.class, new DefaultCellEditor(this.linesTableCellField));
    this.linesTable.setFillsViewportHeight(true);
    this.linesTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    this.configureIdColumnWidth(this.linesTable, 36);
    this.linesTable.getColumnModel().getColumn(1).setPreferredWidth(280);
    this.linesTable.getColumnModel().getColumn(2).setPreferredWidth(280);
  }

  private void configureIdColumnWidth(final JTable table, final int width) {
    table.getColumnModel().getColumn(0).setMinWidth(width);
    table.getColumnModel().getColumn(0).setPreferredWidth(width);
    table.getColumnModel().getColumn(0).setMaxWidth(width);
  }

  private void refreshLineIds() {
    for (int i = 0; i < this.linesModel.getRowCount(); i++) {
      this.linesModel.setValueAt(i + 1, i, 0);
    }
  }

  private void buildUi() {
    if (this.editorFontSizePoints < 0f) {
      this.editorFontSizePoints = UIManager.getFont("Label.font").getSize2D();
    }

    this.rootPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));

    final JLabel heading = new JLabel("Editor — dialog JSON", SwingConstants.CENTER);
    heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

    final JPanel generalBlock = new JPanel(new GridBagLayout());
    generalBlock.setOpaque(false);
    EditorModule.shrinkWrap(generalBlock);
    generalBlock.setBorder(BorderFactory.createTitledBorder("General"));
    generalBlock.setPreferredSize(new Dimension(300, 0));

    final JScrollPane descScroll = new JScrollPane(this.fieldDescription);
    EditorModule.shrinkWrap(descScroll);
    descScroll.setPreferredSize(new Dimension(200, 96));

    final Insets cellPad = new Insets(4, 4, 4, 4);
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = cellPad;

    gbc.gridx = 0;
    gbc.gridwidth = 1;
    gbc.gridheight = 1;
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;

    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(EditorModule.label("Title (menuName)"), gbc);

    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(this.fieldTitle, gbc);

    gbc.gridy = 2;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(EditorModule.label("Description"), gbc);

    gbc.gridy = 3;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    generalBlock.add(descScroll, gbc);

    gbc.gridy = 4;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(EditorModule.label("Language A"), gbc);

    gbc.gridy = 5;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(this.fieldLangA, gbc);

    gbc.gridy = 6;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(EditorModule.label("Language B"), gbc);

    gbc.gridy = 7;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(this.fieldLangB, gbc);

    gbc.gridy = 8;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    generalBlock.add(this.checkShuffled, gbc);

    final JPanel linesWrap = new JPanel(new BorderLayout(8, 8));
    linesWrap.setOpaque(false);
    EditorModule.shrinkWrap(linesWrap);
    linesWrap.setBorder(BorderFactory.createTitledBorder("Lines (phrases)"));
    final JScrollPane linesScroll = new JScrollPane(this.linesTable);
    EditorModule.shrinkWrap(linesScroll);
    linesWrap.add(linesScroll, BorderLayout.CENTER);
    linesWrap.add(this.lineButtons(), BorderLayout.SOUTH);

    final JLabel pairHint =
        new JLabel(
            "Each row is one equivalence rule. Key and Value are comma-separated tokens (e.g. e,E and e,E,ё,Ё).",
            SwingConstants.LEADING);
    pairHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
    final JScrollPane pairScroll = new JScrollPane(this.equivPairTable);
    EditorModule.shrinkWrap(pairScroll);
    final JPanel equivTableWrap = new JPanel(new BorderLayout(0, 6));
    equivTableWrap.setOpaque(false);
    equivTableWrap.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
    EditorModule.shrinkWrap(equivTableWrap);
    equivTableWrap.add(pairHint, BorderLayout.NORTH);
    equivTableWrap.add(pairScroll, BorderLayout.CENTER);

    final JPanel equivBlock = new JPanel(new BorderLayout());
    equivBlock.setOpaque(false);
    EditorModule.shrinkWrap(equivBlock);
    equivBlock.setBorder(BorderFactory.createTitledBorder("Input equivalence rules"));
    equivBlock.add(equivTableWrap, BorderLayout.CENTER);
    equivBlock.add(this.equivButtons(), BorderLayout.SOUTH);

    final JSplitPane linesEquivSplit =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, linesWrap, equivBlock);
    linesEquivSplit.setResizeWeight(0.5);
    linesEquivSplit.setContinuousLayout(true);
    linesEquivSplit.setOneTouchExpandable(true);
    linesEquivSplit.setBorder(BorderFactory.createEmptyBorder());
    linesEquivSplit.setOpaque(false);
    EditorModule.shrinkWrap(linesEquivSplit);

    final JSplitPane mainSplit =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, generalBlock, linesEquivSplit);
    mainSplit.setResizeWeight(0.0);
    mainSplit.setContinuousLayout(true);
    mainSplit.setOneTouchExpandable(true);
    mainSplit.setBorder(BorderFactory.createEmptyBorder());
    mainSplit.setOpaque(false);

    final JPanel center = new JPanel(new BorderLayout());
    center.setOpaque(false);
    EditorModule.shrinkWrap(center);
    center.add(heading, BorderLayout.NORTH);
    center.add(mainSplit, BorderLayout.CENTER);

    final JPanel newDocRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    newDocRow.setOpaque(false);
    this.newDocumentButton = new JButton("New document");
    this.restyleNewDocumentButton();
    this.newDocumentButton.addActionListener(e -> {
      if (this.confirmLoseChanges()) {
        this.newDocument();
      }
    });
    newDocRow.add(this.newDocumentButton);

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
    add.addActionListener(e -> {
      this.linesModel.addRow(new Object[] {0, "", ""});
      this.refreshLineIds();
      final int last = this.linesModel.getRowCount() - 1;
      this.linesTable.setRowSelectionInterval(last, last);
    });
    remove.addActionListener(e -> this.removeSelectedLine());
    up.addActionListener(e -> this.moveLine(-1));
    down.addActionListener(e -> this.moveLine(1));
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
    add.addActionListener(e -> {
      this.equivPairModel.addRow(new Object[] {0, "", ""});
      this.refreshEquivPairIds();
      final int last = this.equivPairModel.getRowCount() - 1;
      this.equivPairTable.setRowSelectionInterval(last, last);
    });
    remove.addActionListener(e -> this.removeSelectedEquiv());
    up.addActionListener(e -> this.moveEquiv(-1));
    down.addActionListener(e -> this.moveEquiv(1));
    final JButton prefillEn =
        new JButton("Prefill for language");
    prefillEn.setToolTipText(
        "Replace rules with common US-keyboard → target-letter mappings (Estonian, German, Czech, Esperanto).");
    prefillEn.addActionListener(e -> this.prefillEquivFromEnglishPreset());
    p.add(add);
    p.add(remove);
    p.add(up);
    p.add(down);
    p.add(prefillEn);
    return p;
  }

  private boolean equivTableHasNonEmptyRule() {
    for (int i = 0; i < this.equivPairModel.getRowCount(); i++) {
      if (!EditorModule.cellString(this.equivPairModel.getValueAt(i, 1)).isEmpty()
          || !EditorModule.cellString(this.equivPairModel.getValueAt(i, 2)).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private void prefillEquivFromEnglishPreset() {
    final InputEquivalenceEnglishPresets.TargetLanguage[] langs =
        InputEquivalenceEnglishPresets.TargetLanguage.values();
    final JComboBox<String> combo = new JComboBox<>();
    for (final InputEquivalenceEnglishPresets.TargetLanguage lang : langs) {
      combo.addItem(lang.label());
    }
    combo.setSelectedIndex(0);
    final JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(
        new JLabel(
            "<html>Key column = typed on a US English keyboard;<br>"
                + "Value column = letters that count as correct.</html>"),
        BorderLayout.NORTH);
    panel.add(combo, BorderLayout.CENTER);
    if (JOptionPane.showConfirmDialog(
        this.rootPanel,
        panel,
        "Prefill input equivalence",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE)
        != JOptionPane.OK_OPTION) {
      return;
    }
    final int choice = combo.getSelectedIndex();
    if (choice < 0 || choice >= langs.length) {
      return;
    }
    if (this.equivTableHasNonEmptyRule()) {
      if (JOptionPane.showConfirmDialog(
          this.rootPanel,
          "Replace all existing input equivalence rules with this preset?",
          "Replace rules",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.WARNING_MESSAGE)
          != JOptionPane.YES_OPTION) {
        return;
      }
    }
    this.stopEquivTableEditing();
    this.fillEquivTableFromRules(
        InputEquivalenceEnglishPresets.rowsFor(langs[choice]));
    if (this.equivPairModel.getRowCount() > 0) {
      this.equivPairTable.setRowSelectionInterval(0, 0);
    }
  }

  private void removeSelectedLine() {
    final int r = this.linesTable.getSelectedRow();
    if (r < 0) {
      return;
    }
    if (this.linesModel.getRowCount() == 1) {
      this.stopLinesTableEditing();
      this.linesModel.setValueAt("", r, 1);
      this.linesModel.setValueAt("", r, 2);
      return;
    }
    this.stopLinesTableEditing();
    this.linesModel.removeRow(r);
    this.refreshLineIds();
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
    this.refreshLineIds();
    this.linesTable.setRowSelectionInterval(to, to);
    EditorModule.ensureTableRowVisible(this.linesTable, to);
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
    this.refreshEquivPairIds();
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
    this.refreshEquivPairIds();
    this.equivPairTable.setRowSelectionInterval(to, to);
    EditorModule.ensureTableRowVisible(this.equivPairTable, to);
  }

  private void newDocument() {
    this.currentFilePath = null;
    this.fieldTitle.setText("");
    this.fieldDescription.setText("");
    this.fieldLangA.setText("");
    this.fieldLangB.setText("");
    this.checkShuffled.setSelected(false);
    this.linesModel.setRowCount(0);
    this.linesModel.addRow(new Object[] {0, "", ""});
    this.refreshLineIds();
    this.clearEquivRules();
    this.linesTable.setRowSelectionInterval(0, 0);
  }

  private void clearEquivRules() {
    this.stopEquivTableEditing();
    this.fillEquivTableFromRules(List.of());
    this.equivPairTable.setEnabled(true);
  }

  private void applyDefinition(final DialogDefinition def) {
    this.fieldTitle.setText(def.menuName());
    this.fieldDescription.setText(def.description());
    this.fieldLangA.setText(def.langA());
    this.fieldLangB.setText(def.langB());
    this.checkShuffled.setSelected(def.shuffled());
    this.linesModel.setRowCount(0);
    for (final DialogLine line : def.lines()) {
      this.linesModel.addRow(new Object[] {0, line.a(), line.b()});
    }
    this.refreshLineIds();
    this.stopEquivTableEditing();
    this.fillEquivTableFromRules(def.inputEqu());
    if (this.linesModel.getRowCount() > 0) {
      this.linesTable.setRowSelectionInterval(0, 0);
    }
  }

  private DialogDefinition readDefinitionFromUi() {
    this.stopLinesTableEditing();
    this.stopEquivTableEditing();
    if (this.linesModel.getRowCount() == 0) {
      throw new IllegalStateException("Add at least one dialog line.");
    }
    this.validateLinesForSave();
    this.validateEquivPairsForSave();
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
        this.inputEquivalenceRowsFromTable(),
        this.checkShuffled.isSelected());
  }

  private void loadFromUser() {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
    chooser.setAcceptAllFileFilterUsed(false);
    if (EditorModule.workDirectory != null) {
      chooser.setCurrentDirectory(EditorModule.workDirectory);
    }
    if (chooser.showOpenDialog(this.rootPanel) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    final File f = chooser.getSelectedFile();
    if (f == null) {
      return;
    }
    EditorModule.rememberWorkDir(f);
    try {
      final String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
      final DialogDefinition def = LangResourceJson.parse(text);
      this.currentFilePath = f.toPath();
      this.applyDefinition(def);
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
      def = this.readDefinitionFromUi();
      LangResourceJson.parse(LangResourceJson.toPrettyJson(def));
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
    if (EditorModule.workDirectory != null) {
      chooser.setCurrentDirectory(EditorModule.workDirectory);
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
    if (!out.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
      out = new File(out.getParentFile(), out.getName() + ".json");
    }
    EditorModule.rememberWorkDir(out);
    try {
      Files.writeString(out.toPath(), LangResourceJson.toPrettyJson(def),
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

  private void restyleNewDocumentButton() {
    EditorModule.stylePrimary(
        this.newDocumentButton,
        new Color(93, 64, 55),
        this.editorFontSizePoints + EditorModule.PRIMARY_BUTTON_EXTRA_PT);
  }

  private void applyFontSizeToTree(final Component c, final float sizePt) {
    if (c instanceof JComponent jc) {
      final Font existing = jc.getFont();
      if (existing != null) {
        jc.setFont(existing.deriveFont(sizePt));
      }
      if (jc.getBorder() instanceof final TitledBorder tb) {
        Font titleFont = tb.getTitleFont();
        if (titleFont == null) {
          titleFont = existing != null ? existing : UIManager.getFont("Label.font");
        }
        tb.setTitleFont(titleFont.deriveFont(sizePt));
      }
      for (final Component child : jc.getComponents()) {
        this.applyFontSizeToTree(child, sizePt);
      }
      return;
    }
    if (c instanceof Container ct) {
      for (final Component child : ct.getComponents()) {
        this.applyFontSizeToTree(child, sizePt);
      }
    }
  }

  private void applyEditorModuleFonts() {
    this.applyFontSizeToTree(this.rootPanel, this.editorFontSizePoints);
    this.restyleNewDocumentButton();
    final Font cellFont = this.linesTableCellField.getFont().deriveFont(this.editorFontSizePoints);
    this.linesTableCellField.setFont(cellFont);
    this.equivTableCellField.setFont(cellFont);
    for (final JComponent t : this.toolbarFontTargets) {
      final Font f = t.getFont();
      if (f != null) {
        t.setFont(f.deriveFont(this.editorFontSizePoints));
      }
    }
    this.syncEditorTableRowHeights();
    this.rootPanel.revalidate();
    this.rootPanel.repaint();
    if (!this.toolbarFontTargets.isEmpty()) {
      final Container bar = this.toolbarFontTargets.get(0).getParent();
      if (bar != null) {
        bar.revalidate();
        bar.repaint();
      }
    }
  }

  private void syncEditorTableRowHeights() {
    this.syncEditorTableRowHeight(this.linesTable);
    this.syncEditorTableRowHeight(this.equivPairTable);
  }

  private void syncEditorTableRowHeight(final JTable table) {
    final Font font = table.getFont();
    if (font == null) {
      return;
    }
    final FontMetrics fm = table.getFontMetrics(font);
    table.setRowHeight(
        Math.max(EditorModule.TABLE_ROW_MIN_PX, fm.getHeight() + EditorModule.TABLE_ROW_PAD_PX));
    final JTableHeader header = table.getTableHeader();
    if (header != null) {
      final Font headerFont = header.getFont();
      if (headerFont != null) {
        final FontMetrics hfm = header.getFontMetrics(headerFont);
        final int headerH =
            Math.max(EditorModule.TABLE_ROW_MIN_PX,
                hfm.getHeight() + EditorModule.TABLE_ROW_PAD_PX);
        final Dimension headerPref = header.getPreferredSize();
        header.setPreferredSize(new Dimension(headerPref.width, headerH));
      }
    }
  }

  private void bumpEditorFont(final float delta) {
    final float clamped =
        Math.min(
            EditorModule.EDITOR_FONT_MAX_PT,
            Math.max(EditorModule.EDITOR_FONT_MIN_PT, this.editorFontSizePoints + delta));
    if (clamped == this.editorFontSizePoints) {
      return;
    }
    this.editorFontSizePoints = clamped;
    this.applyEditorModuleFonts();
  }

  @Override
  public void populateMainToolbar(final JPanel eastToolbar) {
    this.toolbarFontTargets.clear();
    final JButton load =
        new JButton(ImageResourceLoader.loadIcon("/editor/images/editor-load.svg", 24, 24));
    load.setToolTipText("Load JSON file…");
    load.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    load.setFocusPainted(false);
    load.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    load.addActionListener(e -> this.loadFromUser());
    final JButton save =
        new JButton(ImageResourceLoader.loadIcon("/editor/images/editor-save.svg", 24, 24));
    save.setToolTipText("Save JSON file…");
    save.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    save.setFocusPainted(false);
    save.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    save.addActionListener(e -> this.saveToUser());
    final JButton fontSmaller =
        new JButton(
            ImageResourceLoader.loadIcon("/editor/images/editor-font-decrease.svg", 24, 24));
    fontSmaller.setToolTipText("Decrease font size for this module");
    fontSmaller.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    fontSmaller.setFocusPainted(false);
    fontSmaller.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    fontSmaller.addActionListener(e -> this.bumpEditorFont(-EditorModule.EDITOR_FONT_STEP_PT));
    final JButton fontLarger =
        new JButton(
            ImageResourceLoader.loadIcon("/editor/images/editor-font-increase.svg", 24, 24));
    fontLarger.setToolTipText("Increase font size for this module");
    fontLarger.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    fontLarger.setFocusPainted(false);
    fontLarger.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    fontLarger.addActionListener(e -> this.bumpEditorFont(EditorModule.EDITOR_FONT_STEP_PT));
    eastToolbar.add(load);
    eastToolbar.add(save);
    eastToolbar.add(fontSmaller);
    eastToolbar.add(fontLarger);
    this.toolbarFontTargets.add(load);
    this.toolbarFontTargets.add(save);
    this.toolbarFontTargets.add(fontSmaller);
    this.toolbarFontTargets.add(fontLarger);
    this.applyEditorModuleFonts();
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
    SwingUtilities.invokeLater(this.fieldTitle::requestFocusInWindow);
  }
}
