package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

public final class MainMenuPanel extends JPanel {

  private final JList<AbstractLangTrainerModule> modulesList;

  public MainMenuPanel(
      final List<AbstractLangTrainerModule> modules,
      final Consumer<AbstractLangTrainerModule> onModuleActivate) {
    super(new BorderLayout());
    final DefaultListModel<AbstractLangTrainerModule> model = new DefaultListModel<>();
    modules.forEach(model::addElement);
    this.modulesList = new JList<>(model);
    this.modulesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.modulesList.setCellRenderer(new ModuleCellRenderer());
    this.modulesList.setVisibleRowCount(-1);
    this.modulesList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    this.modulesList.setFixedCellWidth(180);
    this.modulesList.setFixedCellHeight(180);
    this.modulesList.setSelectedIndex(0);
    this.modulesList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent event) {
        final int index = modulesList.locationToIndex(event.getPoint());
        if (index >= 0) {
          modulesList.setSelectedIndex(index);
          activateSelected(onModuleActivate);
        }
      }
    });
    this.modulesList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate");
    this.modulesList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "activate");
    this.modulesList.getActionMap().put("activate", new AbstractAction() {
      @Override
      public void actionPerformed(final java.awt.event.ActionEvent event) {
        activateSelected(onModuleActivate);
      }
    });
    final JScrollPane scrollPane = new JScrollPane(this.modulesList);
    scrollPane.setPreferredSize(new Dimension(640, 420));
    add(scrollPane, BorderLayout.CENTER);
  }

  public void focusList() {
    this.modulesList.requestFocusInWindow();
  }

  private void activateSelected(final Consumer<AbstractLangTrainerModule> onModuleActivate) {
    final AbstractLangTrainerModule selected = this.modulesList.getSelectedValue();
    if (selected != null) {
      onModuleActivate.accept(selected);
    }
  }
}
