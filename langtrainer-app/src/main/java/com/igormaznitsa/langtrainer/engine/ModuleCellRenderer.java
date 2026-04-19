package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

public final class ModuleCellRenderer extends JPanel
    implements ListCellRenderer<AbstractLangTrainerModule> {

  public static final String PROP_MODULE_HOVER = "langtrainer.moduleHoverIndex";
  public static final String PROP_MODULE_PRESS = "langtrainer.modulePressIndex";

  private final JLabel iconLabel;
  private final JLabel nameLabel;

  public ModuleCellRenderer() {
    super(new BorderLayout(10, 10));
    this.iconLabel = new JLabel();
    this.iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
    this.nameLabel = new JLabel();
    this.nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
    this.nameLabel.setVerticalAlignment(SwingConstants.TOP);
    this.nameLabel.setFont(this.nameLabel.getFont().deriveFont(Font.BOLD, 19f));
    add(this.iconLabel, BorderLayout.CENTER);
    add(this.nameLabel, BorderLayout.SOUTH);
    setOpaque(false);
    this.iconLabel.setOpaque(false);
    this.nameLabel.setOpaque(false);
  }

  private static int clientIndex(final JList<?> list, final String key) {
    final Object v = list.getClientProperty(key);
    if (v instanceof Integer i) {
      return i;
    }
    return -1;
  }

  @Override
  public Component getListCellRendererComponent(
      final JList<? extends AbstractLangTrainerModule> list,
      final AbstractLangTrainerModule value,
      final int index,
      final boolean isSelected,
      final boolean cellHasFocus) {
    final Color nameFg = isSelected ? new Color(12, 72, 168) : new Color(28, 38, 58);
    this.iconLabel.setIcon(value.getImage());
    this.nameLabel.setText(value.getName());
    this.nameLabel.setForeground(nameFg);
    this.nameLabel.setToolTipText(value.getDescription());
    final int hover = clientIndex(list, PROP_MODULE_HOVER);
    final int press = clientIndex(list, PROP_MODULE_PRESS);
    int top = 10;
    int left = 12;
    int bottom = 14;
    int right = 12;
    if (index == press && press >= 0) {
      top += 2;
      left += 2;
      bottom -= 2;
      right -= 2;
    } else if (index == hover && hover >= 0) {
      top -= 2;
      left -= 2;
      bottom += 2;
      right += 2;
    }
    setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
    return this;
  }
}
