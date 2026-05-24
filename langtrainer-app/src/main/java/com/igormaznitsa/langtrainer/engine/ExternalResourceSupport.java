package com.igormaznitsa.langtrainer.engine;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

public final class ExternalResourceSupport {

  public static final String EXTERNAL_FOLDER_URL =
      "https://github.com/raydac/langtrainer/pub/externals";
  public static final String EXTERNAL_FOLDER_PROPERTY = "LANGTRAINER_EXTERNALS";
  public static final Path EXTERNAL_FOLDER = resolveExternalFolder();
  private static final String EXTERNAL_FOLDER_PATH_KEY_PREFIX = "external:";

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

  public static void syncAndLoadAsync(
      final AbstractLangTrainerModule module,
      final ResourceListSelectPanel.Result view,
      final Consumer<ClasspathResourceIndexTree> onTreeLoaded,
      final Runnable onListRefreshNeeded) {
    view.setBusy(true);
    new SwingWorker<ClasspathResourceIndexTree, Void>() {
      @Override
      protected ClasspathResourceIndexTree doInBackground() {
        ExternalResourceSupport.syncExternalFolder();
        return ExternalResourceSupport.loadLocalTree(module);
      }

      @Override
      protected void done() {
        try {
          onTreeLoaded.accept(this.get());
          onListRefreshNeeded.run();
        } catch (final InterruptedException ex) {
          Thread.currentThread().interrupt();
          System.err.println("External resource refresh was interrupted: " + ex.getMessage());
        } catch (final ExecutionException ex) {
          final Throwable cause = ex.getCause() == null ? ex : ex.getCause();
          System.err.println("Can't refresh external resources: " + cause.getMessage());
        } catch (final Exception ex) {
          System.err.println("Can't refresh external resources: " + ex.getMessage());
        } finally {
          view.setBusy(false);
        }
      }
    }.execute();
  }

  private static void syncExternalFolder() {
    try {
      new GitHubFolderSynchronizer(EXTERNAL_FOLDER_URL, EXTERNAL_FOLDER).sync();
    } catch (final Exception ex) {
      System.err.println(
          "Can't sync external resources from " + EXTERNAL_FOLDER_URL + ": " + ex.getMessage());
    }
  }

  private static Path resolveExternalFolder() {
    return Optional.ofNullable(System.getProperty(EXTERNAL_FOLDER_PROPERTY))
        .or(() -> Optional.ofNullable(System.getenv(EXTERNAL_FOLDER_PROPERTY)))
        .map(String::strip)
        .filter(value -> !value.isEmpty())
        .map(Path::of)
        .orElse(Path.of("./externals"));
  }
}
