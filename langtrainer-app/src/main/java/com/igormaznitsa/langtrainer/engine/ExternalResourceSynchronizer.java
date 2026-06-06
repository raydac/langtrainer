package com.igormaznitsa.langtrainer.engine;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.list;
import static java.nio.file.Files.mismatch;
import static java.nio.file.Files.move;
import static java.nio.file.Files.newOutputStream;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static java.nio.file.Files.writeString;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.apache.commons.lang3.StringUtils.containsAny;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.SystemUtils.getUserDir;
import static org.apache.commons.lang3.SystemUtils.getUserHome;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;

public final class ExternalResourceSynchronizer {

  private static final Logger LOG = Logger.getLogger(ExternalResourceSynchronizer.class.getName());
  private static final String INDEX_FILE = "index.json";
  private static final String WINDOWS_FORBIDDEN_CHARS = "<>:\"|?*";
  private static final Set<String> WINDOWS_RESERVED_NAMES =
      Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6",
          "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6",
          "LPT7", "LPT8", "LPT9");
  private static final Set<String> CRITICAL_HOME_CHILD_NAMES =
      Set.of("desktop", "documents", "downloads", "music", "pictures", "projects", "videos",
          "work", "workspace");
  private static final String LOCAL_SYNC_MARKER_FILE = ".langtrainer-external-sync";
  private static final LinkOption[] NO_LINK_OPTIONS = {LinkOption.NOFOLLOW_LINKS};
  private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10L);
  private static final Duration HTTP_RESPONSE_TIMEOUT = Duration.ofSeconds(20L);
  private static final int DOWNLOAD_THREAD_LIMIT = 3;
  private static final int HTTP_BUFFER_SIZE = 8192;
  private static final int MAX_INDEX_BYTES = 1024 * 1024;
  private static final int MAX_RESOURCE_BYTES = 8 * 1024 * 1024;
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final URI indexUri;
  private final Path localFolder;

  public ExternalResourceSynchronizer(final String indexUrl, final Path localFolder) {
    this(URI.create(requireNonNull(indexUrl, "indexUrl must not be null")), localFolder);
  }

  public ExternalResourceSynchronizer(final URI indexUri, final Path localFolder) {
    this.indexUri = requireNonNull(indexUri, "indexUri must not be null").normalize();
    this.localFolder = requireNonNull(localFolder, "localFolder must not be null")
        .toAbsolutePath()
        .normalize();
  }

  private static boolean hasControlCharacter(final String segment) {
    return segment.chars().anyMatch(character -> character < 32);
  }

  private static boolean hasWindowsUnsafeName(final String segment) {
    final String baseName = segment.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
    return segment.endsWith(" ")
        || segment.endsWith(".")
        || containsAny(segment, WINDOWS_FORBIDDEN_CHARS)
        || WINDOWS_RESERVED_NAMES.contains(baseName);
  }

  private static Path normalizePath(final Path path) {
    return path.toAbsolutePath().normalize();
  }

  private static Path normalizePath(final File file) {
    return normalizePath(file.toPath());
  }

  private static long elapsedMillis(final long startedAtNs) {
    return Duration.ofNanos(System.nanoTime() - startedAtNs).toMillis();
  }

  private static CloseableHttpClient httpClient() {
    return HttpClients.custom()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                .setSslContext(createTrustingSslContext())
                .buildClassic())
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(HTTP_CONNECT_TIMEOUT))
                .build())
            .setMaxConnTotal(DOWNLOAD_THREAD_LIMIT)
            .setMaxConnPerRoute(DOWNLOAD_THREAD_LIMIT)
            .build())
        .setDefaultRequestConfig(RequestConfig.custom()
            .setResponseTimeout(Timeout.of(HTTP_RESPONSE_TIMEOUT))
            .build())
        .build();
  }

  private static javax.net.ssl.SSLContext createTrustingSslContext() {
    try {
      return SSLContextBuilder.create()
          .loadTrustMaterial(TrustAllStrategy.INSTANCE)
          .build();
    } catch (final GeneralSecurityException ex) {
      throw new IllegalStateException("Can't create external sync TLS context", ex);
    }
  }

  private static ThreadFactory downloadThreadFactory() {
    return runnable -> {
      final Thread thread = new Thread(runnable, "langtrainer-external-download");
      thread.setDaemon(true);
      return thread;
    };
  }

  private static void requireSuccessfulResponse(
      final URI uri,
      final ClassicHttpResponse response) throws IOException {
    final int statusCode = response.getCode();
    if (statusCode < HttpStatus.SC_SUCCESS || statusCode >= HttpStatus.SC_REDIRECTION) {
      EntityUtils.consumeQuietly(response.getEntity());
      throw new IOException("HTTP %d for %s".formatted(statusCode, uri));
    }
  }

  private static HttpEntity requireEntity(
      final URI uri,
      final ClassicHttpResponse response) throws IOException {
    return Optional.ofNullable(response.getEntity())
        .orElseThrow(() -> new IOException("Empty response body for " + uri));
  }

  private static byte[] readBoundedBytes(
      final InputStream input,
      final URI uri,
      final int maxBytes) throws IOException {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    copyBounded(input, output, uri, maxBytes);
    return output.toByteArray();
  }

  private static void copyBounded(
      final InputStream input,
      final OutputStream output,
      final URI uri,
      final int maxBytes) throws IOException {
    final byte[] buffer = new byte[HTTP_BUFFER_SIZE];
    int total = 0;
    int read;
    while ((read = input.read(buffer)) >= 0) {
      if (total > maxBytes - read) {
        throw new IOException(
            "HTTP response is too large for %s: maximum %d bytes".formatted(uri, maxBytes));
      }
      output.write(buffer, 0, read);
      total += read;
    }
  }

  public SyncSummary sync() {
    final long startedAtNs = System.nanoTime();
    LOG.info(() -> "Starting external lesson sync: source=%s target=%s".formatted(
        this.indexUri, this.localFolder));
    try (CloseableHttpClient httpClient = httpClient()) {
      this.requireIndexUrl();
      final RemoteFolder remote = this.collectRemoteFolder(httpClient);
      this.logRemoteInventory(remote);
      final SyncSummaryBuilder summary = new SyncSummaryBuilder();

      LOG.info(() -> "Preparing local lesson sync folder: " + this.localFolder);
      this.requireLocalFolderReady();
      LOG.info("Creating lesson directories from external index");
      this.createRemoteDirectories(remote, summary);
      LOG.info(() -> "Downloading or updating %d lesson files".formatted(remote.files().size()));
      this.downloadRemoteFiles(httpClient, remote, summary);
      LOG.info("Removing local lesson files missing from external index");
      this.removeLocalEntriesMissingFrom(remote, summary);
      return this.logSyncCompleted(summary.build(), startedAtNs);
    } catch (final IOException ex) {
      throw new IllegalStateException(
          "Can't sync external resources %s into %s".formatted(this.indexUri, this.localFolder),
          ex);
    }
  }

  private void requireIndexUrl() {
    if (!"https".equalsIgnoreCase(this.indexUri.getScheme())
        || this.indexUri.getHost() == null
        || this.indexUri.getRawQuery() != null
        || this.indexUri.getRawFragment() != null) {
      throw new IllegalArgumentException("Only HTTPS external resource index URLs are supported");
    }
  }

  private RemoteFolder collectRemoteFolder(final CloseableHttpClient httpClient)
      throws IOException {
    LOG.info(() -> "Collecting external lesson inventory from " + this.indexUri);
    final byte[] indexBytes = this.fetchBytes(httpClient, this.indexUri);
    final List<RemoteFile> files = new ArrayList<>();
    final Set<String> directories = new LinkedHashSet<>();
    directories.add("");
    files.add(RemoteFile.inline(INDEX_FILE, this.indexUri, indexBytes));
    this.resourceFiles(indexBytes).forEach(file -> {
      files.add(file);
      directories.addAll(this.parentDirectories(file.relativePath()));
    });
    return RemoteFolder.of(files, directories);
  }

  private List<RemoteFile> resourceFiles(final byte[] indexBytes) {
    final JsonNode index = this.requireIndexWithResourcesArray(this.parseIndex(indexBytes));
    final List<RemoteFile> files = new ArrayList<>();
    for (final JsonNode entry : index.get("resources")) {
      if (entry.isObject() && this.isLeafNode(entry)) {
        final String resource = entry.get("resource").asText();
        files.add(RemoteFile.remote(
            this.localResourcePath(resource),
            this.requireResourceUri(resource)));
      }
    }
    return List.copyOf(files);
  }

  private JsonNode parseIndex(final byte[] indexBytes) {
    try {
      return MAPPER.readTree(new String(indexBytes, StandardCharsets.UTF_8));
    } catch (final IOException ex) {
      throw new IllegalStateException("Invalid external resource index: " + this.indexUri, ex);
    }
  }

  private JsonNode requireIndexWithResourcesArray(final JsonNode root) {
    if (root == null || !root.has("resources") || !root.get("resources").isArray()) {
      throw new IllegalStateException("Invalid external resource index: " + this.indexUri);
    }
    return root;
  }

  private boolean isLeafNode(final JsonNode entry) {
    return entry.has("resource") && entry.get("resource").isValueNode();
  }

  private URI requireResourceUri(final String resource) {
    final URI resolved = this.indexUri.resolve(this.requireRelativeResource(resource)).normalize();
    if (!this.hasSameOrigin(resolved)
        || resolved.getRawQuery() != null
        || resolved.getRawFragment() != null) {
      throw new IllegalStateException("External resource URL must stay under the index origin: "
          + resource);
    }
    return resolved;
  }

  private String requireRelativeResource(final String resource) {
    final String normalized = Optional.ofNullable(resource)
        .map(String::strip)
        .filter(not(String::isEmpty))
        .orElseThrow(() -> new IllegalStateException(
            "Missing resource path in external index: " + this.indexUri));
    if (URI.create(normalized).isAbsolute()) {
      throw new IllegalStateException("External resource path must be relative: " + resource);
    }
    return normalized;
  }

  private boolean hasSameOrigin(final URI uri) {
    return this.indexUri.getScheme().equalsIgnoreCase(uri.getScheme())
        && this.indexUri.getHost().equalsIgnoreCase(uri.getHost())
        && this.indexUri.getPort() == uri.getPort();
  }

  private String localResourcePath(final String resource) {
    final String path = String.join("/", this.normalizedResourceSegments(resource));
    if (!path.endsWith(".json")) {
      throw new IllegalStateException(
          "External resource path must point to a JSON file: " + resource);
    }
    return path;
  }

  private List<String> normalizedResourceSegments(final String resource) {
    final List<String> segments = new ArrayList<>();
    for (final String segment : this.requireRelativeResource(resource).split("/")) {
      if (segment.isEmpty() || ".".equals(segment)) {
        continue;
      }
      if ("..".equals(segment)) {
        if (segments.isEmpty()) {
          throw new IllegalStateException("External resource path escapes local folder: "
              + resource);
        }
        segments.remove(segments.size() - 1);
      } else {
        segments.add(this.requirePortableLocalSegment(segment));
      }
    }
    if (segments.isEmpty()) {
      throw new IllegalStateException("External resource path must not be empty: " + resource);
    }
    return List.copyOf(segments);
  }

  private List<String> parentDirectories(final String relativePath) {
    final List<String> directories = new ArrayList<>();
    final List<String> segments = List.of(relativePath.split("/"));
    for (int i = 1; i < segments.size(); i++) {
      directories.add(String.join("/", segments.subList(0, i)));
    }
    return List.copyOf(directories);
  }

  private byte[] fetchBytes(final CloseableHttpClient httpClient, final URI uri)
      throws IOException {
    return httpClient.execute(new HttpGet(uri), new BinaryResponseHandler(uri));
  }

  private void logRemoteInventory(final RemoteFolder remote) {
    LOG.info(() -> "Collected external lesson inventory: directories=%d files=%d".formatted(
        remote.directories().size(), remote.files().size()));
  }

  private SyncSummary logSyncCompleted(final SyncSummary summary, final long startedAtNs) {
    LOG.info(() -> (
        "Completed external lesson sync: checked=%d loaded=%d updated=%d removed=%d "
            + "elapsedMs=%d").formatted(
        summary.checkedFiles(),
        summary.loadedFiles(),
        summary.updatedFiles(),
        summary.removedFiles(),
        elapsedMillis(startedAtNs)));
    return summary;
  }

  private void requireLocalFolderReady() throws IOException {
    this.requireSafeSyncTarget();
    final boolean folderExists = exists(this.localFolder, NO_LINK_OPTIONS);
    if (folderExists && !isDirectory(this.localFolder, NO_LINK_OPTIONS)) {
      throw new IllegalStateException("Local sync target is not a folder: " + this.localFolder);
    }
    createDirectories(this.localFolder);
    this.requireLangTrainerSyncMarkerOrEmptyFolder(folderExists);
  }

  private void requireSafeSyncTarget() {
    if (this.isRootDirectory()) {
      throw new IllegalStateException("Refusing to sync into filesystem root: " + this.localFolder);
    }
    if (this.isConfiguredHomeDirectory()) {
      throw new IllegalStateException("Refusing to sync into user home: " + this.localFolder);
    }
    if (this.isConfiguredHomeCriticalChild()) {
      throw new IllegalStateException(
          "Refusing to sync into a broad user folder: " + this.localFolder);
    }
    if (this.isCurrentWorkingDirectory()) {
      throw new IllegalStateException(
          "Refusing to sync into application working directory: " + this.localFolder);
    }
    if (this.isLikelyProjectRoot()) {
      throw new IllegalStateException(
          "Refusing to sync into a project root folder: " + this.localFolder);
    }
    if (this.hasSymbolicLocalPathSegment()) {
      throw new IllegalStateException(
          "Refusing to sync through a symbolic link: " + this.localFolder);
    }
  }

  private boolean isRootDirectory() {
    return this.localFolder.getParent() == null;
  }

  private boolean isConfiguredHomeDirectory() {
    return this.localFolder.equals(normalizePath(getUserHome()));
  }

  private boolean isConfiguredHomeCriticalChild() {
    return normalizePath(getUserHome()).equals(this.localFolder.getParent())
        && Optional.ofNullable(this.localFolder.getFileName())
        .map(Path::toString)
        .map(name -> name.toLowerCase(Locale.ROOT))
        .filter(CRITICAL_HOME_CHILD_NAMES::contains)
        .isPresent();
  }

  private boolean isCurrentWorkingDirectory() {
    return this.localFolder.equals(normalizePath(getUserDir()));
  }

  private boolean isLikelyProjectRoot() {
    return this.hasLocalChild(".git")
        || this.hasLocalChild("pom.xml")
        || this.hasLocalChild("build.gradle")
        || this.hasLocalChild("build.gradle.kts")
        || this.hasLocalChild("settings.gradle")
        || this.hasLocalChild("settings.gradle.kts");
  }

  private boolean hasLocalChild(final String name) {
    return exists(this.localFolder.resolve(name), NO_LINK_OPTIONS);
  }

  private boolean hasSymbolicLocalPathSegment() {
    Path current = this.localFolder.getRoot();
    for (final Path segment : this.localFolder) {
      current = current == null ? segment : current.resolve(segment);
      if (exists(current, NO_LINK_OPTIONS) && isSymbolicLink(current)) {
        return true;
      }
    }
    return false;
  }

  private void requireLangTrainerSyncMarkerOrEmptyFolder(final boolean folderExisted)
      throws IOException {
    final Path marker = this.localPath(LOCAL_SYNC_MARKER_FILE);
    if (exists(marker, NO_LINK_OPTIONS)) {
      if (!isRegularFile(marker, NO_LINK_OPTIONS)) {
        throw new IllegalStateException("Invalid LangTrainer sync marker: " + marker);
      }
      return;
    }
    if (folderExisted && !this.isDirectoryEmpty(this.localFolder)) {
      throw new IllegalStateException(
          "Refusing to sync into an existing folder without LangTrainer marker: "
              + this.localFolder);
    }
    writeString(marker, "LangTrainer external resources sync folder\n", StandardCharsets.UTF_8);
  }

  private void createRemoteDirectories(
      final RemoteFolder remote,
      final SyncSummaryBuilder summary) throws IOException {
    for (final String directory : remote.directoriesByDepth()) {
      if (!directory.isEmpty()) {
        this.createLocalDirectory(directory, summary);
      }
    }
  }

  private void createLocalDirectory(
      final String relativePath,
      final SyncSummaryBuilder summary) throws IOException {
    final Path target = this.localPath(relativePath);
    if (exists(target, NO_LINK_OPTIONS)
        && !isDirectory(target, NO_LINK_OPTIONS)
        && deleteIfExists(target)) {
      summary.fileRemoved();
    }
    createDirectories(target);
  }

  private void downloadRemoteFiles(
      final CloseableHttpClient httpClient,
      final RemoteFolder remote,
      final SyncSummaryBuilder summary) throws IOException {
    final ExecutorService executor = Executors.newFixedThreadPool(
        DOWNLOAD_THREAD_LIMIT, downloadThreadFactory());
    try {
      final List<Future<Void>> downloads = remote.files().stream()
          .map(file -> executor.submit(this.downloadTask(httpClient, file, summary)))
          .toList();

      this.awaitDownloads(downloads);
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<Void> downloadTask(
      final CloseableHttpClient httpClient,
      final RemoteFile file,
      final SyncSummaryBuilder summary) {
    return () -> {
      summary.fileChecked();
      this.downloadRemoteFile(httpClient, file, summary);
      return null;
    };
  }

  private void awaitDownloads(final List<Future<Void>> downloads) throws IOException {
    for (final Future<Void> download : downloads) {
      try {
        download.get();
      } catch (final InterruptedException ex) {
        Thread.currentThread().interrupt();
        this.cancelDownloads(downloads);
        throw new IOException("External resource download was interrupted", ex);
      } catch (final ExecutionException ex) {
        this.cancelDownloads(downloads);
        this.throwDownloadFailure(ex.getCause() == null ? ex : ex.getCause());
      }
    }
  }

  private void cancelDownloads(final List<Future<Void>> downloads) {
    downloads.forEach(download -> download.cancel(true));
  }

  private void throwDownloadFailure(final Throwable cause) throws IOException {
    if (cause instanceof final IOException ioException) {
      throw ioException;
    }
    if (cause instanceof final RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (cause instanceof final Error error) {
      throw error;
    }
    throw new IllegalStateException("External resource download failed", cause);
  }

  private void downloadRemoteFile(
      final CloseableHttpClient httpClient,
      final RemoteFile file,
      final SyncSummaryBuilder summary) throws IOException {
    LOG.fine(() -> "Syncing lesson file: " + file.relativePath());
    final Path target = this.localPath(file.relativePath());
    this.ensureWritableParent(target);
    if (exists(target, NO_LINK_OPTIONS) && isDirectory(target, NO_LINK_OPTIONS)) {
      summary.addRemovedFiles(this.deleteRecursively(target));
    }

    final Path tempFile = createTempFile(target.getParent(), ".external-sync-", ".tmp");
    try {
      this.downloadIntoTempFile(httpClient, file, tempFile);
      this.moveDownloadedFile(tempFile, target, summary);
    } finally {
      deleteIfExists(tempFile);
    }
  }

  private void downloadIntoTempFile(
      final CloseableHttpClient httpClient,
      final RemoteFile file,
      final Path tempFile) throws IOException {
    if (file.content().isPresent()) {
      write(tempFile, file.content().orElseThrow());
      return;
    }
    httpClient.execute(new HttpGet(file.uri()), new FileResponseHandler(file.uri(), tempFile));
  }

  private void moveDownloadedFile(
      final Path tempFile,
      final Path target,
      final SyncSummaryBuilder summary) throws IOException {
    if (!exists(target, NO_LINK_OPTIONS)) {
      this.moveIntoPlace(tempFile, target);
      summary.fileLoaded();
      LOG.fine(() -> "Loaded new lesson file: " + target);
      return;
    }
    if (isRegularFile(target, NO_LINK_OPTIONS) && mismatch(tempFile, target) == -1L) {
      LOG.fine(() -> "Lesson file unchanged: " + target);
      return;
    }
    this.moveIntoPlace(tempFile, target);
    summary.fileUpdated();
    LOG.fine(() -> "Updated lesson file: " + target);
  }

  private void ensureWritableParent(final Path target) throws IOException {
    final Path parent = target.getParent();
    if (exists(parent, NO_LINK_OPTIONS) && !isDirectory(parent, NO_LINK_OPTIONS)) {
      deleteIfExists(parent);
    }
    createDirectories(parent);
  }

  private void moveIntoPlace(final Path tempFile, final Path target) throws IOException {
    try {
      move(tempFile, target, REPLACE_EXISTING, ATOMIC_MOVE);
    } catch (final AtomicMoveNotSupportedException ignored) {
      move(tempFile, target, REPLACE_EXISTING);
    }
  }

  private void removeLocalEntriesMissingFrom(
      final RemoteFolder remote,
      final SyncSummaryBuilder summary) throws IOException {
    try (Stream<Path> paths = walk(this.localFolder)) {
      for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        if (!this.localFolder.equals(path)) {
          this.removeLocalEntryIfMissing(path, remote, summary);
        }
      }
    }
  }

  private void removeLocalEntryIfMissing(
      final Path path,
      final RemoteFolder remote,
      final SyncSummaryBuilder summary) throws IOException {
    final String relativePath = this.localRelativePath(path);
    if (LOCAL_SYNC_MARKER_FILE.equals(relativePath)) {
      return;
    }
    if (isDirectory(path, NO_LINK_OPTIONS)) {
      if (!remote.directories().contains(relativePath) && this.isDirectoryEmpty(path)) {
        deleteIfExists(path);
        LOG.fine(() -> "Removed empty stale lesson directory: " + path);
      }
    } else if (!remote.filePaths().contains(relativePath) && deleteIfExists(path)) {
      summary.fileRemoved();
      LOG.fine(() -> "Removed stale lesson file: " + path);
    }
  }

  private int deleteRecursively(final Path target) throws IOException {
    if (!exists(target, NO_LINK_OPTIONS)) {
      return 0;
    }
    int removedFiles = 0;
    try (Stream<Path> paths = walk(target)) {
      for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        if (!isDirectory(path, NO_LINK_OPTIONS) && deleteIfExists(path)) {
          removedFiles++;
          LOG.fine(() -> "Removed replaced lesson file: " + path);
        } else {
          deleteIfExists(path);
          LOG.fine(() -> "Removed replaced lesson directory: " + path);
        }
      }
    }
    return removedFiles;
  }

  private boolean isDirectoryEmpty(final Path path) throws IOException {
    try (Stream<Path> children = list(path)) {
      return children.findAny().isEmpty();
    }
  }

  private Path localPath(final String relativePath) {
    Path resolved = this.localFolder;
    for (final String segment : this.requirePortableRelativePath(relativePath)) {
      resolved = resolved.resolve(segment);
    }
    resolved = resolved.normalize();
    if (!resolved.startsWith(this.localFolder)) {
      throw new IllegalStateException("External resource path escapes local folder: "
          + relativePath);
    }
    return resolved;
  }

  private List<String> requirePortableRelativePath(final String relativePath) {
    final List<String> segments = Stream.of(relativePath.split("/"))
        .map(this::requirePortableLocalSegment)
        .toList();
    if (segments.isEmpty()) {
      throw new IllegalStateException("External resource path must not be empty");
    }
    return segments;
  }

  private String requirePortableLocalSegment(final String segment) {
    if (isEmpty(segment) || ".".equals(segment) || "..".equals(segment)) {
      throw new IllegalStateException("Invalid external resource path segment: " + segment);
    }
    if (containsAny(segment, '/', '\\') || hasControlCharacter(segment)) {
      throw new IllegalStateException("Unsupported external resource path segment: " + segment);
    }
    if (hasWindowsUnsafeName(segment)) {
      throw new IllegalStateException(
          "External resource path segment is not portable across supported OSes: " + segment);
    }
    return segment;
  }

  private String localRelativePath(final Path path) {
    return this.localFolder.relativize(path)
        .toString()
        .replace(path.getFileSystem().getSeparator(), "/");
  }

  public record SyncSummary(int checkedFiles, int loadedFiles, int updatedFiles, int removedFiles) {
  }

  private static final class SyncSummaryBuilder {
    private final AtomicInteger checkedFiles = new AtomicInteger();
    private final AtomicInteger loadedFiles = new AtomicInteger();
    private final AtomicInteger updatedFiles = new AtomicInteger();
    private final AtomicInteger removedFiles = new AtomicInteger();

    private void fileChecked() {
      this.checkedFiles.incrementAndGet();
    }

    private void fileLoaded() {
      this.loadedFiles.incrementAndGet();
    }

    private void fileRemoved() {
      this.removedFiles.incrementAndGet();
    }

    private void fileUpdated() {
      this.updatedFiles.incrementAndGet();
    }

    private void addRemovedFiles(final int removedFiles) {
      this.removedFiles.addAndGet(removedFiles);
    }

    private SyncSummary build() {
      return new SyncSummary(
          this.checkedFiles.get(),
          this.loadedFiles.get(),
          this.updatedFiles.get(),
          this.removedFiles.get());
    }
  }

  private record BinaryResponseHandler(URI uri) implements HttpClientResponseHandler<byte[]> {

    private BinaryResponseHandler(final URI uri) {
      this.uri = requireNonNull(uri, "uri must not be null");
    }

    @Override
    public byte[] handleResponse(final ClassicHttpResponse response) throws IOException {
      requireSuccessfulResponse(this.uri, response);
      final HttpEntity entity = requireEntity(this.uri, response);
      try (InputStream input = entity.getContent()) {
        return readBoundedBytes(input, this.uri, MAX_INDEX_BYTES);
      } finally {
        EntityUtils.consumeQuietly(entity);
      }
    }
  }

  private static final class FileResponseHandler implements HttpClientResponseHandler<Void> {

    private final URI uri;
    private final Path target;

    private FileResponseHandler(final URI uri, final Path target) {
      this.uri = requireNonNull(uri, "uri must not be null");
      this.target = requireNonNull(target, "target must not be null");
    }

    @Override
    public Void handleResponse(final ClassicHttpResponse response) throws IOException {
      requireSuccessfulResponse(this.uri, response);
      final HttpEntity entity = requireEntity(this.uri, response);
      try (InputStream input = entity.getContent();
           OutputStream output = newOutputStream(this.target, WRITE, TRUNCATE_EXISTING)) {
        copyBounded(input, output, this.uri, MAX_RESOURCE_BYTES);
      } finally {
        EntityUtils.consumeQuietly(entity);
      }
      return null;
    }
  }

  private record RemoteFile(String relativePath, URI uri, Optional<byte[]> content) {

    private RemoteFile {
      requireNonNull(relativePath, "relativePath must not be null");
      requireNonNull(uri, "uri must not be null");
      requireNonNull(content, "content must not be null");
    }

    private static RemoteFile inline(
        final String relativePath, final URI uri, final byte[] content) {
      return new RemoteFile(relativePath, uri, Optional.of(content.clone()));
    }

    private static RemoteFile remote(final String relativePath, final URI uri) {
      return new RemoteFile(relativePath, uri, Optional.empty());
    }
  }

  private record RemoteFolder(List<RemoteFile> files, Set<String> directories,
                              Set<String> filePaths) {

    private static RemoteFolder of(final List<RemoteFile> files, final Set<String> directories) {
      final List<RemoteFile> copiedFiles = List.copyOf(files);
      requireNoCaseInsensitivePathCollision(copiedFiles, directories);
      return new RemoteFolder(
          copiedFiles,
          Set.copyOf(directories),
          copiedFiles.stream().map(RemoteFile::relativePath).collect(toUnmodifiableSet()));
    }

    private static void requireNoCaseInsensitivePathCollision(
        final List<RemoteFile> files, final Set<String> directories) {
      final Set<String> normalizedPaths = new HashSet<>();
      for (final String directory : directories) {
        requireNoCaseInsensitivePathCollision(normalizedPaths, directory);
      }
      for (final RemoteFile file : files) {
        requireNoCaseInsensitivePathCollision(normalizedPaths, file.relativePath());
      }
    }

    private static void requireNoCaseInsensitivePathCollision(
        final Set<String> normalizedPaths, final String path) {
      if (LOCAL_SYNC_MARKER_FILE.equals(path)) {
        throw new IllegalStateException(
            "External resource index contains reserved local sync marker: " + path);
      }
      final String normalizedPath = path.toLowerCase(Locale.ROOT);
      if (!path.isEmpty() && !normalizedPaths.add(normalizedPath)) {
        throw new IllegalStateException(
            "External resource index contains case-insensitive path collision: " + path);
      }
    }

    private List<String> directoriesByDepth() {
      return this.directories.stream()
          .sorted(Comparator.comparingInt(path -> path.isEmpty() ? 0 : path.split("/").length))
          .toList();
    }
  }
}
