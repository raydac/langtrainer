package com.igormaznitsa.langtrainer.engine;

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
    final JButton start = new JButton(startButtonLabel);
    start.setFont(start.getFont().deriveFont(Font.BOLD, 18f));
    start.setForeground(Color.WHITE);
    start.setBackground(new Color(46, 125, 50));
    start.setOpaque(true);
    start.setContentAreaFilled(true);
    start.setFocusPainted(false);
    start.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(27, 94, 32), 2, true),
            BorderFactory.createEmptyBorder(14, 32, 14, 32)));
    start.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    start.addActionListener(
        event -> {
          final DialogListEntry selected = list.getSelectedValue();
          if (selected instanceof final DialogListEntry.DialogResourceRow row) {
            onStart.accept(row.definition());
          }
        });

    final JButton openFile = new JButton(openFromFileLabel);
    openFile.setFont(openFile.getFont().deriveFont(Font.BOLD, 18f));
    openFile.setForeground(Color.WHITE);
    openFile.setBackground(new Color(25, 118, 210));
    openFile.setOpaque(true);
    openFile.setContentAreaFilled(true);
    openFile.setFocusPainted(false);
    openFile.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(13, 71, 161), 2, true),
            BorderFactory.createEmptyBorder(14, 28, 14, 28)));
    openFile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    openFile.addActionListener(event -> onOpenFromFile.accept(list));
    result.addBusyControlled(openFile);
    result.addBusyControlled(start);

    final JPanel southWrap = new JPanel(new BorderLayout());
    southWrap.setBackground(panelBg);
    southWrap.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
    buttonRow.setOpaque(false);
    if (onLoadExternals != null) {
      buttonRow.add(makeDialogLoadExternalsButton(onLoadExternals, result));
    }
    buttonRow.add(openFile);
    buttonRow.add(start);
    southWrap.add(buttonRow, BorderLayout.CENTER);
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
    final JPanel south = new JPanel();
    south.setOpaque(false);
    if (onLoadExternals != null) {
      south.add(makeFlyLoadExternalsButton(onLoadExternals, result));
    }
    final JButton open = new JButton(openFromFileLabel);
    styleFlyPrimaryButton(open, new Color(25, 118, 210));
    open.addActionListener(e -> onOpenFromFile.accept(list));
    final JButton start = new JButton(startButtonLabel);
    styleFlyPrimaryButton(start, new Color(46, 125, 50));
    start.addActionListener(
        e -> {
          final DialogListEntry entry = list.getSelectedValue();
          if (entry instanceof final DialogListEntry.DialogResourceRow row) {
            onStart.accept(row.definition());
          }
        });
    south.add(open);
    south.add(start);
    result.addBusyControlled(open);
    result.addBusyControlled(start);
    panel.add(south, BorderLayout.SOUTH);
  }

  private static JButton makeDialogLoadExternalsButton(
      final Runnable onLoadExternals, final Result result) {
    final JButton button = new JButton("Sync GitHub");
    button.setFont(button.getFont().deriveFont(Font.BOLD, 18f));
    button.setForeground(Color.WHITE);
    button.setBackground(new Color(123, 31, 162));
    button.setOpaque(true);
    button.setContentAreaFilled(true);
    button.setFocusPainted(false);
    button.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(74, 20, 140), 2, true),
            BorderFactory.createEmptyBorder(14, 28, 14, 28)));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(event -> onLoadExternals.run());
    result.addBusyControlled(button);
    return button;
  }

  private static JButton makeFlyLoadExternalsButton(
      final Runnable onLoadExternals, final Result result) {
    final JButton button = new JButton("Sync GitHub");
    styleFlyPrimaryButton(button, new Color(123, 31, 162));
    button.addActionListener(event -> onLoadExternals.run());
    result.addBusyControlled(button);
    return button;
  }

  private static void styleFlyPrimaryButton(final JButton button, final Color bg) {
    button.setFont(button.getFont().deriveFont(Font.BOLD, 16f));
    button.setForeground(Color.WHITE);
    button.setBackground(bg);
    button.setOpaque(true);
    button.setFocusPainted(false);
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
        final String rowTitle =
            (row.fromExternalFile() ? "* " : "") + row.definition().menuName();
        label.setIcon(null);
        label.setText(rowTitle);
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
        final String chevron = folder.expanded() ? "\u25bc " : "\u25b6 ";
        label.setText(chevron + folder.title());
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
            final String rowTitle =
                (row.fromExternalFile() ? "* " : "") + row.definition().menuName();
            cell.setIcon(null);
            cell.setText(rowTitle);
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
            final String chevron = folder.expanded() ? "\u25bc " : "\u25b6 ";
            cell.setText(chevron + folder.title());
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
    private JList<DialogListEntry> list;

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
      for (final Component component : this.busyControlledComponents) {
        component.setEnabled(!busy);
      }
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
