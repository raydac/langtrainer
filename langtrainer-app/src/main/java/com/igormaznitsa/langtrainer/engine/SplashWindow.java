package com.igormaznitsa.langtrainer.engine;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class SplashWindow {

  private final JWindow window;

  public SplashWindow(final Window owner, final String appVersion) {
    this.window = new JWindow(owner);
    final JLabel image = new JLabel(
        new ImageIcon(ImageResourceLoader.loadImage("/images/splash.svg", 640, 360)));
    image.setLayout(new BorderLayout());
    final JLabel versionLabel = new JLabel("   Version: " + appVersion);
    versionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 12));
    versionLabel.setForeground(new Color(0, 0, 0, 230));
    versionLabel.setFont(versionLabel.getFont().deriveFont(Font.BOLD, 14.0f));
    image.add(versionLabel, BorderLayout.SOUTH);
    this.window.getContentPane().setLayout(new BorderLayout());
    this.window.getContentPane().add(image, BorderLayout.CENTER);
    this.window.pack();
    this.window.setLocationRelativeTo(null);
    this.window.setAlwaysOnTop(true);
  }

  public void showForMillis(
      final int millis,
      final Runnable showOwnerWindow,
      final Runnable onDone) {
    this.window.setVisible(true);
    this.window.toFront();
    SwingUtilities.invokeLater(showOwnerWindow);
    final Timer timer = new Timer(millis, event -> {
      this.window.setAlwaysOnTop(false);
      this.window.setVisible(false);
      this.window.dispose();
      onDone.run();
    });
    timer.setRepeats(false);
    timer.start();
  }
}
