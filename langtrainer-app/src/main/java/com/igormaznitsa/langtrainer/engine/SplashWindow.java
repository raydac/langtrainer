package com.igormaznitsa.langtrainer.engine;

import java.awt.BorderLayout;
import java.awt.Window;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.Timer;

public final class SplashWindow {

  private final JWindow window;

  public SplashWindow(final Window owner) {
    this.window = new JWindow(owner);
    final JLabel image = new JLabel(
        new ImageIcon(ImageResourceLoader.loadImage("/images/splash.svg", 640, 360)));
    this.window.getContentPane().setLayout(new BorderLayout());
    this.window.getContentPane().add(image, BorderLayout.CENTER);
    this.window.pack();
    this.window.setLocationRelativeTo(null);
  }

  public void showForMillis(final int millis, final Runnable onDone) {
    this.window.setVisible(true);
    final Timer timer = new Timer(millis, event -> {
      this.window.setVisible(false);
      this.window.dispose();
      onDone.run();
    });
    timer.setRepeats(false);
    timer.start();
  }
}
