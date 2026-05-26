package com.igormaznitsa.langtrainer.engine;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ClassUtils.getSimpleName;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.apache.commons.lang3.SystemUtils.IS_OS_MAC;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.apache.commons.lang3.SystemUtils.getUserHome;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.engine.GitHubFolderSynchronizer.SyncSummary;
import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class ExternalResourceSupport {

  private static final Logger LOG = Logger.getLogger(ExternalResourceSupport.class.getName());
  public static final String EXTERNAL_FOLDER_URL =
      "https://github.com/raydac/langtrainer/pub/externals";
  public static final String EXTERNAL_FOLDER_PROPERTY = "LANGTRAINER_EXTERNALS";
  public static final Path EXTERNAL_FOLDER = resolveExternalFolder();
  private static final String APP_CONFIG_FOLDER_NAME = "LangTrainer";
  private static final String EXTERNAL_RESOURCES_FOLDER_NAME = "externals";
  private static final String EXTERNAL_FOLDER_PATH_KEY_PREFIX = "external:";
  private static final List<DialogListEntry.DialogResourceRow> OPENED_FILE_RESOURCE_ROWS =
      new CopyOnWriteArrayList<>();

  private ExternalResourceSupport() {
  }

  public static ClasspathResourceIndexTree loadLocalTree(final AbstractLangTrainerModule module) {
    return ExternalLangResourceIndex.loadSharedTree(EXTERNAL_FOLDER, module);
  }

  public static void materializeLocalTree(
      final ClasspathResourceIndexTree tree,
      final javax.swing.DefaultListModel<DialogListEntry> model,
      final java.util.Set<String> expandedFolderPathKeys) {
    tree.materializeInto(model, expandedFolderPathKeys, true, EXTERNAL_FOLDER_PATH_KEY_PREFIX);
  }

  public static void materializeOpenedFileRows(
      final AbstractLangTrainerModule module,
      final DefaultListModel<DialogListEntry> model) {
    OPENED_FILE_RESOURCE_ROWS.stream()
        .filter(row -> module.isResourceAllowed(row.definition()))
        .forEach(model::addElement);
  }

  public static Optional<OpenedResource> openResourceFromFile(
      final Component parent,
      final File currentDirectory,
      final String filterDescription,
      final String errorTitle) {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter(filterDescription, "json"));
    chooser.setAcceptAllFileFilterUsed(false);
    if (currentDirectory != null) {
      chooser.setCurrentDirectory(currentDirectory);
    }
    if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
      return Optional.empty();
    }
    return parseSelectedResource(parent, chooser.getSelectedFile(), errorTitle);
  }

  public static void mergeOpenedResource(
      final DefaultListModel<DialogListEntry> model,
      final JList<DialogListEntry> list,
      final DialogDefinition loaded,
      final Runnable onListRefreshNeeded) {
    DialogListEntry.mergeFileResourceRow(
        OPENED_FILE_RESOURCE_ROWS, DialogListEntry.fileResourceRow(loaded));
    onListRefreshNeeded.run();
    final int index = DialogListEntry.indexOfFileResourceMenuName(model, loaded.menuName());
    if (index >= 0) {
      list.setSelectedIndex(index);
    }
  }

  public static void syncAndLoadAsync(
      final AbstractLangTrainerModule module,
      final ResourceListSelectPanel.Result view,
      final Consumer<ClasspathResourceIndexTree> onTreeLoaded,
      final Runnable onListRefreshNeeded) {
    view.setBusy(true);
    new SwingWorker<RefreshResult, Void>() {
      @Override
      protected RefreshResult doInBackground() {
        return ExternalResourceSupport.refreshExternalResources(module);
      }

      @Override
      protected void done() {
        try {
          final RefreshResult result = this.get();
          onTreeLoaded.accept(result.tree());
          onListRefreshNeeded.run();
          if (result instanceof final RefreshResult.SyncFailed failed) {
            ExternalResourceSupport.showExternalSyncFailure(view, failed.failure());
          } else if (result instanceof final RefreshResult.SyncSucceeded succeeded) {
            ExternalResourceSupport.showExternalSyncSuccess(view, succeeded.summary());
          }
        } catch (final InterruptedException ex) {
          Thread.currentThread().interrupt();
          LOG.log(Level.WARNING, "External resource refresh was interrupted", ex);
          ExternalResourceSupport.showExternalSyncFailure(view, ex);
        } catch (final ExecutionException ex) {
          final Throwable cause = ex.getCause() == null ? ex : ex.getCause();
          LOG.log(Level.SEVERE, "Can't refresh external resources", cause);
          ExternalResourceSupport.showExternalSyncFailure(view, cause);
        } catch (final Exception ex) {
          LOG.log(Level.SEVERE, "Can't refresh external resources", ex);
          ExternalResourceSupport.showExternalSyncFailure(view, ex);
        } finally {
          view.setBusy(false);
        }
      }
    }.execute();
  }

  private static RefreshResult refreshExternalResources(final AbstractLangTrainerModule module) {
    try {
      final SyncSummary summary = syncExternalFolder();
      return new RefreshResult.SyncSucceeded(loadLocalTree(module), summary);
    } catch (final Exception ex) {
      LOG.log(Level.WARNING, "Can't sync external resources from " + EXTERNAL_FOLDER_URL, ex);
      return new RefreshResult.SyncFailed(loadLocalTree(module), ex);
    }
  }

  private static Optional<OpenedResource> parseSelectedResource(
      final Component parent,
      final File file,
      final String errorTitle) {
    if (file == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new OpenedResource(
              LangResourceJson.parseFromPath(file.toPath()),
              Optional.ofNullable(file.getParentFile())));
    } catch (final Exception ex) {
      JOptionPane.showMessageDialog(parent, ex.getMessage(), errorTitle, JOptionPane.ERROR_MESSAGE);
      return Optional.empty();
    }
  }

  private static SyncSummary syncExternalFolder() {
    return new GitHubFolderSynchronizer(EXTERNAL_FOLDER_URL, EXTERNAL_FOLDER).sync();
  }

  private static void showExternalSyncSuccess(
      final ResourceListSelectPanel.Result view,
      final SyncSummary summary) {
    JOptionPane.showMessageDialog(
        view.panel(),
        externalSyncSuccessMessage(summary),
        "External resources",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private static String externalSyncSuccessMessage(final SyncSummary summary) {
    return """
        Sync is OK.
        
        Source: %s
        Target: %s
        
        Files loaded: %d
        Files removed: %d
        Files updated: %d
        """.formatted(
        EXTERNAL_FOLDER_URL,
        EXTERNAL_FOLDER.toAbsolutePath().normalize(),
        summary.loadedFiles(),
        summary.removedFiles(),
        summary.updatedFiles());
  }

  private static void showExternalSyncFailure(
      final ResourceListSelectPanel.Result view, final Throwable failure) {
    JOptionPane.showMessageDialog(
        view.panel(),
        externalSyncFailureMessage(failure),
        "External resources",
        JOptionPane.ERROR_MESSAGE);
  }

  private static String externalSyncFailureMessage(final Throwable failure) {
    return """
        Can't sync external resources from GitHub.
        
        Source: %s
        Target: %s
        Reason: %s
        
        Existing local resources will still be shown if they can be loaded.
        """.formatted(EXTERNAL_FOLDER_URL, EXTERNAL_FOLDER.toAbsolutePath().normalize(),
        describeFailure(failure));
  }

  private static String describeFailure(final Throwable failure) {
    final Throwable root = getIfNull(getRootCause(failure), failure);
    final String message = root.getMessage();
    return isBlank(message) ? getSimpleName(root) :
        "%s: %s".formatted(getSimpleName(root), message);
  }

  private static Path resolveExternalFolder() {
    return Optional.ofNullable(stripToNull(System.getProperty(EXTERNAL_FOLDER_PROPERTY)))
        .or(() -> Optional.ofNullable(stripToNull(System.getenv(EXTERNAL_FOLDER_PROPERTY))))
        .map(Path::of)
        .orElseGet(() -> findDefaultAppConfigFolder().resolve(EXTERNAL_RESOURCES_FOLDER_NAME));
  }

  static Path findDefaultAppConfigFolder() {
    if (IS_OS_WINDOWS) {
      return resolveWindowsAppConfigFolder();
    }
    if (IS_OS_MAC) {
      return userHomeFolder().resolve("Library").resolve("Application Support")
          .resolve(APP_CONFIG_FOLDER_NAME);
    }
    return resolveUnixAppConfigFolder();
  }

  private static Path resolveWindowsAppConfigFolder() {
    return configuredPath("APPDATA")
        .orElseGet(() -> userHomeFolder().resolve("AppData").resolve("Roaming"))
        .resolve(APP_CONFIG_FOLDER_NAME);
  }

  private static Path resolveUnixAppConfigFolder() {
    return configuredPath("XDG_CONFIG_HOME")
        .orElseGet(() -> userHomeFolder().resolve(".config"))
        .resolve(APP_CONFIG_FOLDER_NAME.toLowerCase(Locale.ROOT));
  }

  private static Optional<Path> configuredPath(final String environmentVariable) {
    return Optional.ofNullable(stripToNull(System.getenv(environmentVariable))).map(Path::of);
  }

  private static Path userHomeFolder() {
    return getUserHome().toPath();
  }

  private sealed interface RefreshResult
      permits RefreshResult.SyncSucceeded, RefreshResult.SyncFailed {

    ClasspathResourceIndexTree tree();

    record SyncSucceeded(ClasspathResourceIndexTree tree, SyncSummary summary)
        implements RefreshResult {
    }

    record SyncFailed(ClasspathResourceIndexTree tree, Throwable failure) implements RefreshResult {
    }
  }

  public record OpenedResource(DialogDefinition definition, Optional<File> parentDirectory) {

    public OpenedResource {
      requireNonNull(definition, "definition must not be null");
      requireNonNull(parentDirectory, "parentDirectory must not be null");
    }
  }
}
