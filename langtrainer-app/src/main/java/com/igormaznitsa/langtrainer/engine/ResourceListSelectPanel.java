package com.igormaznitsa.langtrainer.engine;

import static com.igormaznitsa.langtrainer.engine.ImageResourceLoader.loadIcon;
import static java.util.Objects.requireNonNull;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Resource selection for modules that list {@link DialogListEntry} items, open JSON from file, and
 * start a session. Double-clicking a resource row starts the same way as the primary start button.
 * Single-clicking a folder row invokes {@code onFolderRowSingleClick} (expand/collapse).
 */
public final class ResourceListSelectPanel {

  private static final int INDENT_PER_LEVEL = 14;
  private static final String SYNC_LESSONS_LABEL = "Sync Lessons";
  private static final String START_TOOLTIP = "Start training with the selected resource";
  private static final String OPEN_FROM_FILE_TOOLTIP = "Open a compatible JSON resource from disk";
  private static final String SYNC_LESSONS_TOOLTIP =
      "Download or refresh lesson resources from the online lesson repository";
  private static final Icon START_ICON = loadIcon("/images/action-start.svg", 26, 26);
  private static final Icon OPEN_FROM_FILE_ICON = loadIcon("/images/action-open-file.svg", 26, 26);
  private static final Icon SYNC_GITHUB_ICON = loadIcon("/images/action-sync-github.svg", 26, 26);
  private static final Icon EMBEDDED_RESOURCE_ICON =
      new ResourceSourceIcon(new Color(55, 71, 79), new Color(117, 117, 117), ResourceGlyph.DOT);
  private static final Icon EXTERNAL_RESOURCE_ICON =
      new ResourceSourceIcon(new Color(74, 20, 140), new Color(123, 31, 162),
          ResourceGlyph.SYNC);
  private static final Icon FILE_RESOURCE_ICON =
      new ResourceSourceIcon(new Color(230, 126, 34), new Color(245, 166, 35),
          ResourceGlyph.FOLDER);
  private static final Color START_BUTTON_BG = new Color(46, 125, 50);
  private static final Color OPEN_BUTTON_BG = new Color(25, 118, 210);
  private static final Color SYNC_BUTTON_BG = new Color(123, 31, 162);

  private ResourceListSelectPanel() {
  }

  public static Result build(
      final DefaultListModel<DialogListEntry> model,
      final Appearance appearance,
      final String titleText,
      final String startButtonLabel,
      final String openFromFileLabel,
      final Consumer<DialogDefinition> onStart,
      final Consumer<JList<DialogListEntry>> onOpenFromFile,
      final Consumer<DialogListEntry.DialogFolderRow> onFolderRowSingleClick) {
    return build(
        model,
        appearance,
        titleText,
        startButtonLabel,
        openFromFileLabel,
        onStart,
        onOpenFromFile,
        onFolderRowSingleClick,
        null);
  }

  public static Result build(
      final DefaultListModel<DialogListEntry> model,
      final Appearance appearance,
      final String titleText,
      final String startButtonLabel,
      final String openFromFileLabel,
      final Consumer<DialogDefinition> onStart,
      final Consumer<JList<DialogListEntry>> onOpenFromFile,
      final Consumer<DialogListEntry.DialogFolderRow> onFolderRowSingleClick,
      final Runnable onLoadExternals) {
    final JPanel panel = new JPanel(new BorderLayout(12, 14));
    final BusyOverlayPanel busyOverlay = new BusyOverlayPanel();
    final Result result = new Result(wrapWithBusyOverlay(panel, busyOverlay), busyOverlay);
    final Color panelBg = appearance == Appearance.DIALOG
        ? new Color(236, 242, 249)
        : new Color(230, 240, 255);
    panel.setBackground(panelBg);
    panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));

    final JLabel title = new JLabel(titleText, SwingConstants.CENTER);
    if (appearance == Appearance.DIALOG) {
      title.setFont(title.getFont().deriveFont(Font.BOLD, 28.0f));
      title.setForeground(new Color(25, 45, 85));
      title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    } else {
      title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
      title.setForeground(new Color(25, 55, 120));
    }
    panel.add(title, BorderLayout.NORTH);

    final JList<DialogListEntry> list = new JList<>(model);
    if (appearance == Appearance.DIALOG) {
      applyDialogListLook(list);
    } else {
      applyFlyListLook(list);
    }
    final int firstResource = DialogListEntry.indexOfFirstResourceRow(model);
    if (firstResource >= 0) {
      list.setSelectedIndex(firstResource);
    }
    bindDoubleClickToStart(list, onStart);
    if (onFolderRowSingleClick != null) {
      bindFolderRowSingleClick(list, onFolderRowSingleClick);
    }
    result.setList(list);

    final JScrollPane scroll = new JScrollPane(list);
    if (appearance == Appearance.DIALOG) {
      final Color listBorder = new Color(100, 130, 170);
      final Color unselectedBg = Color.WHITE;
      scroll.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(listBorder, 2, true),
              BorderFactory.createEmptyBorder(2, 2, 2, 2)));
      scroll.getViewport().setBackground(unselectedBg);
      scroll.setPreferredSize(new Dimension(480, 280));
    } else {
      scroll.setPreferredSize(new Dimension(460, 260));
    }
    panel.add(scroll, BorderLayout.CENTER);

    if (appearance == Appearance.DIALOG) {
      addDialogSouth(panel, panelBg, list, startButtonLabel, openFromFileLabel, onStart,
          onOpenFromFile, onLoadExternals, result);
    } else {
      addFlySouth(
          panel, list, startButtonLabel, openFromFileLabel, onStart, onOpenFromFile,
          onLoadExternals, result);
    }

    return result;
  }

  private static JPanel wrapWithBusyOverlay(
      final JPanel content, final BusyOverlayPanel busyOverlay) {
    busyOverlay.setVisible(false);
    return new JPanel(null) {
      {
        this.add(content);
        this.add(busyOverlay);
        this.setComponentZOrder(busyOverlay, 0);
      }

      @Override
      public void doLayout() {
        content.setBounds(0, 0, this.getWidth(), this.getHeight());
        busyOverlay.setBounds(0, 0, this.getWidth(), this.getHeight());
      }

      @Override
      public Dimension getPreferredSize() {
        return content.getPreferredSize();
      }
    };
  }

  private static Icon folderRowIcon() {
    Icon icon = UIManager.getIcon("FileView.directoryIcon");
    if (icon != null) {
      return icon;
    }
    return UIManager.getIcon("Tree.closedIcon");
  }

  private static Icon resourceRowIcon(final DialogListEntry.DialogResourceRow row) {
    return switch (row.source()) {
      case EMBEDDED -> EMBEDDED_RESOURCE_ICON;
      case EXTERNAL -> EXTERNAL_RESOURCE_ICON;
      case FILE -> FILE_RESOURCE_ICON;
    };
  }

  private static String folderRowText(final DialogListEntry.DialogFolderRow folder) {
    return (folder.expanded() ? "▼ " : "▶ ") + folder.title().toUpperCase(Locale.ROOT);
  }

  private static void addDialogSouth(
      final JPanel panel,
      final Color panelBg,
      final JList<DialogListEntry> list,
      final String startButtonLabel,
      final String openFromFileLabel,
      final Consumer<DialogDefinition> onStart,
      final Consumer<JList<DialogListEntry>> onOpenFromFile,
      final Runnable onLoadExternals,
      final Result result) {
    final JButton start =
        makeDialogActionButton(startButtonLabel, START_BUTTON_BG, new Color(27, 94, 32),
            BorderFactory.createEmptyBorder(14, 32, 14, 32));
    final JButton openFile =
        makeDialogActionButton(openFromFileLabel, OPEN_BUTTON_BG, new Color(13, 71, 161),
            BorderFactory.createEmptyBorder(14, 28, 14, 28));
    start.setIcon(START_ICON);
    openFile.setIcon(OPEN_FROM_FILE_ICON);
    start.setToolTipText(START_TOOLTIP);
    openFile.setToolTipText(OPEN_FROM_FILE_TOOLTIP);
    bindStartButton(start, list, onStart);
    bindOpenFileButton(openFile, list, onOpenFromFile);
    result.addBusyControlled(openFile);
    result.addAvailabilityControlled(start, () -> hasSelectedResourceRow(list));
    list.addListSelectionListener(event -> result.refreshAvailabilityBindings());

    final JPanel southWrap = new JPanel(new BorderLayout());
    southWrap.setBackground(panelBg);
    southWrap.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
    buttonRow.setOpaque(false);
    buttonRow.add(start);
    southWrap.add(buttonRow, BorderLayout.CENTER);
    if (onLoadExternals != null) {
      addExternalResourceButtonsRight(
          southWrap,
          openFile,
          makeLoadExternalsButton(onLoadExternals, result,
              ResourceListSelectPanel::styleDialogLoadExternalsButton),
          8);
    } else {
      buttonRow.add(openFile);
    }
    panel.add(southWrap, BorderLayout.SOUTH);
  }

  private static void addFlySouth(
      final JPanel panel,
      final JList<DialogListEntry> list,
      final String startButtonLabel,
      final String openFromFileLabel,
      final Consumer<DialogDefinition> onStart,
      final Consumer<JList<DialogListEntry>> onOpenFromFile,
      final Runnable onLoadExternals,
      final Result result) {
    final JPanel south = new JPanel(new BorderLayout());
    south.setOpaque(false);
    final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
    buttonRow.setOpaque(false);
    final JButton open = new JButton(openFromFileLabel);
    styleFlyPrimaryButton(open, OPEN_BUTTON_BG);
    open.setIcon(OPEN_FROM_FILE_ICON);
    open.setToolTipText(OPEN_FROM_FILE_TOOLTIP);
    bindOpenFileButton(open, list, onOpenFromFile);
    final JButton start = new JButton(startButtonLabel);
    styleFlyPrimaryButton(start, START_BUTTON_BG);
    start.setIcon(START_ICON);
    start.setToolTipText(START_TOOLTIP);
    bindStartButton(start, list, onStart);
    buttonRow.add(start);
    south.add(buttonRow, BorderLayout.CENTER);
    if (onLoadExternals != null) {
      addExternalResourceButtonsRight(
          south,
          open,
          makeLoadExternalsButton(onLoadExternals, result,
              button -> styleFlyPrimaryButton(button, SYNC_BUTTON_BG)),
          5);
    } else {
      buttonRow.add(open);
    }
    result.addBusyControlled(open);
    result.addAvailabilityControlled(start, () -> hasSelectedResourceRow(list));
    list.addListSelectionListener(event -> result.refreshAvailabilityBindings());
    panel.add(south, BorderLayout.SOUTH);
  }

  private static JButton makeDialogActionButton(
      final String label,
      final Color background,
      final Color borderColor,
      final javax.swing.border.Border padding) {
    final JButton button = new JButton(label);
    styleDialogButton(button, background, borderColor, padding);
    return button;
  }

  private static JButton makeLoadExternalsButton(
      final Runnable onLoadExternals,
      final Result result,
      final Consumer<JButton> styleButton) {
    final JButton button = new JButton(SYNC_LESSONS_LABEL);
    styleButton.accept(button);
    button.setIcon(SYNC_GITHUB_ICON);
    button.setToolTipText(SYNC_LESSONS_TOOLTIP);
    button.addActionListener(event -> onLoadExternals.run());
    result.addBusyControlled(button);
    return button;
  }

  private static void styleDialogLoadExternalsButton(final JButton button) {
    styleDialogButton(button, SYNC_BUTTON_BG, new Color(74, 20, 140),
        BorderFactory.createEmptyBorder(14, 28, 14, 28));
  }

  private static void styleDialogButton(
      final JButton button,
      final Color background,
      final Color borderColor,
      final javax.swing.border.Border padding) {
    button.setFont(button.getFont().deriveFont(Font.BOLD, 18f));
    button.setForeground(Color.WHITE);
    button.setBackground(background);
    button.setOpaque(true);
    button.setContentAreaFilled(true);
    button.setFocusPainted(false);
    button.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 2, true),
            padding));
    button.setIconTextGap(8);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  private static void addExternalResourceButtonsRight(
      final JPanel container,
      final JButton openFileButton,
      final JButton syncButton,
      final int verticalGap) {
    final JPanel syncRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, verticalGap));
    syncRow.setOpaque(false);
    syncRow.add(openFileButton);
    syncRow.add(syncButton);
    container.add(syncRow, BorderLayout.EAST);
  }

  private static void bindOpenFileButton(
      final JButton button,
      final JList<DialogListEntry> list,
      final Consumer<JList<DialogListEntry>> onOpenFromFile) {
    button.addActionListener(event -> onOpenFromFile.accept(list));
  }

  private static void bindStartButton(
      final JButton button,
      final JList<DialogListEntry> list,
      final Consumer<DialogDefinition> onStart) {
    button.addActionListener(
        event -> {
          final DialogListEntry selected = list.getSelectedValue();
          if (selected instanceof final DialogListEntry.DialogResourceRow row) {
            onStart.accept(row.definition());
          }
        });
  }

  private static boolean hasSelectedResourceRow(final JList<DialogListEntry> list) {
    return list.getSelectedValue() instanceof DialogListEntry.DialogResourceRow;
  }

  private static void styleFlyPrimaryButton(final JButton button, final Color bg) {
    button.setFont(button.getFont().deriveFont(Font.BOLD, 16f));
    button.setForeground(Color.WHITE);
    button.setBackground(bg);
    button.setOpaque(true);
    button.setFocusPainted(false);
    button.setIconTextGap(8);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  private static void applyDialogListLook(final JList<DialogListEntry> list) {
    final Color selectedBg = new Color(21, 101, 192);
    final Color unselectedBg = Color.WHITE;
    final Color unselectedFg = new Color(38, 50, 56);
    final Color rowDivider = new Color(215, 224, 238);
    list.setBackground(unselectedBg);
    list.setSelectionBackground(selectedBg);
    list.setSelectionForeground(Color.WHITE);
    list.setFont(list.getFont().deriveFont(Font.PLAIN, 19f));
    list.setFixedCellHeight(52);
    list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    list.setCellRenderer(dialogListRenderer(rowDivider, selectedBg, unselectedBg, unselectedFg));
  }

  private static ListCellRenderer<DialogListEntry> dialogListRenderer(
      final Color rowDivider,
      final Color selectedBg,
      final Color unselectedBg,
      final Color unselectedFg) {
    final Icon folderIcon = folderRowIcon();
    return (jList, value, index, isSelected, cellHasFocus) -> {
      final JLabel label = new JLabel();
      label.setOpaque(true);
      label.setFont(label.getFont().deriveFont(Font.BOLD, 19f));
      label.setHorizontalAlignment(SwingConstants.LEFT);
      label.setVerticalAlignment(SwingConstants.CENTER);
      label.setIconTextGap(8);
      if (value instanceof final DialogListEntry.DialogResourceRow row) {
        final int left = 18 + row.indentLevel() * INDENT_PER_LEVEL;
        label.setIcon(resourceRowIcon(row));
        label.setText(row.displayTitle());
        label.setCursor(Cursor.getDefaultCursor());
        label.setToolTipText(
            "<html><body style='width:280px;'>%s</body></html>".formatted(
                row.definition().description()));
        label.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, rowDivider),
                BorderFactory.createEmptyBorder(12, left, 12, 18)));
        if (isSelected) {
          label.setBackground(selectedBg);
          label.setForeground(Color.WHITE);
        } else {
          label.setBackground(unselectedBg);
          label.setForeground(unselectedFg);
        }
      } else if (value instanceof final DialogListEntry.DialogFolderRow folder) {
        final int left = 18 + folder.depth() * INDENT_PER_LEVEL;
        label.setIcon(folderIcon);
        label.setText(folderRowText(folder));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setToolTipText(null);
        label.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, rowDivider),
                BorderFactory.createEmptyBorder(12, left, 12, 18)));
        if (isSelected) {
          label.setBackground(selectedBg);
          label.setForeground(Color.WHITE);
        } else {
          label.setBackground(new Color(245, 248, 252));
          label.setForeground(new Color(55, 71, 79));
        }
      }
      return label;
    };
  }

  private static void applyFlyListLook(final JList<DialogListEntry> list) {
    list.setFont(list.getFont().deriveFont(18f));
    list.setFixedCellHeight(48);
    final Icon folderIcon = folderRowIcon();
    list.setCellRenderer(
        (jList, value, index, isSelected, cellHasFocus) -> {
          final JLabel cell = new JLabel();
          cell.setOpaque(true);
          cell.setHorizontalAlignment(SwingConstants.LEFT);
          cell.setVerticalAlignment(SwingConstants.CENTER);
          cell.setIconTextGap(8);
          if (value instanceof final DialogListEntry.DialogResourceRow row) {
            final int left = 14 + row.indentLevel() * INDENT_PER_LEVEL;
            cell.setIcon(resourceRowIcon(row));
            cell.setText(row.displayTitle());
            cell.setCursor(Cursor.getDefaultCursor());
            cell.setToolTipText(row.definition().description());
            cell.setBorder(BorderFactory.createEmptyBorder(10, left, 10, 14));
            cell.setFont(cell.getFont().deriveFont(Font.BOLD, 17f));
            if (isSelected) {
              cell.setBackground(new Color(25, 118, 210));
              cell.setForeground(Color.WHITE);
            } else {
              cell.setBackground(Color.WHITE);
              cell.setForeground(new Color(40, 50, 70));
            }
          } else if (value instanceof final DialogListEntry.DialogFolderRow folder) {
            final int left = 14 + folder.depth() * INDENT_PER_LEVEL;
            cell.setIcon(folderIcon);
            cell.setText(folderRowText(folder));
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cell.setToolTipText(null);
            cell.setBorder(BorderFactory.createEmptyBorder(10, left, 10, 14));
            cell.setFont(cell.getFont().deriveFont(Font.BOLD, 17f));
            if (isSelected) {
              cell.setBackground(new Color(25, 118, 210));
              cell.setForeground(Color.WHITE);
            } else {
              cell.setBackground(new Color(245, 248, 252));
              cell.setForeground(new Color(55, 71, 79));
            }
          }
          return cell;
        });
  }

  private static void bindFolderRowSingleClick(
      final JList<DialogListEntry> list,
      final Consumer<DialogListEntry.DialogFolderRow> onFolderRowSingleClick) {
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(final MouseEvent e) {
            if (e.getClickCount() != 1) {
              return;
            }
            final int index = list.locationToIndex(e.getPoint());
            if (index < 0) {
              return;
            }
            final Rectangle cell = list.getCellBounds(index, index);
            if (cell != null && !cell.contains(e.getPoint())) {
              return;
            }
            final DialogListEntry entry = list.getModel().getElementAt(index);
            if (entry instanceof final DialogListEntry.DialogFolderRow folder) {
              onFolderRowSingleClick.accept(folder);
            }
          }
        });
  }

  private static void bindDoubleClickToStart(
      final JList<DialogListEntry> list, final Consumer<DialogDefinition> onStart) {
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(final MouseEvent e) {
            if (e.getClickCount() != 2) {
              return;
            }
            final int index = list.locationToIndex(e.getPoint());
            if (index < 0) {
              return;
            }
            final Rectangle cell = list.getCellBounds(index, index);
            if (cell != null && !cell.contains(e.getPoint())) {
              return;
            }
            final DialogListEntry entry = list.getModel().getElementAt(index);
            if (entry instanceof final DialogListEntry.DialogResourceRow row) {
              onStart.accept(row.definition());
            }
          }
        });
  }

  public enum Appearance {
    DIALOG,
    FLY_GAME
  }

  public static final class Result {

    private final JPanel panel;
    private final BusyOverlayPanel busyOverlay;
    private final List<JComponent> busyControlledComponents = new ArrayList<>();
    private final List<AvailabilityBinding> availabilityBindings = new ArrayList<>();
    private JList<DialogListEntry> list;
    private boolean busy;

    private Result(final JPanel panel, final BusyOverlayPanel busyOverlay) {
      this.panel = panel;
      this.busyOverlay = busyOverlay;
    }

    public JPanel panel() {
      return this.panel;
    }

    public JList<DialogListEntry> list() {
      return this.list;
    }

    public void setBusy(final boolean busy) {
      this.busy = busy;
      for (final Component component : this.busyControlledComponents) {
        component.setEnabled(!busy);
      }
      this.refreshAvailabilityBindings();
      this.panel.setCursor(
          busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
      this.busyOverlay.setBusy(busy);
    }

    private void setList(final JList<DialogListEntry> list) {
      this.list = list;
      this.addBusyControlled(list);
    }

    private void addBusyControlled(final JComponent component) {
      this.busyControlledComponents.add(component);
    }

    private void addAvailabilityControlled(
        final JComponent component,
        final BooleanSupplier enabledWhenIdle) {
      this.availabilityBindings.add(new AvailabilityBinding(component, enabledWhenIdle));
      this.refreshAvailabilityBindings();
    }

    private void refreshAvailabilityBindings() {
      this.availabilityBindings.forEach(binding -> binding.component()
          .setEnabled(!this.busy && binding.enabledWhenIdle().getAsBoolean()));
    }
  }

  private record AvailabilityBinding(
      JComponent component,
      BooleanSupplier enabledWhenIdle) {
  }

  private enum ResourceGlyph {
    DOT,
    SYNC,
    FOLDER
  }

  private static final class ResourceSourceIcon implements Icon {

    private static final int SIZE = 18;
    private final Color outline;
    private final Color accent;
    private final ResourceGlyph glyph;

    private ResourceSourceIcon(
        final Color outline, final Color accent, final ResourceGlyph glyph) {
      this.outline = requireNonNull(outline, "outline must not be null");
      this.accent = requireNonNull(accent, "accent must not be null");
      this.glyph = requireNonNull(glyph, "glyph must not be null");
    }

    @Override
    public int getIconWidth() {
      return SIZE;
    }

    @Override
    public int getIconHeight() {
      return SIZE;
    }

    @Override
    public void paintIcon(final Component component, final Graphics graphics, final int x,
                          final int y) {
      final Graphics2D g2 = (Graphics2D) graphics.create();
      try {
        ImageResourceLoader.applyHighQualityDrawingHints(g2);
        g2.translate(x, y);
        this.paintDocument(g2);
        this.paintGlyph(g2);
      } finally {
        g2.dispose();
      }
    }

    private void paintDocument(final Graphics2D g2) {
      g2.setColor(new Color(255, 255, 255, 230));
      g2.fillRoundRect(3, 1, 11, 15, 3, 3);
      g2.setColor(this.outline);
      g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawRoundRect(3, 1, 11, 15, 3, 3);
      g2.drawLine(11, 1, 14, 4);
      g2.drawLine(14, 4, 14, 15);
    }

    private void paintGlyph(final Graphics2D g2) {
      g2.setColor(this.accent);
      switch (this.glyph) {
        case DOT -> g2.fillOval(10, 10, 6, 6);
        case SYNC -> this.paintSyncGlyph(g2);
        case FOLDER -> this.paintFolderGlyph(g2);
      }
    }

    private void paintSyncGlyph(final Graphics2D g2) {
      g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawArc(8, 9, 8, 7, 40, 210);
      g2.drawArc(8, 9, 8, 7, 220, 210);
      g2.fillPolygon(new int[] {14, 17, 15}, new int[] {8, 9, 11}, 3);
      g2.fillPolygon(new int[] {10, 7, 9}, new int[] {17, 16, 14}, 3);
    }

    private void paintFolderGlyph(final Graphics2D g2) {
      g2.fillRoundRect(8, 11, 9, 5, 2, 2);
      g2.fillRect(9, 9, 4, 3);
    }
  }

  private static final class BusyOverlayPanel extends JPanel {

    private static final long SPIN_PERIOD_NS = 1_200_000_000L;
    private static final int ANIMATION_PERIOD_MS = 16;
    private final Timer animationTimer = new Timer(ANIMATION_PERIOD_MS, event -> this.repaint());
    private long startedAtNs;

    private BusyOverlayPanel() {
      this.setOpaque(false);
      this.animationTimer.setRepeats(true);
    }

    private void setBusy(final boolean busy) {
      this.setVisible(busy);
      if (busy) {
        this.startedAtNs = System.nanoTime();
        this.animationTimer.start();
      } else {
        this.animationTimer.stop();
      }
      this.repaint();
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
      super.paintComponent(graphics);
      if (!this.isVisible()) {
        return;
      }
      final Graphics2D g2 = (Graphics2D) graphics.create();
      try {
        this.paintBusyOverlay(g2);
      } finally {
        g2.dispose();
      }
    }

    private void paintBusyOverlay(final Graphics2D g2) {
      final int w = this.getWidth();
      final int h = this.getHeight();
      g2.setColor(new Color(255, 255, 255, 170));
      g2.fillRect(0, 0, w, h);
      if (w < 12 || h < 12) {
        return;
      }

      final int diameterRaw = Math.max(44, Math.min(w, h) / 9);
      final int diameter = diameterRaw - (diameterRaw & 1);
      final long elapsedNs = System.nanoTime() - this.startedAtNs;
      final long phaseNs = Math.floorMod(elapsedNs, SPIN_PERIOD_NS);
      final double angleRad = phaseNs * (Math.PI * 2.0d / SPIN_PERIOD_NS);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.translate(w * 0.5d, h * 0.5d);
      g2.rotate(angleRad);
      g2.setStroke(
          new BasicStroke(Math.max(3f, diameter / 14f), BasicStroke.CAP_ROUND,
              BasicStroke.JOIN_ROUND));
      g2.setColor(new Color(200, 200, 200));
      g2.drawOval(-diameter / 2, -diameter / 2, diameter, diameter);
      g2.setColor(new Color(25, 118, 210));
      g2.drawArc(-diameter / 2, -diameter / 2, diameter, diameter, 90, 270);
    }
  }
}
