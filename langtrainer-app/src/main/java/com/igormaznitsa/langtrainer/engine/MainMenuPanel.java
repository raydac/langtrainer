package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MouseInputAdapter;

public final class MainMenuPanel extends JPanel {

  private static final Icon HELP_ICON =
      ImageResourceLoader.loadIcon("/images/help-question.svg", 84, 84);
  private static final Icon HELP_HOVER_ICON =
      ImageResourceLoader.loadIcon("/images/help-question-hover.svg", 84, 84);
  private static final Icon HELP_PRESSED_ICON =
      ImageResourceLoader.loadIcon("/images/help-question-pressed.svg", 84, 84);
  private final JList<AbstractLangTrainerModule> modulesList;

  public MainMenuPanel(
      final List<AbstractLangTrainerModule> modules,
      final Consumer<AbstractLangTrainerModule> onModuleActivate,
      final Runnable onHelpRequested) {
    super(new BorderLayout());
    final Color menuBg = new Color(248, 250, 252);
    this.setBackground(menuBg);
    this.setOpaque(true);
    final DefaultListModel<AbstractLangTrainerModule> model = new DefaultListModel<>();
    modules.forEach(model::addElement);
    this.modulesList = new JList<>(model);
    this.modulesList.setBackground(menuBg);
    this.modulesList.setOpaque(true);
    this.modulesList.setSelectionBackground(menuBg);
    this.modulesList.setSelectionForeground(new Color(28, 38, 58));
    this.modulesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.modulesList.setCellRenderer(new ModuleCellRenderer());
    this.modulesList.setVisibleRowCount(-1);
    this.modulesList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    this.modulesList.setFixedCellWidth(228);
    this.modulesList.setFixedCellHeight(200);
    this.modulesList.setSelectedIndex(0);
    final MouseInputAdapter hoverPress = new MouseInputAdapter() {
      private void syncHover(final Point p) {
        final int idx = indexAtPoint(MainMenuPanel.this.modulesList, p);
        final Object prev = MainMenuPanel.this.modulesList.getClientProperty(
            ModuleCellRenderer.PROP_MODULE_HOVER);
        final int prevI = prev instanceof Integer ? (Integer) prev : -1;
        if (idx != prevI) {
          MainMenuPanel.this.modulesList.putClientProperty(
              ModuleCellRenderer.PROP_MODULE_HOVER, idx < 0 ? null : idx);
          MainMenuPanel.this.modulesList.repaint();
        }
      }

      @Override
      public void mouseMoved(final MouseEvent event) {
        syncHover(event.getPoint());
      }

      @Override
      public void mouseDragged(final MouseEvent event) {
        syncHover(event.getPoint());
      }

      @Override
      public void mouseExited(final MouseEvent event) {
        MainMenuPanel.this.modulesList.putClientProperty(ModuleCellRenderer.PROP_MODULE_HOVER,
            null);
        MainMenuPanel.this.modulesList.putClientProperty(ModuleCellRenderer.PROP_MODULE_PRESS,
            null);
        MainMenuPanel.this.modulesList.repaint();
      }

      @Override
      public void mouseEntered(final MouseEvent event) {
        syncHover(event.getPoint());
      }

      @Override
      public void mousePressed(final MouseEvent event) {
        final int idx = indexAtPoint(MainMenuPanel.this.modulesList, event.getPoint());
        MainMenuPanel.this.modulesList.putClientProperty(
            ModuleCellRenderer.PROP_MODULE_PRESS, idx < 0 ? null : idx);
        MainMenuPanel.this.modulesList.repaint();
      }

      @Override
      public void mouseReleased(final MouseEvent event) {
        MainMenuPanel.this.modulesList.putClientProperty(ModuleCellRenderer.PROP_MODULE_PRESS,
            null);
        MainMenuPanel.this.modulesList.repaint();
      }

      @Override
      public void mouseClicked(final MouseEvent event) {
        final int index = indexAtPoint(MainMenuPanel.this.modulesList, event.getPoint());
        if (index >= 0) {
          MainMenuPanel.this.modulesList.setSelectedIndex(index);
          MainMenuPanel.this.activateSelected(onModuleActivate);
        }
      }
    };
    this.modulesList.addMouseListener(hoverPress);
    this.modulesList.addMouseMotionListener(hoverPress);
    this.modulesList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate");
    this.modulesList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "activate");
    this.modulesList.getActionMap().put("activate", new AbstractAction() {
      @Override
      public void actionPerformed(final java.awt.event.ActionEvent event) {
        MainMenuPanel.this.activateSelected(onModuleActivate);
      }
    });
    final JScrollPane scrollPane = new JScrollPane(this.modulesList);
    scrollPane.getViewport().setBackground(menuBg);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
    scrollPane.setPreferredSize(new Dimension(720, 440));
    this.add(scrollPane, BorderLayout.CENTER);
    this.add(this.makeHelpButtonPanel(onHelpRequested), BorderLayout.SOUTH);
  }

  private JPanel makeHelpButtonPanel(final Runnable onHelpRequested) {
    final JPanel panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 20, 14));
    panel.setOpaque(false);
    panel.setBorder(new EmptyBorder(0, 0, 10, 10));
    panel.add(this.makeHelpButton(onHelpRequested));
    return panel;
  }

  private JButton makeHelpButton(final Runnable onHelpRequested) {
    final JButton button = new JButton();
    button.setFocusPainted(false);
    button.setBackground(new Color(0, 0, 0, 0));
    button.setOpaque(false);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setBorder(new EmptyBorder(0, 0, 0, 0));
    button.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    button.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
    button.setIcon(HELP_ICON);
    button.setRolloverEnabled(true);
    button.setRolloverIcon(HELP_HOVER_ICON);
    button.setPressedIcon(HELP_PRESSED_ICON);
    final Dimension size = new Dimension(98, 98);
    button.setPreferredSize(size);
    button.setMinimumSize(size);
    button.setMaximumSize(size);
    button.addActionListener(event -> onHelpRequested.run());
    return button;
  }

  public void focusList() {
    this.modulesList.requestFocusInWindow();
  }

  private static int indexAtPoint(final JList<?> list, final Point p) {
    final int i = list.locationToIndex(p);
    if (i < 0) {
      return -1;
    }
    final Rectangle r = list.getCellBounds(i, i);
    return r != null && r.contains(p) ? i : -1;
  }

  private void activateSelected(final Consumer<AbstractLangTrainerModule> onModuleActivate) {
    final AbstractLangTrainerModule selected = this.modulesList.getSelectedValue();
    if (selected != null) {
      onModuleActivate.accept(selected);
    }
  }
}
