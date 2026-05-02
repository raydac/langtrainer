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

/**
 * Raster brick chips for pool/build/history (rounded rect + label).
 */
final class BrickImageRenderer {

  static final int BRICK_PAD_X = 12;
  static final int BRICK_PAD_Y = 8;
  static final int BRICK_CORNER_ARC = 14;
  static final int BRICK_FLOW_H_GAP = 8;
  private static final Color BRICK_STROKE = new Color(70, 100, 130);

  private BrickImageRenderer() {
  }

  static int measureBrickWidthPx(final String word, final Font font) {
    final BufferedImage scratch =
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g0 = scratch.createGraphics();
    g0.setFont(font);
    final FontMetrics fm = g0.getFontMetrics();
    final int w = fm.stringWidth(word) + 2 * BRICK_PAD_X + 4;
    g0.dispose();
    return w;
  }

  static int measureBrickHeightPx(final Font font) {
    final BufferedImage scratch =
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g0 = scratch.createGraphics();
    g0.setFont(font);
    final int h = g0.getFontMetrics().getHeight() + 2 * BRICK_PAD_Y + 4;
    g0.dispose();
    return h;
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

  static ImageIcon renderRowIcon(final List<String> words, final Color fill, final Font font) {
    if (words.isEmpty()) {
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
    totalW += BRICK_FLOW_H_GAP * (words.size() - 1);
    final BufferedImage row =
        new BufferedImage(totalW, maxH, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g2 = row.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int x = 0;
    for (final ImageIcon ic : icons) {
      ic.paintIcon(null, g2, x, (maxH - ic.getIconHeight()) / 2);
      x += ic.getIconWidth() + BRICK_FLOW_H_GAP;
    }
    g2.dispose();
    return new ImageIcon(row);
  }
}
