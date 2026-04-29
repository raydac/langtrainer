package com.igormaznitsa.langtrainer.ui;

import static javax.swing.text.StyleConstants.setAlignment;
import static javax.swing.text.StyleConstants.setBackground;
import static javax.swing.text.StyleConstants.setBold;
import static javax.swing.text.StyleConstants.setFontFamily;
import static javax.swing.text.StyleConstants.setFontSize;
import static javax.swing.text.StyleConstants.setForeground;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Centered modal: alternates two phrase faces every second for five seconds, with inverted colors
 * on each face (flashcard-style). Used by Dialog and Fly modules.
 */
public final class PhraseFlashBanner {

  public static final Color SHOW_ACTION_BUTTON_BG = new Color(0, 121, 107);
  public static final Color SHOW_ACTION_BUTTON_FG = Color.WHITE;
  public static final Color SHOW_ACTION_BUTTON_BORDER = new Color(0, 77, 64);

  private static final Color PHRASE_FLASH_LIGHT_BG = Color.WHITE;
  private static final Color PHRASE_FLASH_LIGHT_FG = Color.BLACK;
  private static final Color PHRASE_FLASH_DARK_BG = Color.BLACK;
  private static final Color PHRASE_FLASH_DARK_FG = Color.WHITE;
  private static final Color PHRASE_FLASH_BORDER = new Color(48, 48, 48);
  /**
   * Minimum / maximum phrase size in the flash banner (pt); scales with parent window height.
   */
  private static final float PHRASE_FONT_MIN_PT = 52f;
  private static final float PHRASE_FONT_MAX_PT = 102f;
  private static final float PHRASE_FONT_HEIGHT_FRACTION = 0.072f;

  private JDialog overlay;
  private Timer flipTimer;
  private Timer dismissTimer;

  public static String normalizeLineBreaksForDisplay(final String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static float phraseFontPointsForWindow(final int ownerHeightPx) {
    final float scaled = Math.max(1, ownerHeightPx) * PHRASE_FONT_HEIGHT_FRACTION;
    return Math.max(PHRASE_FONT_MIN_PT, Math.min(PHRASE_FONT_MAX_PT, scaled));
  }

  public void dismiss() {
    if (this.flipTimer != null) {
      this.flipTimer.stop();
      this.flipTimer = null;
    }
    if (this.dismissTimer != null) {
      this.dismissTimer.stop();
      this.dismissTimer = null;
    }
    if (this.overlay != null) {
      this.overlay.dispose();
      this.overlay = null;
    }
  }

  /**
   * Shows a flashcard-style phrase banner. Dismisses any previous banner from this controller first.
   *
   * @param owner        parent window; must not be null
   * @param afterDismiss optional callback on the EDT after the overlay is disposed
   */
  public void show(
      final Window owner,
      final String expectedFaceText,
      final String partnerFaceText,
      final Runnable afterDismiss) {
    Objects.requireNonNull(owner, "owner");
    this.dismiss();

    final JDialog dialog = new JDialog(owner, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    this.overlay = dialog;
    dialog.setUndecorated(true);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(final WindowEvent e) {
        if (PhraseFlashBanner.this.flipTimer != null) {
          PhraseFlashBanner.this.flipTimer.stop();
          PhraseFlashBanner.this.flipTimer = null;
        }
        if (PhraseFlashBanner.this.dismissTimer != null) {
          PhraseFlashBanner.this.dismissTimer.stop();
          PhraseFlashBanner.this.dismissTimer = null;
        }
        if (PhraseFlashBanner.this.overlay == dialog) {
          PhraseFlashBanner.this.overlay = null;
        }
      }
    });

    final JPanel pane = new JPanel(new BorderLayout());
    pane.setOpaque(true);
    pane.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(PHRASE_FLASH_BORDER, 3, true),
        BorderFactory.createEmptyBorder(28, 36, 28, 36)));
    dialog.setContentPane(pane);

    final float phraseFontSize =
        PhraseFlashBanner.phraseFontPointsForWindow(owner.getHeight());
    final JTextPane face = new JTextPane();
    face.setEditable(false);
    face.setFocusable(false);
    face.setOpaque(true);
    face.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    face.setFont(face.getFont().deriveFont(Font.BOLD, phraseFontSize));
    final int viewW = 520;
    final int viewH = (int) Math.max(180, Math.min(400, owner.getHeight() * 0.5));
    final JPanel phraseWrap = new JPanel(new GridBagLayout()) {
      @Override
      public Dimension getPreferredSize() {
        final Container p = this.getParent();
        if (!(p instanceof JViewport)) {
          return new Dimension(viewW, viewH);
        }
        final JViewport vp = (JViewport) p;
        final int w = Math.max(1, vp.getWidth() > 0 ? vp.getWidth() : viewW);
        final int vph = Math.max(1, vp.getHeight() > 0 ? vp.getHeight() : viewH);
        face.setSize(new Dimension(w, 10_000));
        final int textH = face.getPreferredSize().height;
        return new Dimension(w, Math.max(vph, textH));
      }

      @Override
      public Dimension getMinimumSize() {
        return this.getPreferredSize();
      }
    };
    phraseWrap.setOpaque(true);
    phraseWrap.setBackground(PHRASE_FLASH_LIGHT_BG);
    final GridBagConstraints wrapG = new GridBagConstraints();
    wrapG.gridx = 0;
    wrapG.gridy = 0;
    wrapG.weightx = 1.0;
    wrapG.weighty = 1.0;
    wrapG.fill = GridBagConstraints.HORIZONTAL;
    wrapG.anchor = GridBagConstraints.CENTER;
    wrapG.insets = new Insets(0, 0, 0, 0);
    phraseWrap.add(face, wrapG);
    final JScrollPane faceScroll = new JScrollPane(phraseWrap);
    faceScroll.setOpaque(false);
    faceScroll.getViewport().setOpaque(true);
    faceScroll.setBorder(BorderFactory.createEmptyBorder());
    faceScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    faceScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    faceScroll.setPreferredSize(new Dimension(viewW, viewH));

    pane.setBackground(PHRASE_FLASH_LIGHT_BG);
    faceScroll.getViewport().setBackground(PHRASE_FLASH_LIGHT_BG);
    face.setText(expectedFaceText);
    this.applyFaceStyles(face, PHRASE_FLASH_LIGHT_FG, PHRASE_FLASH_LIGHT_BG);

    pane.add(faceScroll, BorderLayout.CENTER);

    final boolean[] showExpectedRef = {true};
    final Runnable updateFace = () -> {
      if (showExpectedRef[0]) {
        pane.setBackground(PHRASE_FLASH_LIGHT_BG);
        phraseWrap.setBackground(PHRASE_FLASH_LIGHT_BG);
        faceScroll.getViewport().setBackground(PHRASE_FLASH_LIGHT_BG);
        face.setText(expectedFaceText);
        this.applyFaceStyles(face, PHRASE_FLASH_LIGHT_FG, PHRASE_FLASH_LIGHT_BG);
      } else {
        pane.setBackground(PHRASE_FLASH_DARK_BG);
        phraseWrap.setBackground(PHRASE_FLASH_DARK_BG);
        faceScroll.getViewport().setBackground(PHRASE_FLASH_DARK_BG);
        face.setText(partnerFaceText);
        this.applyFaceStyles(face, PHRASE_FLASH_DARK_FG, PHRASE_FLASH_DARK_BG);
      }
      face.setCaretPosition(0);
      phraseWrap.revalidate();
    };

    this.flipTimer = new Timer(1_000, event -> {
      showExpectedRef[0] = !showExpectedRef[0];
      updateFace.run();
      faceScroll.getVerticalScrollBar().setValue(0);
      pane.revalidate();
      pane.repaint();
    });
    this.flipTimer.setInitialDelay(1_000);
    this.flipTimer.start();

    final Runnable closeAndFinish = () -> {
      this.dismiss();
      if (afterDismiss != null) {
        SwingUtilities.invokeLater(afterDismiss);
      }
    };

    this.dismissTimer = new Timer(5_000, event -> closeAndFinish.run());
    this.dismissTimer.setRepeats(false);
    this.dismissTimer.start();

    dialog.pack();
    dialog.setMinimumSize(dialog.getPreferredSize());
    final java.awt.Point ownerLoc = owner.getLocationOnScreen();
    final Dimension ownerSize = owner.getSize();
    dialog.setLocation(
        ownerLoc.x + Math.max(0, (ownerSize.width - dialog.getWidth()) / 2),
        ownerLoc.y + Math.max(0, (ownerSize.height - dialog.getHeight()) / 2));

    SwingUtilities.invokeLater(() -> dialog.setVisible(true));
  }

  private void applyFaceStyles(final JTextPane face, final Color fg, final Color bg) {
    face.setBackground(bg);
    face.setForeground(fg);
    face.setCaretColor(fg);
    final StyledDocument doc = face.getStyledDocument();
    final int len = doc.getLength();
    if (len <= 0) {
      return;
    }
    final Font f = face.getFont();
    final SimpleAttributeSet chars = new SimpleAttributeSet();
    setForeground(chars, fg);
    setBackground(chars, bg);
    setBold(chars, true);
    setFontFamily(chars, f.getFamily());
    setFontSize(chars, f.getSize());
    final SimpleAttributeSet para = new SimpleAttributeSet();
    setAlignment(para, StyleConstants.ALIGN_CENTER);
    try {
      doc.setCharacterAttributes(0, len, chars, true);
      doc.setParagraphAttributes(0, len, para, true);
    } catch (final Exception ignored) {
    }
  }
}
