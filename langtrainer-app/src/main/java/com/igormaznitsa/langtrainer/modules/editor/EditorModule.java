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
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
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
import javax.swing.event.ListSelectionEvent;
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
  private final JTextArea fieldDescription = new JTextArea(2, 40);
  private final JTextField fieldLangA = new JTextField();
  private final JTextField fieldLangB = new JTextField();
  private final DefaultTableModel linesModel =
      new DefaultTableModel(new Object[] {"A", "B"}, 0) {
        @Override
        public Class<?> getColumnClass(final int columnIndex) {
          return String.class;
        }
      };
  private final JTable linesTable = new JTable(this.linesModel);
  private final List<InputEquivalenceRow> equivRules = new ArrayList<>();
  private final DefaultListModel<String> equivRuleListModel = new DefaultListModel<>();
  private final JList<String> equivRuleList = new JList<>(this.equivRuleListModel);
  private final JTextArea equivKeysArea = new JTextArea(10, 22);
  private final JTextArea equivValsArea = new JTextArea(10, 22);
  /**
   * Index whose text is currently shown in {@link #equivKeysArea} / {@link #equivValsArea}.
   */
  private int equivDisplayedIndex = -1;

  private Path currentFilePath;

  public EditorModule() {
    this.contentFont = this.rootPanel.getFont().deriveFont(Font.PLAIN, CONTENT_FONT_PT);
    this.fieldDescription.setLineWrap(true);
    this.fieldDescription.setWrapStyleWord(true);
    styleTextFields();
    configureLinesTable();
    styleEquivEditAreas();
    this.equivRuleList.setFixedCellHeight(32);
    this.equivRuleList.setFont(this.contentFont);
    this.equivRuleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.equivRuleList.addListSelectionListener(this::onEquivRuleSelectionChanged);
    buildUi();
    newDocument();
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

  private static InputEquivalenceRow rowFromTextAreas(final JTextArea keys, final JTextArea vals) {
    return new InputEquivalenceRow(linesFromArea(keys), linesFromArea(vals));
  }

  private static String joinLines(final List<String> parts) {
    return parts.stream().collect(Collectors.joining("\n"));
  }

  private static List<String> linesFromArea(final JTextArea area) {
    final List<String> out = new ArrayList<>();
    for (final String line : area.getText().split("\\R", -1)) {
      final String s = line.strip();
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return List.copyOf(out);
  }

  private void styleEquivEditAreas() {
    final Font f = this.contentFont;
    this.equivKeysArea.setFont(f);
    this.equivValsArea.setFont(f);
    this.equivKeysArea.setMargin(new Insets(8, 10, 8, 10));
    this.equivValsArea.setMargin(new Insets(8, 10, 8, 10));
    this.equivKeysArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    this.equivValsArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
  }

  private void onEquivRuleSelectionChanged(final ListSelectionEvent event) {
    if (event.getValueIsAdjusting()) {
      return;
    }
    final int newIdx = this.equivRuleList.getSelectedIndex();
    commitEquivAtDisplayedIndex();
    this.equivDisplayedIndex = newIdx;
    fillEquivEditorsFromModel(newIdx);
  }

  /**
   * Persists text areas into {@link #equivRules} for {@link #equivDisplayedIndex}.
   */
  private void commitEquivAtDisplayedIndex() {
    if (this.equivDisplayedIndex < 0 || this.equivDisplayedIndex >= this.equivRules.size()) {
      return;
    }
    this.equivRules.set(
        this.equivDisplayedIndex, rowFromTextAreas(this.equivKeysArea, this.equivValsArea));
  }

  private void fillEquivEditorsFromModel(final int index) {
    if (index < 0 || index >= this.equivRules.size()) {
      this.equivKeysArea.setText("");
      this.equivValsArea.setText("");
      this.equivKeysArea.setEnabled(false);
      this.equivValsArea.setEnabled(false);
      return;
    }
    final InputEquivalenceRow row = this.equivRules.get(index);
    this.equivKeysArea.setText(joinLines(row.key()));
    this.equivValsArea.setText(joinLines(row.value()));
    this.equivKeysArea.setEnabled(true);
    this.equivValsArea.setEnabled(true);
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
    final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
    renderer.setFont(f);
    this.linesTable.setDefaultRenderer(Object.class, renderer);
    this.linesTable.setDefaultRenderer(String.class, renderer);
    final JTextField cellField = new JTextField();
    cellField.setFont(f);
    cellField.setMargin(new Insets(4, 8, 4, 8));
    this.linesTable.setDefaultEditor(String.class, new DefaultCellEditor(cellField));
  }

  private void buildUi() {
    this.rootPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
    this.rootPanel.setBackground(PANEL_BG);

    final JLabel heading = new JLabel("Editor — dialog JSON", SwingConstants.CENTER);
    heading.setFont(heading.getFont().deriveFont(Font.BOLD, 26f));
    heading.setForeground(ACCENT);
    heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

    final JPanel header = new JPanel(new GridLayout(4, 2, 10, 8));
    header.setOpaque(false);
    header.add(label("Title (menuName)"));
    header.add(this.fieldTitle);
    header.add(label("Description"));
    final JScrollPane descScroll = new JScrollPane(this.fieldDescription);
    descScroll.setPreferredSize(new Dimension(200, 52));
    header.add(descScroll);
    header.add(label("Language A"));
    header.add(this.fieldLangA);
    header.add(label("Language B"));
    header.add(this.fieldLangB);

    final JPanel linesWrap = new JPanel(new BorderLayout(8, 8));
    linesWrap.setOpaque(false);
    linesWrap.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(new Color(100, 130, 170), 2, true),
        "Lines (phrases)",
        0,
        0,
        this.fieldTitle.getFont().deriveFont(Font.BOLD, 16f),
        ACCENT));
    final JScrollPane linesScroll = new JScrollPane(this.linesTable);
    linesScroll.setPreferredSize(new Dimension(400, 200));
    linesWrap.add(linesScroll, BorderLayout.CENTER);
    linesWrap.add(lineButtons(), BorderLayout.SOUTH);

    final JPanel topBlock = new JPanel(new BorderLayout(0, 10));
    topBlock.setOpaque(false);
    topBlock.add(header, BorderLayout.NORTH);
    topBlock.add(linesWrap, BorderLayout.CENTER);

    final JPanel keysCol = wrapEquivColumn("Key equivalents (one per line)", this.equivKeysArea);
    final JPanel valsCol = wrapEquivColumn("Value equivalents (one per line)", this.equivValsArea);
    final JPanel detailGrid = new JPanel(new GridLayout(1, 2, 12, 0));
    detailGrid.setOpaque(false);
    detailGrid.add(keysCol);
    detailGrid.add(valsCol);
    final JPanel detailPanel = new JPanel(new BorderLayout());
    detailPanel.setOpaque(true);
    detailPanel.setBackground(Color.WHITE);
    detailPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    detailPanel.add(detailGrid, BorderLayout.CENTER);
    final JScrollPane detailScroll = new JScrollPane(detailPanel);
    detailScroll.getViewport().setBackground(Color.WHITE);

    final JScrollPane equivListScroll = new JScrollPane(this.equivRuleList);
    equivListScroll.setMinimumSize(new Dimension(140, 80));
    equivListScroll.setPreferredSize(new Dimension(160, 200));
    final JSplitPane equivSplit =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, equivListScroll, detailScroll);
    equivSplit.setResizeWeight(0.22);
    equivSplit.setContinuousLayout(true);
    equivSplit.setOneTouchExpandable(true);
    equivSplit.setBorder(BorderFactory.createEmptyBorder());

    final JPanel equivBlock = new JPanel(new BorderLayout());
    equivBlock.setOpaque(false);
    equivBlock.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(new Color(120, 100, 160), 2, true),
        "Input equivalence rules",
        0,
        0,
        this.fieldTitle.getFont().deriveFont(Font.BOLD, 16f),
        new Color(74, 20, 140)));
    equivBlock.add(equivSplit, BorderLayout.CENTER);
    equivBlock.add(equivButtons(), BorderLayout.SOUTH);

    final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topBlock, equivBlock);
    split.setResizeWeight(0.55);
    split.setOpaque(false);

    final JPanel center = new JPanel(new BorderLayout());
    center.setOpaque(false);
    center.add(heading, BorderLayout.NORTH);
    center.add(split, BorderLayout.CENTER);

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

  private JPanel wrapEquivColumn(final String title, final JTextArea area) {
    final JPanel col = new JPanel(new BorderLayout(0, 6));
    col.setOpaque(false);
    final JLabel lab = new JLabel(title);
    lab.setFont(lab.getFont().deriveFont(Font.BOLD, 15f));
    lab.setForeground(new Color(55, 71, 79));
    col.add(lab, BorderLayout.NORTH);
    col.add(new JScrollPane(area), BorderLayout.CENTER);
    return col;
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
    add.addActionListener(e -> this.linesModel.addRow(new Object[] {"", ""}));
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
      commitEquivAtDisplayedIndex();
      this.equivRules.add(new InputEquivalenceRow(List.of(), List.of()));
      refreshEquivRuleTitles();
      final int last = this.equivRules.size() - 1;
      this.equivRuleList.setSelectedIndex(last);
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
    if (r >= 0) {
      this.linesModel.removeRow(r);
    }
  }

  private void moveLine(final int delta) {
    final int r = this.linesTable.getSelectedRow();
    final int n = this.linesModel.getRowCount();
    final int to = r + delta;
    if (r < 0 || to < 0 || to >= n) {
      return;
    }
    for (int c = 0; c < 2; c++) {
      final Object a = this.linesModel.getValueAt(r, c);
      final Object b = this.linesModel.getValueAt(to, c);
      this.linesModel.setValueAt(b, r, c);
      this.linesModel.setValueAt(a, to, c);
    }
    this.linesTable.setRowSelectionInterval(to, to);
  }

  private void removeSelectedEquiv() {
    if (this.equivRules.isEmpty()) {
      return;
    }
    final int idx = this.equivRuleList.getSelectedIndex();
    if (idx < 0) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "Select a rule in the list on the left.",
          "No rule selected",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    commitEquivAtDisplayedIndex();
    this.equivRules.remove(idx);
    refreshEquivRuleTitles();
    this.equivDisplayedIndex = -1;
    if (this.equivRules.isEmpty()) {
      this.equivRuleList.clearSelection();
      fillEquivEditorsFromModel(-1);
    } else {
      final int sel = Math.min(idx, this.equivRules.size() - 1);
      this.equivRuleList.setSelectedIndex(sel);
    }
  }

  private void moveEquiv(final int delta) {
    if (this.equivRules.isEmpty()) {
      return;
    }
    final int idx = this.equivRuleList.getSelectedIndex();
    if (idx < 0) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "Select a rule in the list on the left.",
          "No rule selected",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    final int to = idx + delta;
    if (to < 0 || to >= this.equivRules.size()) {
      return;
    }
    commitEquivAtDisplayedIndex();
    final InputEquivalenceRow a = this.equivRules.get(idx);
    final InputEquivalenceRow b = this.equivRules.get(to);
    this.equivRules.set(idx, b);
    this.equivRules.set(to, a);
    refreshEquivRuleTitles();
    this.equivRuleList.setSelectedIndex(to);
  }

  private void refreshEquivRuleTitles() {
    this.equivRuleListModel.clear();
    for (int i = 0; i < this.equivRules.size(); i++) {
      this.equivRuleListModel.addElement("Rule " + (i + 1));
    }
  }

  private void newDocument() {
    this.currentFilePath = null;
    this.fieldTitle.setText("");
    this.fieldDescription.setText("");
    this.fieldLangA.setText("");
    this.fieldLangB.setText("");
    this.linesModel.setRowCount(0);
    this.linesModel.addRow(new Object[] {"", ""});
    clearEquivRules();
    this.linesTable.setRowSelectionInterval(0, 0);
  }

  private void clearEquivRules() {
    commitEquivAtDisplayedIndex();
    this.equivRules.clear();
    this.equivDisplayedIndex = -1;
    refreshEquivRuleTitles();
    this.equivRuleList.clearSelection();
    fillEquivEditorsFromModel(-1);
  }

  private void applyDefinition(final DialogDefinition def) {
    this.fieldTitle.setText(def.menuName());
    this.fieldDescription.setText(def.description());
    this.fieldLangA.setText(def.langA());
    this.fieldLangB.setText(def.langB());
    this.linesModel.setRowCount(0);
    for (final DialogLine line : def.lines()) {
      this.linesModel.addRow(new Object[] {line.a(), line.b()});
    }
    commitEquivAtDisplayedIndex();
    this.equivRules.clear();
    this.equivRules.addAll(def.inputEqu());
    this.equivDisplayedIndex = -1;
    refreshEquivRuleTitles();
    if (!this.equivRules.isEmpty()) {
      this.equivRuleList.setSelectedIndex(0);
    } else {
      this.equivRuleList.clearSelection();
      fillEquivEditorsFromModel(-1);
    }
    if (this.linesModel.getRowCount() > 0) {
      this.linesTable.setRowSelectionInterval(0, 0);
    }
  }

  private DialogDefinition readDefinitionFromUi() {
    commitEquivAtDisplayedIndex();
    if (this.linesModel.getRowCount() == 0) {
      throw new IllegalStateException("Add at least one dialog line.");
    }
    final List<DialogLine> lines = new ArrayList<>();
    for (int i = 0; i < this.linesModel.getRowCount(); i++) {
      final String a = String.valueOf(this.linesModel.getValueAt(i, 0) == null
          ? ""
          : this.linesModel.getValueAt(i, 0));
      final String b = String.valueOf(this.linesModel.getValueAt(i, 1) == null
          ? ""
          : this.linesModel.getValueAt(i, 1));
      lines.add(new DialogLine(a, b));
    }
    return new DialogDefinition(
        this.fieldTitle.getText().strip(),
        this.fieldDescription.getText().strip(),
        this.fieldLangA.getText().strip(),
        this.fieldLangB.getText().strip(),
        lines,
        List.copyOf(this.equivRules));
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
    return List.of(KeyboardLanguage.ENG, KeyboardLanguage.RUS, KeyboardLanguage.EST);
  }

  @Override
  public void onActivation() {
    SwingUtilities.invokeLater(() -> this.fieldTitle.requestFocusInWindow());
  }
}
