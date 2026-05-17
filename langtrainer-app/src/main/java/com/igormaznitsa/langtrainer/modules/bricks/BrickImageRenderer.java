package com.igormaznitsa.langtrainer.modules.bricks;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;

final class BrickImageRenderer {

  static final int BRICK_PAD_X = 12;
  static final int BRICK_PAD_Y = 8;
  static final int BRICK_CORNER_ARC = 14;
  static final int BRICK_FLOW_H_GAP = 8;
  static final Color SUFFIX_BRICK_FILL = new Color(0, 229, 255);
  private static final Color BRICK_STROKE = new Color(70, 100, 130);

  private BrickImageRenderer() {
  }

  static int measureBrickWidthPx(final String word, final Font font) {
    final BufferedImage scratch =
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D gfx = scratch.createGraphics();
    try {
      gfx.setFont(font);
      final FontMetrics fm = gfx.getFontMetrics();
      return fm.stringWidth(word) + 2 * BRICK_PAD_X + 4;
    } finally {
      gfx.dispose();
    }
  }

  static int measureBrickHeightPx(final Font font) {
    final BufferedImage scratch =
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D gfx = scratch.createGraphics();
    try {
      gfx.setFont(font);
      return gfx.getFontMetrics().getHeight() + 2 * BRICK_PAD_Y + 4;
    } finally {
      gfx.dispose();
    }
  }

  static BufferedImage renderBrickImage(final String word, final Color fill, final Font font) {
    final BufferedImage scratch =
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g0 = scratch.createGraphics();
    g0.setFont(font);
    final FontMetrics fm = g0.getFontMetrics();
    g0.dispose();
    final int textW = fm.stringWidth(word);
    final int textH = fm.getHeight();
    final int w = textW + 2 * BRICK_PAD_X + 4;
    final int h = textH + 2 * BRICK_PAD_Y + 4;
    final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g2 = img.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setColor(fill);
    g2.fillRoundRect(1, 1, w - 2, h - 2, BRICK_CORNER_ARC, BRICK_CORNER_ARC);
    g2.setColor(BRICK_STROKE);
    g2.drawRoundRect(1, 1, w - 2, h - 2, BRICK_CORNER_ARC, BRICK_CORNER_ARC);
    g2.setFont(font);
    g2.setColor(Color.BLACK);
    final int baselineY = BRICK_PAD_Y + fm.getAscent();
    g2.drawString(word, BRICK_PAD_X + 2, baselineY);
    g2.dispose();
    return img;
  }

  static ImageIcon renderIcon(final String word, final Color fill, final Font font) {
    return new ImageIcon(renderBrickImage(word, fill, font));
  }

  static ImageIcon renderRowIcon(
      final List<String> words, final String fixedEndSuffix, final Color fill, final Font font) {
    if (words.isEmpty() && (fixedEndSuffix == null || fixedEndSuffix.isEmpty())) {
      return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    }
    int totalW = 0;
    int maxH = 0;
    final List<ImageIcon> icons = new ArrayList<>(words.size());
    for (final String w : words) {
      final ImageIcon ic = renderIcon(w, fill, font);
      icons.add(ic);
      totalW += ic.getIconWidth();
      maxH = Math.max(maxH, ic.getIconHeight());
    }
    if (!icons.isEmpty()) {
      totalW += BRICK_FLOW_H_GAP * (icons.size() - 1);
    }
    ImageIcon suffixIcon = null;
    if (fixedEndSuffix != null && !fixedEndSuffix.isEmpty()) {
      if (!icons.isEmpty()) {
        totalW += BRICK_FLOW_H_GAP;
      }
      suffixIcon = renderIcon(fixedEndSuffix, SUFFIX_BRICK_FILL, font);
      totalW += suffixIcon.getIconWidth();
      maxH = Math.max(maxH, suffixIcon.getIconHeight());
    }
    final BufferedImage row =
        new BufferedImage(Math.max(1, totalW), Math.max(1, maxH), BufferedImage.TYPE_INT_ARGB);
    final Graphics2D gfx = row.createGraphics();
    try {
      gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      gfx.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      gfx.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      int x = 0;
      for (final ImageIcon ic : icons) {
        ic.paintIcon(null, gfx, x, (maxH - ic.getIconHeight()) / 2);
        x += ic.getIconWidth() + BRICK_FLOW_H_GAP;
      }
      if (suffixIcon != null) {
        suffixIcon.paintIcon(null, gfx, x, (maxH - suffixIcon.getIconHeight()) / 2);
      }
      return new ImageIcon(row);
    } finally {
      gfx.dispose();
    }
  }
}
