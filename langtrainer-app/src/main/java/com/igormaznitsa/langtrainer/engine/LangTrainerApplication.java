package com.igormaznitsa.langtrainer.engine;

import static com.igormaznitsa.langtrainer.engine.EngineUtils.loadResourceImage;

import com.igormaznitsa.langtrainer.Modules;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.KeyboardLanguage;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

public final class LangTrainerApplication {

  /**
   * Placed on the module host panel so embedded modules (for example Fly game) can request return
   * to the main menu the same way as the frame toolbar close action.
   */
  public static final String CLOSE_MODULE_CLIENT_PROPERTY = "langtrainer.closeModule";

  /**
   * Placed on the module host panel so modules can show or hide the frame toolbar (virtual keyboard
   * and exit). Value is a {@code Consumer<Boolean>}; {@code true} means the toolbar is visible.
   */
  public static final String SET_TOOLBAR_VISIBLE_CLIENT_PROPERTY = "langtrainer.setToolbarVisible";

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
    this.mainMenuPanel = new MainMenuPanel(this.modules, this::activateModule);
    this.realKeyboardDispatcher = this::dispatchTypedChar;
  }

  public void start() {
    initMainFrame();
    final SplashWindow splashWindow = new SplashWindow(this.mainFrame);
    splashWindow.showForMillis(5000, () -> {
      this.mainFrame.setVisible(true);
      this.mainMenuPanel.focusList();
    });
  }

  private void initMainFrame() {
    this.mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    this.mainFrame.setLayout(new BorderLayout());
    this.mainFrame.setContentPane(this.mainMenuPanel);
    this.mainFrame.pack();
    this.mainFrame.setLocationRelativeTo(null);
  }

  private void activateModule(final AbstractLangTrainerModule module) {
    closeVirtualKeyboard();
    this.activeModule = module;
    this.activeModule.onActivation();
    attachRealKeyboardDispatcher();

    final JPanel container = new JPanel(new BorderLayout());
    container.putClientProperty(CLOSE_MODULE_CLIENT_PROPERTY, (Runnable) this::closeActiveModule);
    container.putClientProperty(
        SET_TOOLBAR_VISIBLE_CLIENT_PROPERTY, (Consumer<Boolean>) this::setModuleTopToolbarVisible);
    container.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    container.add(makeTopPanel(), BorderLayout.NORTH);
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
    this.keyboardButton.addActionListener(event -> showVirtualKeyboard());
    final JButton closeButton = new JButton("X");
    closeButton.setHorizontalAlignment(SwingConstants.CENTER);
    closeButton.setForeground(Color.WHITE);
    closeButton.setBackground(new Color(180, 40, 40));
    closeButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
    closeButton.setFocusPainted(false);
    closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, 24.0f));
    closeButton.addActionListener(event -> closeActiveModule());
    rightButtons.add(this.keyboardButton);
    rightButtons.add(closeButton);
    topPanel.add(rightButtons, BorderLayout.EAST);
    return topPanel;
  }

  private void setModuleTopToolbarVisible(final boolean visible) {
    if (this.moduleToolbarPanel != null) {
      this.moduleToolbarPanel.setVisible(visible);
    }
    if (!visible) {
      closeVirtualKeyboard();
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
    closeVirtualKeyboard();
    this.moduleToolbarPanel = null;
    this.mainFrame.setContentPane(this.mainMenuPanel);
    this.mainFrame.revalidate();
    this.mainFrame.repaint();
    this.mainMenuPanel.focusList();
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
    boolean result;
    result = false;
    if (this.activeModule != null && event.getID() == KeyEvent.KEY_TYPED) {
      final char symbol = event.getKeyChar();
      if (symbol != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(symbol)) {
        onCharInput(symbol);
        result = true;
      }
    }
    return result;
  }
}
