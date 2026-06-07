package com.igormaznitsa.langtrainer.ui;

import static java.util.Objects.requireNonNull;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class CompletionBanner {

  public static final String DEFAULT_TEXT = "COMPLETED";

  private static final Color BACKGROUND = new Color(0, 75, 38, 210);
  private static final Color FOREGROUND = new Color(255, 145, 30);
  private static final int HORIZONTAL_TEXT_PAD = 80;
  private static final int VERTICAL_TEXT_PAD = 32;
  private static final int CORNER_ARC = 28;

  private JDialog overlay;
  private Timer dismissTimer;

  public static void paintCentered(final Graphics2D g2, final JComponent component,
                                   final int outerGap) {
    paintCentered(g2, component.getWidth(), component.getHeight(), outerGap, DEFAULT_TEXT);
  }

  public static void paintCentered(
      final Graphics2D g2,
      final int width,
      final int height,
      final int outerGap,
      final String text) {
    requireNonNull(g2, "g2");
    requireNonNull(text, "text");

    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(Math.max(36f, width / 15f)));
    final FontMetrics fm = g2.getFontMetrics();
    final Rectangle banner = bannerBounds(width, height, outerGap, fm, text);

    g2.setColor(BACKGROUND);
    g2.fillRoundRect(banner.x, banner.y, banner.width, banner.height, CORNER_ARC, CORNER_ARC);
    g2.setColor(FOREGROUND);
    g2.drawString(
        text,
        banner.x + (banner.width - fm.stringWidth(text)) / 2,
        banner.y + (banner.height - fm.getHeight()) / 2 + fm.getAscent());
  }

  private static Rectangle bannerBounds(
      final int width,
      final int height,
      final int outerGap,
      final FontMetrics fm,
      final String text) {
    final int maxWidth = Math.max(1, width - outerGap * 2);
    final int bannerWidth =
        Math.min(maxWidth, Math.max(width * 3 / 4, fm.stringWidth(text) + HORIZONTAL_TEXT_PAD));
    final int bannerHeight = fm.getHeight() + VERTICAL_TEXT_PAD;
    return new Rectangle(
        Math.max(0, (width - bannerWidth) / 2),
        Math.max(0, (height - bannerHeight) / 2),
        Math.max(1, bannerWidth),
        Math.max(1, bannerHeight));
  }

  private static Dimension preferredOverlaySize(final Window owner, final String text) {
    final int ownerWidth = Math.max(1, owner.getWidth());
    final JLabel sample = new JLabel();
    sample.setFont(LangTrainerFonts.MONO_NL_BOLD.atPoints(Math.max(36f, ownerWidth / 15f)));
    final FontMetrics fm = sample.getFontMetrics(sample.getFont());
    return bannerBounds(ownerWidth, Math.max(1, owner.getHeight()), 32, fm, text).getSize();
  }

  private static Point centeredLocation(final Window owner, final Dimension dialogSize) {
    final Point ownerLocation = owner.getLocationOnScreen();
    final Dimension ownerSize = owner.getSize();
    return new Point(
        ownerLocation.x + Math.max(0, (ownerSize.width - dialogSize.width) / 2),
        ownerLocation.y + Math.max(0, (ownerSize.height - dialogSize.height) / 2));
  }

  private static void applyTransparentBackground(final Window dialog) {
    try {
      dialog.setBackground(new Color(0, 0, 0, 0));
    } catch (final UnsupportedOperationException ignored) {
      dialog.setBackground(BACKGROUND);
    }
  }

  public void dismiss() {
    if (this.dismissTimer != null) {
      this.dismissTimer.stop();
      this.dismissTimer = null;
    }
    if (this.overlay != null) {
      this.overlay.dispose();
      this.overlay = null;
    }
  }

  public void show(final Window owner, final Runnable afterDismiss) {
    this.show(owner, DEFAULT_TEXT, 10_000, afterDismiss);
  }

  public void show(
      final Window owner,
      final String text,
      final int dismissDelayMs,
      final Runnable afterDismiss) {
    requireNonNull(owner, "owner");
    requireNonNull(text, "text");
    this.dismiss();

    final JDialog dialog = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
    this.overlay = dialog;
    dialog.setUndecorated(true);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    applyTransparentBackground(dialog);
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(final WindowEvent event) {
        CompletionBanner.this.stopDismissTimer();
        if (CompletionBanner.this.overlay == dialog) {
          CompletionBanner.this.overlay = null;
        }
      }
    });

    final Runnable closeAndFinish = () -> {
      this.dismiss();
      if (afterDismiss != null) {
        SwingUtilities.invokeLater(afterDismiss);
      }
    };
    final JComponent banner = new BannerComponent(text, preferredOverlaySize(owner, text));
    banner.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent event) {
        closeAndFinish.run();
      }
    });
    dialog.setContentPane(banner);
    dialog.pack();
    dialog.setLocation(centeredLocation(owner, dialog.getSize()));

    this.dismissTimer = new Timer(Math.max(1, dismissDelayMs), event -> closeAndFinish.run());
    this.dismissTimer.setRepeats(false);
    this.dismissTimer.start();

    SwingUtilities.invokeLater(() -> dialog.setVisible(true));
  }

  private void stopDismissTimer() {
    if (this.dismissTimer != null) {
      this.dismissTimer.stop();
      this.dismissTimer = null;
    }
  }

  private static final class BannerComponent extends JComponent {

    private final String text;
    private final Dimension preferredSize;

    private BannerComponent(final String text, final Dimension preferredSize) {
      this.text = requireNonNull(text, "text");
      this.preferredSize = new Dimension(requireNonNull(preferredSize, "preferredSize"));
      this.setOpaque(false);
      this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(this.preferredSize);
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
      super.paintComponent(graphics);
      final Graphics2D g2 = (Graphics2D) graphics.create();
      try {
        CompletionBanner.paintCentered(g2, this.getWidth(), this.getHeight(), 0, this.text);
      } finally {
        g2.dispose();
      }
    }
  }
}
