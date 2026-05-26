package com.igormaznitsa.langtrainer.engine;

import static org.apache.commons.lang3.Strings.CI;

import com.igormaznitsa.langtrainer.Modules;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.SplashScreen;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.JTextComponent;

public final class LangTrainerApplication {

  public static final String CLOSE_MODULE_CLIENT_PROPERTY = "langtrainer.closeModule";
  public static final String SET_TOOLBAR_VISIBLE_CLIENT_PROPERTY = "langtrainer.setToolbarVisible";
  public static final String SHOW_VIRTUAL_KEYBOARD_CLIENT_PROPERTY =
      "langtrainer.showVirtualKeyboard";
  private static final String APP_VERSION_SYSTEM_PROPERTY = "langtrainer.app.version";
  private static final String HELP_DIALOG_HTML_RESOURCE = "/html/how-to-use.html";
  private static final String HELP_VERSION_PLACEHOLDER = "${project.version}";

  private final JFrame mainFrame;
  private final List<AbstractLangTrainerModule> modules;
  private final MainMenuPanel mainMenuPanel;
  private final KeyEventDispatcher realKeyboardDispatcher;
  private JButton keyboardButton;
  private JPanel moduleToolbarPanel;
  private VirtualKeyboardWindow virtualKeyboardWindow;
  private AbstractLangTrainerModule activeModule;

  public LangTrainerApplication() {
    this.mainFrame = new JFrame("LangTrainer");
    try {
      this.mainFrame.setIconImage(ImageResourceLoader.loadImage("/images/icon.svg", 64, 64));
    } catch (Exception ex) {
      System.err.println("Can't load app icon");
    }
    this.modules = Modules.createAll();
    this.mainMenuPanel =
        new MainMenuPanel(this.modules, this::activateModule, this::showHowToUseDialog);
    this.realKeyboardDispatcher = this::dispatchTypedChar;
  }

  public void start() {
    this.initMainFrame();
    SwingUtilities.invokeLater(() -> this.mainFrame.setVisible(true));
    this.dismissSplashAfterMillis(
        5000,
        () -> {
          this.mainFrame.toFront();
          this.mainMenuPanel.focusList();
        });
  }

  private void dismissSplashAfterMillis(final int millis, final Runnable onDone) {
    final SplashScreen splash = SplashScreen.getSplashScreen();
    if (splash == null || !this.isSplashVisible(splash)) {
      onDone.run();
      return;
    }
    this.paintAppVersionOnSplash(splash);
    final Timer timer = new Timer(millis, event -> {
      try {
        this.closeSplashIfVisible(splash);
      } finally {
        onDone.run();
      }
    });
    timer.setRepeats(false);
    timer.start();
  }

  private boolean isSplashVisible(final SplashScreen splash) {
    try {
      return splash.isVisible();
    } catch (final IllegalStateException ex) {
      return false;
    }
  }

  private void closeSplashIfVisible(final SplashScreen splash) {
    if (!this.isSplashVisible(splash)) {
      return;
    }
    try {
      splash.close();
    } catch (final IllegalStateException ignored) {
      // The splash may be closed by the JVM/window system before our timer fires.
    }
  }

  private void paintAppVersionOnSplash(final SplashScreen splash) {
    final Graphics2D graphics;
    try {
      graphics = splash.createGraphics();
    } catch (final IllegalStateException ex) {
      return;
    }
    if (graphics == null) {
      return;
    }
    try {
      final String versionText = "   Version: " + this.resolveAppVersion();
      graphics.setColor(new Color(0, 0, 0, 230));
      graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 14.0f));
      final FontMetrics metrics = graphics.getFontMetrics();
      final int x = splash.getSize().width - metrics.stringWidth(versionText) - 12;
      final int y = splash.getSize().height - 12;
      graphics.drawString(versionText, x, y);
    } finally {
      this.updateSplashIfVisible(splash);
      graphics.dispose();
    }
  }

  private void updateSplashIfVisible(final SplashScreen splash) {
    if (!this.isSplashVisible(splash)) {
      return;
    }
    try {
      splash.update();
    } catch (final IllegalStateException ignored) {
      // The splash may disappear between drawing and update.
    }
  }

  private String resolveAppVersion() {
    return Optional.ofNullable(System.getProperty(APP_VERSION_SYSTEM_PROPERTY))
        .or(() -> Optional.ofNullable(
            LangTrainerApplication.class.getPackage().getImplementationVersion()))
        .orElse("dev");
  }

  private void initMainFrame() {
    this.mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    this.mainFrame.setLayout(new BorderLayout());
    this.mainFrame.setContentPane(this.mainMenuPanel);
    this.mainFrame.pack();
    this.mainFrame.setLocationRelativeTo(null);
    this.mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
  }

  private void activateModule(final AbstractLangTrainerModule module) {
    this.closeVirtualKeyboard();
    this.activeModule = module;
    this.activeModule.onActivation();
    this.attachRealKeyboardDispatcher();

    final JPanel container = new JPanel(new BorderLayout());
    container.putClientProperty(CLOSE_MODULE_CLIENT_PROPERTY, (Runnable) this::closeActiveModule);
    container.putClientProperty(
        SET_TOOLBAR_VISIBLE_CLIENT_PROPERTY, (Consumer<Boolean>) this::setModuleTopToolbarVisible);
    container.putClientProperty(
        SHOW_VIRTUAL_KEYBOARD_CLIENT_PROPERTY, (Runnable) this::showVirtualKeyboard);
    container.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    container.add(this.makeTopPanel(), BorderLayout.NORTH);
    container.add(module.createControlForm(), BorderLayout.CENTER);
    this.mainFrame.setContentPane(container);
    this.mainFrame.revalidate();
    this.mainFrame.repaint();
  }

  private JPanel makeTopPanel() {
    final JPanel topPanel = new JPanel(new BorderLayout());
    this.moduleToolbarPanel = topPanel;
    topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
    final JPanel rightButtons = new JPanel();
    if (this.activeModule.isVirtualKeyboardToolbarButtonShown()) {
      this.keyboardButton = new JButton();
      this.keyboardButton.setIcon(ImageResourceLoader.loadIcon("/images/keyboard.svg", 24, 24));
      this.keyboardButton.setToolTipText("Show virtual keyboard");
      this.keyboardButton.setFont(this.keyboardButton.getFont().deriveFont(Font.BOLD, 24.0f));
      this.keyboardButton.setFocusPainted(false);
      this.keyboardButton.setFocusable(false);
      this.keyboardButton.setRequestFocusEnabled(false);
      this.keyboardButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
      this.keyboardButton.addActionListener(event -> this.showVirtualKeyboard());
      rightButtons.add(this.keyboardButton);
    } else {
      this.keyboardButton = null;
    }
    final JButton closeButton = new JButton("X");
    closeButton.setHorizontalAlignment(SwingConstants.CENTER);
    closeButton.setForeground(Color.WHITE);
    closeButton.setBackground(new Color(180, 40, 40));
    closeButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    closeButton.setFocusPainted(false);
    closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, 24.0f));
    closeButton.addActionListener(event -> this.closeActiveModule());
    if (this.activeModule != null) {
      this.activeModule.populateMainToolbar(rightButtons);
    }
    rightButtons.add(closeButton);
    this.normalizeToolbarButtonSizes(rightButtons);
    topPanel.add(rightButtons, BorderLayout.EAST);
    return topPanel;
  }

  private void normalizeToolbarButtonSizes(final JPanel buttonPanel) {
    int maxW = 0;
    int maxH = 0;
    for (final Component component : buttonPanel.getComponents()) {
      if (component instanceof JButton button) {
        final Dimension pref = button.getPreferredSize();
        maxW = Math.max(maxW, pref.width);
        maxH = Math.max(maxH, pref.height);
      }
    }
    final int side = Math.max(maxW, maxH);
    final Dimension normalized = new Dimension(side, side);
    for (final Component component : buttonPanel.getComponents()) {
      if (component instanceof JButton button) {
        button.setPreferredSize(normalized);
        button.setMinimumSize(normalized);
        button.setMaximumSize(normalized);
      }
    }
  }

  private void setModuleTopToolbarVisible(final boolean visible) {
    if (this.moduleToolbarPanel != null) {
      this.moduleToolbarPanel.setVisible(visible);
    }
    if (!visible) {
      this.closeVirtualKeyboard();
    }
    this.mainFrame.revalidate();
    this.mainFrame.repaint();
  }

  private void closeActiveModule() {
    if (this.activeModule != null) {
      this.activeModule.onClose();
      this.activeModule = null;
    }
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .removeKeyEventDispatcher(this.realKeyboardDispatcher);
    this.closeVirtualKeyboard();
    this.moduleToolbarPanel = null;
    this.mainFrame.setContentPane(this.mainMenuPanel);
    this.mainFrame.revalidate();
    this.mainFrame.repaint();
    this.mainMenuPanel.focusList();
  }

  private void showHowToUseDialog() {
    final String message = this.loadHelpDialogHtml();
    final JEditorPane htmlPane = new JEditorPane("text/html", message);
    htmlPane.setEditable(false);
    htmlPane.setOpaque(false);
    htmlPane.setBackground(new Color(0, 0, 0, 0));
    htmlPane.setCaretPosition(0);
    htmlPane.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    htmlPane.addHyperlinkListener(this::handleHelpHyperlink);
    JOptionPane.showMessageDialog(
        this.mainFrame,
        htmlPane,
        "LangTrainer - Quick Help",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private String loadHelpDialogHtml() {
    try (InputStream input = LangTrainerApplication.class.getResourceAsStream(
        HELP_DIALOG_HTML_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException(
            "Help HTML resource not found: " + HELP_DIALOG_HTML_RESOURCE);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8)
          .replace(HELP_VERSION_PLACEHOLDER, this.resolveAppVersion());
    } catch (final IOException ex) {
      throw new IllegalStateException("Can't read help HTML resource: " + HELP_DIALOG_HTML_RESOURCE,
          ex);
    }
  }

  private void handleHelpHyperlink(final HyperlinkEvent event) {
    if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED || event.getURL() == null) {
      return;
    }
    try {
      this.openInSystemBrowser(event.getURL().toURI());
    } catch (final URISyntaxException ex) {
      throw new IllegalStateException("Invalid help hyperlink URL: " + event.getURL(), ex);
    }
  }

  private void openInSystemBrowser(final URI uri) {
    final String scheme = uri.getScheme();
    if (!CI.equalsAny(scheme, "https", "http")) {
      throw new IllegalStateException("Unsupported help hyperlink scheme: " + scheme);
    }
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      throw new IllegalStateException("Desktop browser open action is not supported");
    }
    try {
      Desktop.getDesktop().browse(uri);
    } catch (final IOException ex) {
      throw new IllegalStateException("Can't open help link in browser: " + uri, ex);
    }
  }

  private void showVirtualKeyboard() {
    if (this.activeModule == null || !this.activeModule.isVirtualKeyboardToolbarButtonShown()) {
      return;
    }
    final Component focusBeforeKeyboard = this.resolveModuleFocusOwner();
    if (this.virtualKeyboardWindow == null) {
      final List<KeyboardLanguage> supported = this.activeModule.getSupportedLanguages();
      this.virtualKeyboardWindow =
          new VirtualKeyboardWindow(
              this.mainFrame,
              supported,
              this::onCharInput,
              this::onVirtualKeyboardHidden);
    }
    this.virtualKeyboardWindow.show();
    if (this.keyboardButton != null) {
      this.keyboardButton.setVisible(false);
    }
    this.restoreModuleFocus(focusBeforeKeyboard);
  }

  private Component resolveModuleFocusOwner() {
    final Component focus =
        KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
    return focus != null && SwingUtilities.isDescendingFrom(focus, this.mainFrame) ? focus : null;
  }

  private void restoreModuleFocus(final Component focusBeforeKeyboard) {
    if (focusBeforeKeyboard != null && focusBeforeKeyboard.isShowing()) {
      SwingUtilities.invokeLater(focusBeforeKeyboard::requestFocusInWindow);
    }
  }

  private void onVirtualKeyboardHidden() {
    if (this.keyboardButton != null && this.activeModule != null
        && this.activeModule.isVirtualKeyboardToolbarButtonShown()) {
      this.keyboardButton.setVisible(true);
    }
  }

  private void closeVirtualKeyboard() {
    if (this.virtualKeyboardWindow != null) {
      this.virtualKeyboardWindow.dispose();
      this.virtualKeyboardWindow = null;
    }
    if (this.keyboardButton != null && this.activeModule != null
        && this.activeModule.isVirtualKeyboardToolbarButtonShown()) {
      this.keyboardButton.setVisible(true);
    }
  }

  private void attachRealKeyboardDispatcher() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .removeKeyEventDispatcher(this.realKeyboardDispatcher);
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(this.realKeyboardDispatcher);
  }

  private void onCharInput(final char symbol) {
    if (this.insertVirtualCharIntoFocusedTextComponent(symbol)) {
      return;
    }
    if (this.activeModule != null) {
      this.activeModule.onCharClick(symbol);
    }
  }

  private boolean insertVirtualCharIntoFocusedTextComponent(final char symbol) {
    final Component focus =
        KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focus instanceof JTextComponent text && text.isEditable() && text.isEnabled()) {
      text.replaceSelection(Character.toString(symbol));
      return true;
    }
    return false;
  }

  private boolean dispatchTypedChar(final KeyEvent event) {
    if (this.activeModule == null || event.getID() != KeyEvent.KEY_TYPED) {
      return false;
    }
    final Component focus =
        KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focus instanceof JTextComponent text && text.isEditable()) {
      return false;
    }
    final char symbol = event.getKeyChar();
    if (symbol == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(symbol)) {
      return false;
    }
    this.onCharInput(symbol);
    return true;
  }
}

