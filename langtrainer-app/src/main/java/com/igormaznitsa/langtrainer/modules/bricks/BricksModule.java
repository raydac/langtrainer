package com.igormaznitsa.langtrainer.modules.bricks;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.api.LangTrainerModuleId;
import com.igormaznitsa.langtrainer.engine.ClasspathLangResourceIndex;
import com.igormaznitsa.langtrainer.engine.ClasspathResourceIndexTree;
import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.DialogLine;
import com.igormaznitsa.langtrainer.engine.DialogListEntry;
import com.igormaznitsa.langtrainer.engine.ExternalResourceSupport;
import com.igormaznitsa.langtrainer.engine.ImageResourceLoader;
import com.igormaznitsa.langtrainer.engine.LangTrainerResourceAccess;
import com.igormaznitsa.langtrainer.engine.ResourceListSelectPanel;
import java.awt.CardLayout;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class BricksModule extends AbstractLangTrainerModule {

  private static final String CARD_SELECT = "select";
  private static final String CARD_WORK = "work";
  private final DefaultListModel<DialogListEntry> listModel = new DefaultListModel<>();
  private final Set<String> expandedClasspathFolders = new HashSet<>();
  private final JPanel rootPanel = new JPanel(new CardLayout());
  private final BricksWorkPanel workPanel;
  private final ClasspathResourceIndexTree classpathResourceTree;
  private ClasspathResourceIndexTree externalResourceTree = ClasspathResourceIndexTree.empty();
  private JList<DialogListEntry> selectionList;
  private ResourceListSelectPanel.Result resourceSelectView;
  private File lastOpenDir;
  public BricksModule() {
    this.classpathResourceTree =
        ClasspathLangResourceIndex.loadSharedTree(
            BricksModule.class, this, "Can't load phrase resources");
    this.externalResourceTree = ExternalResourceSupport.loadLocalTree(this);
    this.rebuildResourceListModel();
    this.workPanel = new BricksWorkPanel(this::showSelectCard);
    this.rootPanel.add(this.makeSelectPanel(), CARD_SELECT);
    this.rootPanel.add(this.workPanel, CARD_WORK);
    this.showSelectCard();
  }

  @Override
  public boolean isVirtualKeyboardToolbarButtonShown() {
    return false;
  }

  private void rebuildResourceListModel() {
    this.listModel.clear();
    this.classpathResourceTree.materializeInto(this.listModel, this.expandedClasspathFolders);
    ExternalResourceSupport.materializeLocalTree(
        this.externalResourceTree, this.listModel, this.expandedClasspathFolders);
    ExternalResourceSupport.materializeOpenedFileRows(this, this.listModel);
  }

  private void onClasspathFolderRowClicked(final DialogListEntry.DialogFolderRow folder) {
    final String key = folder.pathKey();
    if (this.expandedClasspathFolders.contains(key)) {
      this.expandedClasspathFolders.remove(key);
    } else {
      this.expandedClasspathFolders.add(key);
    }
    this.rebuildResourceListModel();
    final int rowIndex = DialogListEntry.indexOfFolderPathKey(this.listModel, key);
    if (this.selectionList != null && rowIndex >= 0) {
      this.selectionList.setSelectedIndex(rowIndex);
    }
  }

  private void showSelectCard() {
    final CardLayout layout = (CardLayout) this.rootPanel.getLayout();
    layout.show(this.rootPanel, CARD_SELECT);
    SwingUtilities.invokeLater(() -> {
      if (this.selectionList != null) {
        this.selectionList.requestFocusInWindow();
      }
    });
  }

  private void showWorkCard() {
    final CardLayout layout = (CardLayout) this.rootPanel.getLayout();
    layout.show(this.rootPanel, CARD_WORK);
  }

  private JPanel makeSelectPanel() {
    final ResourceListSelectPanel.Result view = ResourceListSelectPanel.build(
        this.listModel,
        ResourceListSelectPanel.Appearance.FLY_GAME,
        "Select phrase resource",
        "Start",
        "Open from file",
        this::chooseLanguageAndStart,
        this::openFromFile,
        this::onClasspathFolderRowClicked,
        this::loadExternalResources);
    this.resourceSelectView = view;
    this.selectionList = view.list();
    return view.panel();
  }

  private void loadExternalResources() {
    ExternalResourceSupport.syncAndLoadAsync(
        this,
        this.resourceSelectView,
        tree -> this.externalResourceTree = tree,
        this::rebuildResourceListModel);
  }

  private void reloadExternalResourcesFromDisk() {
    this.externalResourceTree = ExternalResourceSupport.loadLocalTree(this);
    this.rebuildResourceListModel();
  }

  private void openFromFile(final JList<DialogListEntry> list) {
    ExternalResourceSupport.openResourceFromFile(
            this.rootPanel, this.lastOpenDir, "JSON phrase list (*.json)", "Can't open file")
        .ifPresent(resource -> {
          ExternalResourceSupport.mergeOpenedResource(
              this.listModel,
              list,
              resource.definition(),
              this::rebuildResourceListModel);
          resource.parentDirectory().ifPresent(parent -> this.lastOpenDir = parent);
        });
  }

  private void chooseLanguageAndStart(final DialogDefinition definition) {
    final javax.swing.JComboBox<String> combo =
        new javax.swing.JComboBox<>(new String[] {definition.langA(), definition.langB()});
    final int opt = JOptionPane.showConfirmDialog(
        this.rootPanel,
        combo,
        "Build phrases in which language?",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (opt != JOptionPane.OK_OPTION) {
      return;
    }
    final String pick = (String) combo.getSelectedItem();
    final boolean userBuildsSideA = definition.langA().equals(pick);
    final List<DialogLine> exercises =
        BricksWorkPanel.filterMultiWordTargetLines(definition.lines(), userBuildsSideA);
    if (exercises.isEmpty()) {
      JOptionPane.showMessageDialog(
          this.rootPanel,
          "This resource has no lines with more than one word on the chosen side.",
          "Nothing to practice",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    this.workPanel.startSession(definition, userBuildsSideA, exercises);
    this.showWorkCard();
  }

  @Override
  public String getName() {
    return "Bricks";
  }

  @Override
  public String getDescription() {
    return "Drag shuffled words into the correct phrase order";
  }

  @Override
  public Icon getImage() {
    return ImageResourceLoader.loadIcon("/bricks/images/module-bricks.svg", 128, 128);
  }

  @Override
  public JComponent createControlForm() {
    return this.rootPanel;
  }

  @Override
  public boolean isResourceAllowed(final DialogDefinition resourceDefinition) {
    return LangTrainerResourceAccess.visibleToModule(
        resourceDefinition, LangTrainerModuleId.BRICKS);
  }

  @Override
  public void onActivation() {
    this.workPanel.resetToIdle();
    this.reloadExternalResourcesFromDisk();
    this.showSelectCard();
    SwingUtilities.invokeLater(() -> {
      if (this.selectionList != null) {
        this.selectionList.requestFocusInWindow();
      }
    });
  }
}
