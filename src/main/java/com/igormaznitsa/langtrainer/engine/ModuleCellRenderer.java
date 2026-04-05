package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

public final class ModuleCellRenderer extends JPanel
    implements ListCellRenderer<AbstractLangTrainerModule> {

  private final JLabel iconLabel;
  private final JLabel nameLabel;

  public ModuleCellRenderer() {
    super(new BorderLayout(8, 8));
    this.iconLabel = new JLabel();
    this.iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
    this.nameLabel = new JLabel();
    this.nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
    this.nameLabel.setVerticalAlignment(SwingConstants.CENTER);
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    add(this.iconLabel, BorderLayout.CENTER);
    add(this.nameLabel, BorderLayout.SOUTH);
    setOpaque(true);
  }

  @Override
  public Component getListCellRendererComponent(
      final JList<? extends AbstractLangTrainerModule> list,
      final AbstractLangTrainerModule value,
      final int index,
      final boolean isSelected,
      final boolean cellHasFocus) {
    final Color background = isSelected ? new Color(204, 230, 255) : new Color(245, 245, 245);
    final Color borderColor = isSelected ? new Color(70, 130, 180) : new Color(210, 210, 210);
    this.iconLabel.setIcon(value.getImage());
    this.nameLabel.setText(value.getName());
    this.nameLabel.setToolTipText(value.getDescription());
    setBackground(background);
    setBorder(BorderFactory.createLineBorder(borderColor, 2, true));
    return this;
  }
}
