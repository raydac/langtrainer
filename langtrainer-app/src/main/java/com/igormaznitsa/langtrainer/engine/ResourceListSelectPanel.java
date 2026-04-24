package com.igormaznitsa.langtrainer.engine;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

/**
 * Resource selection for modules that list {@link DialogListEntry} items, open JSON from file, and
 * start a session. Double-clicking a row starts the same way as the primary start button.
 */
public final class ResourceListSelectPanel {

  private ResourceListSelectPanel() {
  }

  public static Result build(
      final DefaultListModel<DialogListEntry> model,
      final Appearance appearance,
      final String titleText,
      final String startButtonLabel,
      final String openFromFileLabel,
      final Consumer<DialogDefinition> onStart,
      final Consumer<JList<DialogListEntry>> onOpenFromFile) {
    final JPanel panel = new JPanel(new BorderLayout(12, 14));
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
    if (model.getSize() > 0) {
      list.setSelectedIndex(0);
    }
    bindDoubleClickToStart(list, onStart);

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
          onOpenFromFile);
    } else {
      addFlySouth(panel, list, startButtonLabel, openFromFileLabel, onStart, onOpenFromFile);
    }

    return new Result(panel, list);
  }

  private static void addDialogSouth(
      final JPanel panel,
      final Color panelBg,
      final JList<DialogListEntry> list,
      final String startButtonLabel,
      final String openFromFileLabel,
      final Consumer<DialogDefinition> onStart,
      final Consumer<JList<DialogListEntry>> onOpenFromFile) {
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
          if (selected != null) {
            onStart.accept(selected.definition());
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

    final JPanel southWrap = new JPanel(new BorderLayout());
    southWrap.setBackground(panelBg);
    southWrap.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
    buttonRow.setOpaque(false);
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
      final Consumer<JList<DialogListEntry>> onOpenFromFile) {
    final JPanel south = new JPanel();
    south.setOpaque(false);
    final JButton open = new JButton(openFromFileLabel);
    styleFlyPrimaryButton(open, new Color(25, 118, 210));
    open.addActionListener(e -> onOpenFromFile.accept(list));
    final JButton start = new JButton(startButtonLabel);
    styleFlyPrimaryButton(start, new Color(46, 125, 50));
    start.addActionListener(
        e -> {
          final DialogListEntry entry = list.getSelectedValue();
          if (entry != null) {
            onStart.accept(entry.definition());
          }
        });
    south.add(open);
    south.add(start);
    panel.add(south, BorderLayout.SOUTH);
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
    return (jList, value, index, isSelected, cellHasFocus) -> {
      final String rowTitle =
          (value.fromExternalFile() ? "* " : "") + value.definition().menuName();
      final JLabel label = new JLabel(rowTitle);
      label.setOpaque(true);
      label.setFont(label.getFont().deriveFont(Font.BOLD, 19f));
      label.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 1, 0, rowDivider),
              BorderFactory.createEmptyBorder(12, 18, 12, 18)));
      label.setToolTipText(
          "<html><body style='width:280px;'>%s</body></html>".formatted(
              value.definition().description()));
      if (isSelected) {
        label.setBackground(selectedBg);
        label.setForeground(Color.WHITE);
      } else {
        label.setBackground(unselectedBg);
        label.setForeground(unselectedFg);
      }
      return label;
    };
  }

  private static void applyFlyListLook(final JList<DialogListEntry> list) {
    list.setFont(list.getFont().deriveFont(18f));
    list.setFixedCellHeight(48);
    list.setCellRenderer(
        (jList, value, index, isSelected, cellHasFocus) -> {
          final String rowTitle =
              (value.fromExternalFile() ? "* " : "") + value.definition().menuName();
          final JLabel cell = new JLabel(rowTitle);
          cell.setOpaque(true);
          cell.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
          cell.setFont(cell.getFont().deriveFont(Font.BOLD, 17f));
          if (isSelected) {
            cell.setBackground(new Color(25, 118, 210));
            cell.setForeground(Color.WHITE);
          } else {
            cell.setBackground(Color.WHITE);
            cell.setForeground(new Color(40, 50, 70));
          }
          cell.setToolTipText(value.definition().description());
          return cell;
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
            onStart.accept(entry.definition());
          }
        });
  }

  public enum Appearance {
    DIALOG,
    FLY_GAME
  }

  public record Result(JPanel panel, JList<DialogListEntry> list) {
  }
}
