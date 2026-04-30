package com.igormaznitsa.langtrainer.engine;

import static com.igormaznitsa.langtrainer.engine.EngineUtils.loadResourceImage;

import com.igormaznitsa.langtrainer.Modules;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.text.JTextComponent;

public final class LangTrainerApplication {

  public static final String CLOSE_MODULE_CLIENT_PROPERTY = "langtrainer.closeModule";
  public static final String SET_TOOLBAR_VISIBLE_CLIENT_PROPERTY = "langtrainer.setToolbarVisible";
  private static final String APP_VERSION_SYSTEM_PROPERTY = "langtrainer.app.version";

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
      this.mainFrame.setIconImage(loadResourceImage("/images/icon.png"));
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
    this.mainFrame.setEnabled(false);
    final SplashWindow splashWindow = new SplashWindow(this.mainFrame, this.resolveAppVersion());
    splashWindow.showForMillis(
        5000,
        () -> this.mainFrame.setVisible(true),
        () -> {
          this.mainFrame.setEnabled(true);
          this.mainFrame.toFront();
          this.mainMenuPanel.focusList();
        });
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
    this.keyboardButton = new JButton();
    this.keyboardButton.setIcon(ImageResourceLoader.loadIcon("/images/keyboard.svg", 24, 24));
    this.keyboardButton.setToolTipText("Show virtual keyboard");
    this.keyboardButton.setFont(this.keyboardButton.getFont().deriveFont(Font.BOLD, 24.0f));
    this.keyboardButton.setFocusPainted(false);
    this.keyboardButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    this.keyboardButton.addActionListener(event -> this.showVirtualKeyboard());
    final JButton closeButton = new JButton("X");
    closeButton.setHorizontalAlignment(SwingConstants.CENTER);
    closeButton.setForeground(Color.WHITE);
    closeButton.setBackground(new Color(180, 40, 40));
    closeButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    closeButton.setFocusPainted(false);
    closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, 24.0f));
    closeButton.addActionListener(event -> this.closeActiveModule());
    rightButtons.add(this.keyboardButton);
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
    final String message = """
        <html>
        <body style='width:620px;font-family:sans-serif;'>
        <h2>How to use LangTrainer</h2>
        <ol>
          <li><b>Choose a module</b> on the main screen (Dialog, Fly game, Crossword, Editor).</li>
          <li><b>Open built-in content</b> by selecting a list item, or load your own JSON file where supported.</li>
          <li><b>Start a session</b> with Enter, Space, or mouse click.</li>
          <li><b>Type answers</b> using your physical keyboard or the virtual keyboard button in module toolbar.</li>
          <li><b>Finish or return</b> with module controls, then use <b>X</b> in the top-right to come back.</li>
        </ol>
        <p><b>Tips</b></p>
        <ul>
          <li>Use the <b>Editor</b> module to create or adjust your own training JSON packs.</li>
          <li>In game modules, status lines and highlights explain progress and mistakes.</li>
          <li>You can hide/show module top toolbar when module supports it.</li>
        </ul>
        </body>
        </html>
        """;
    JOptionPane.showMessageDialog(
        this.mainFrame,
        message,
        "LangTrainer - Quick Help",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private void showVirtualKeyboard() {
    if (this.activeModule != null) {
      if (this.virtualKeyboardWindow == null) {
        final List<KeyboardLanguage> supported = this.activeModule.getSupportedLanguages();
        this.virtualKeyboardWindow = new VirtualKeyboardWindow(
            this.mainFrame,
            supported,
            this::onCharInput,
            this::onVirtualKeyboardHidden);
      }
      this.virtualKeyboardWindow.show();
      if (this.keyboardButton != null) {
        this.keyboardButton.setVisible(false);
      }
    }
  }

  private void onVirtualKeyboardHidden() {
    if (this.keyboardButton != null) {
      this.keyboardButton.setVisible(true);
    }
  }

  private void closeVirtualKeyboard() {
    if (this.virtualKeyboardWindow != null) {
      this.virtualKeyboardWindow.dispose();
      this.virtualKeyboardWindow = null;
    }
    if (this.keyboardButton != null) {
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
    if (this.activeModule != null) {
      this.activeModule.onCharClick(symbol);
    }
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

