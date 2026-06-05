package com.igormaznitsa.langtrainer.engine;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.mismatch;
import static java.nio.file.Files.move;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.writeString;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.apache.commons.lang3.StringUtils.containsAny;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.apache.commons.lang3.Strings.CS;
import static org.apache.commons.lang3.SystemUtils.getUserDir;
import static org.apache.commons.lang3.SystemUtils.getUserHome;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import org.apache.commons.io.IOUtils;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;

public final class GitHubFolderSynchronizer {

  private static final Logger LOG = Logger.getLogger(GitHubFolderSynchronizer.class.getName());
  private static final String GITHUB_HOST = "github.com";
  private static final String TREE_SEGMENT = "tree";
  private static final String WINDOWS_FORBIDDEN_CHARS = "<>:\"|?*";
  private static final Set<String> WINDOWS_RESERVED_NAMES =
      Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6",
          "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6",
          "LPT7", "LPT8", "LPT9");
  private static final Set<String> CRITICAL_HOME_CHILD_NAMES =
      Set.of("desktop", "documents", "downloads", "music", "pictures", "projects", "videos",
          "work", "workspace");
  private static final String LOCAL_SYNC_MARKER_FILE = ".langtrainer-external-sync";
  private static final String LANGTRAINER_GITHUB_TOKEN = "LANGTRAINER_GITHUB_TOKEN";
  private static final String GITHUB_TOKEN = "GITHUB_TOKEN";
  private static final LinkOption[] NO_LINK_OPTIONS = {LinkOption.NOFOLLOW_LINKS};
  private static final Duration GITHUB_CONNECT_TIMEOUT = Duration.ofSeconds(10L);
  private static final Duration GITHUB_READ_TIMEOUT = Duration.ofSeconds(20L);
  private static final Duration GITHUB_WRITE_TIMEOUT = Duration.ofSeconds(10L);
  private static final Duration GITHUB_CALL_TIMEOUT = Duration.ofSeconds(30L);

  private final URI githubFolderUri;
  private final Path localFolder;

  public GitHubFolderSynchronizer(final String githubFolderUrl, final Path localFolder) {
    this(URI.create(requireNonNull(githubFolderUrl, "githubFolderUrl must not be null")),
        localFolder);
  }

  public GitHubFolderSynchronizer(final URI githubFolderUri, final Path localFolder) {
    this.githubFolderUri = requireNonNull(githubFolderUri, "githubFolderUri must not be null");
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

  private static OkHttpClient okHttpClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(GITHUB_CONNECT_TIMEOUT)
        .readTimeout(GITHUB_READ_TIMEOUT)
        .writeTimeout(GITHUB_WRITE_TIMEOUT)
        .callTimeout(GITHUB_CALL_TIMEOUT)
        .build();
  }

  private FolderSource parseFolderSource() {
    this.requireGitHubUrl();
    final List<String> segments = this.pathSegments();
    if (segments.size() < 3) {
      throw new IllegalArgumentException(
          "GitHub folder URL must include owner, repository, and path");
    }
    if (TREE_SEGMENT.equals(segments.get(2))) {
      return this.parseTreeFolderSource(segments);
    }
    return new FolderSource(
        this.repositoryFullName(segments),
        this.joinPath(segments.subList(2, segments.size())),
        null);
  }

  private FolderSource parseTreeFolderSource(final List<String> segments) {
    if (segments.size() < 5) {
      throw new IllegalArgumentException("GitHub tree URL must include branch and folder path");
    }
    return new FolderSource(
        this.repositoryFullName(segments),
        this.joinPath(segments.subList(4, segments.size())),
        segments.get(3));
  }

  private static Path normalizePath(final Path path) {
    return path.toAbsolutePath().normalize();
  }

  private static Path normalizePath(final File file) {
    return normalizePath(file.toPath());
  }

  private void requireGitHubUrl() {
    if (!"https".equalsIgnoreCase(this.githubFolderUri.getScheme())
        || !GITHUB_HOST.equalsIgnoreCase(this.githubFolderUri.getHost())) {
      throw new IllegalArgumentException("Only https://github.com folder URLs are supported");
    }
  }

  private String repositoryFullName(final List<String> segments) {
    return segments.get(0) + '/' + this.stripGitSuffix(segments.get(1));
  }

  private List<String> pathSegments() {
    return Stream.of(this.githubFolderUri.getPath().split("/"))
        .filter(not(org.apache.commons.lang3.StringUtils::isBlank))
        .map(this::requireValidUrlPathSegment)
        .toList();
  }

  private String joinPath(final List<String> segments) {
    if (segments.isEmpty()) {
      throw new IllegalArgumentException("GitHub folder path must not be empty");
    }
    return String.join("/", segments);
  }

  private static Optional<String> configuredSecret(final String name) {
    return Optional.ofNullable(stripToNull(System.getProperty(name)))
        .or(() -> Optional.of(readGat()));
  }

  private static String readGat() {
    try {
      final String resource = IOUtils.resourceToString("/common/gat.txt", StandardCharsets.UTF_8);
      return new String(Base64.getDecoder().decode(resource), StandardCharsets.UTF_8);
    } catch (final IOException ex) {
      throw new IllegalStateException("Can't read GAT resource", ex);
    }
  }

  private static long elapsedMillis(final long startedAtNs) {
    return Duration.ofNanos(System.nanoTime() - startedAtNs).toMillis();
  }

  public SyncSummary sync() {
    final long startedAtNs = System.nanoTime();
    LOG.info(() -> "Starting GitHub lesson sync: source=%s target=%s".formatted(
        this.githubFolderUri, this.localFolder));
    try {
      final FolderSource source = this.parseFolderSource();
      this.logResolvedSource(source);
      final RemoteFolder remote = this.collectRemoteFolder(
          this.connectToGitHub().getRepository(source.repositoryFullName()),
          source);
      this.logRemoteInventory(remote);
      final SyncSummaryBuilder summary = new SyncSummaryBuilder();

      LOG.info(() -> "Preparing local lesson sync folder: " + this.localFolder);
      this.requireLocalFolderReady();
      LOG.info("Creating lesson directories from GitHub inventory");
      this.createRemoteDirectories(remote, summary);
      LOG.info(() -> "Downloading or updating %d lesson files".formatted(remote.files().size()));
      this.downloadRemoteFiles(remote, summary);
      LOG.info("Removing local lesson files missing from GitHub inventory");
      this.removeLocalEntriesMissingFrom(remote, summary);
      return this.logSyncCompleted(summary.build(), startedAtNs);
    } catch (final IOException ex) {
      throw new IllegalStateException(
          "Can't sync GitHub folder %s into %s".formatted(this.githubFolderUri, this.localFolder),
          ex);
    }
  }

  private RemoteFolder collectRemoteFolder(final GHRepository repository, final FolderSource source)
      throws IOException {
    LOG.info(() -> "Collecting GitHub lesson inventory from %s/%s".formatted(
        source.repositoryFullName(), source.remotePath()));
    final List<RemoteFile> files = new ArrayList<>();
    final Set<String> directories = new LinkedHashSet<>();
    directories.add("");
    this.collectRemoteDirectory(repository, source, source.remotePath(), files, directories);
    return RemoteFolder.of(files, directories);
  }

  private GitHub connectToGitHub() throws IOException {
    LOG.info(() -> "Connecting to GitHub with timeouts: connectMs=%d readMs=%d writeMs=%d callMs=%d"
        .formatted(
            GITHUB_CONNECT_TIMEOUT.toMillis(),
            GITHUB_READ_TIMEOUT.toMillis(),
            GITHUB_WRITE_TIMEOUT.toMillis(),
            GITHUB_CALL_TIMEOUT.toMillis()));
    final GitHubBuilder builder = new GitHubBuilder()
        .withConnector(new OkHttpGitHubConnector(okHttpClient()));
    this.configuredGitHubToken().ifPresent(builder::withOAuthToken);
    LOG.info(() -> "GitHub lesson sync authentication: " + this.githubAuthenticationMode());
    return builder.build();
  }

  private Optional<String> configuredGitHubToken() {
    return configuredSecret(LANGTRAINER_GITHUB_TOKEN).or(() -> configuredSecret(GITHUB_TOKEN));
  }

  private String githubAuthenticationMode() {
    return this.configuredGitHubToken().isPresent() ? "token" : "anonymous";
  }

  private void logResolvedSource(final FolderSource source) {
    LOG.info(() -> "Resolved GitHub lesson source: repository=%s path=%s ref=%s".formatted(
        source.repositoryFullName(),
        source.remotePath(),
        source.ref() == null ? "<default>" : source.ref()));
  }

  private void logRemoteInventory(final RemoteFolder remote) {
    LOG.info(() -> "Collected GitHub lesson inventory: directories=%d files=%d".formatted(
        remote.directories().size(), remote.files().size()));
  }

  private SyncSummary logSyncCompleted(final SyncSummary summary, final long startedAtNs) {
    LOG.info(() -> "Completed GitHub lesson sync: loaded=%d updated=%d removed=%d elapsedMs=%d"
        .formatted(
            summary.loadedFiles(),
            summary.updatedFiles(),
            summary.removedFiles(),
            elapsedMillis(startedAtNs)));
    return summary;
  }

  private void collectRemoteDirectory(
      final GHRepository repository,
      final FolderSource source,
      final String directoryPath,
      final List<RemoteFile> files,
      final Set<String> directories) throws IOException {
    for (final GHContent content : repository.getDirectoryContent(directoryPath, source.ref())) {
      final String relativePath = this.relativeRemotePath(source.remotePath(), content.getPath());
      if (content.isDirectory()) {
        directories.add(relativePath);
        this.collectRemoteDirectory(repository, source, content.getPath(), files, directories);
      } else if (content.isFile()) {
        files.add(new RemoteFile(relativePath, content));
      } else {
        throw new IllegalStateException("Unsupported GitHub content entry: " + content.getPath());
      }
    }
  }

  private String relativeRemotePath(final String remoteRootPath, final String contentPath) {
    if (!contentPath.startsWith(remoteRootPath + '/')) {
      throw new IllegalStateException(
          "GitHub content path is outside selected folder: " + contentPath);
    }
    final String relativePath = contentPath.substring(remoteRootPath.length() + 1);
    this.requirePortableRelativePath(relativePath);
    return relativePath;
  }

  private String requireValidUrlPathSegment(final String segment) {
    if ("..".equals(segment) || containsAny(segment, '\\')) {
      throw new IllegalArgumentException("Invalid GitHub URL path segment: " + segment);
    }
    return segment;
  }

  private String stripGitSuffix(final String repoName) {
    return CS.removeEnd(repoName, ".git");
  }

  private void requireLocalFolderReady() throws IOException {
    this.requireSafeSyncTarget();
    if (exists(this.localFolder, NO_LINK_OPTIONS)
        && !isDirectory(this.localFolder, NO_LINK_OPTIONS)) {
      throw new IllegalStateException("Local sync target is not a folder: " + this.localFolder);
    }
    createDirectories(this.localFolder);
    this.ensureLangTrainerSyncMarker();
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

  private void ensureLangTrainerSyncMarker() throws IOException {
    final Path marker = this.localPath(LOCAL_SYNC_MARKER_FILE);
    if (exists(marker, NO_LINK_OPTIONS)) {
      if (!isRegularFile(marker, NO_LINK_OPTIONS)) {
        throw new IllegalStateException("Invalid LangTrainer sync marker: " + marker);
      }
      return;
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

  private void downloadRemoteFiles(final RemoteFolder remote, final SyncSummaryBuilder summary)
      throws IOException {
    for (final RemoteFile file : remote.files()) {
      this.downloadRemoteFile(file, summary);
    }
  }

  private void downloadRemoteFile(final RemoteFile file, final SyncSummaryBuilder summary)
      throws IOException {
    LOG.fine(() -> "Syncing lesson file: " + file.relativePath());
    final Path target = this.localPath(file.relativePath());
    this.ensureWritableParent(target);
    if (exists(target, NO_LINK_OPTIONS) && isDirectory(target, NO_LINK_OPTIONS)) {
      summary.addRemovedFiles(this.deleteRecursively(target));
    }

    final Path tempFile = createTempFile(target.getParent(), ".github-sync-", ".tmp");
    try (InputStream input = file.content().read()) {
      copy(input, tempFile, REPLACE_EXISTING);
      this.moveDownloadedFile(tempFile, target, summary);
    } finally {
      deleteIfExists(tempFile);
    }
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

  public record SyncSummary(int loadedFiles, int removedFiles, int updatedFiles) {
  }

  private static final class SyncSummaryBuilder {
    private int loadedFiles;
    private int removedFiles;
    private int updatedFiles;

    private void fileLoaded() {
      this.loadedFiles++;
    }

    private void fileRemoved() {
      this.removedFiles++;
    }

    private void fileUpdated() {
      this.updatedFiles++;
    }

    private void addRemovedFiles(final int removedFiles) {
      this.removedFiles += removedFiles;
    }

    private SyncSummary build() {
      return new SyncSummary(this.loadedFiles, this.removedFiles, this.updatedFiles);
    }
  }

  private boolean isDirectoryEmpty(final Path path) throws IOException {
    try (Stream<Path> children = walk(path, 1)) {
      return children.count() == 1L;
    }
  }

  private Path localPath(final String relativePath) {
    Path resolved = this.localFolder;
    for (final String segment : this.requirePortableRelativePath(relativePath)) {
      resolved = resolved.resolve(segment);
    }
    resolved = resolved.normalize();
    if (!resolved.startsWith(this.localFolder)) {
      throw new IllegalStateException("GitHub content path escapes local folder: " + relativePath);
    }
    return resolved;
  }

  private List<String> requirePortableRelativePath(final String relativePath) {
    final List<String> segments = Stream.of(relativePath.split("/"))
        .map(this::requirePortableLocalSegment)
        .toList();
    if (segments.isEmpty()) {
      throw new IllegalStateException("GitHub content path must not be empty");
    }
    return segments;
  }

  private String requirePortableLocalSegment(final String segment) {
    if (isEmpty(segment) || ".".equals(segment) || "..".equals(segment)) {
      throw new IllegalStateException("Invalid GitHub content path segment: " + segment);
    }
    if (containsAny(segment, '/', '\\') || hasControlCharacter(segment)) {
      throw new IllegalStateException("Unsupported GitHub content path segment: " + segment);
    }
    if (hasWindowsUnsafeName(segment)) {
      throw new IllegalStateException(
          "GitHub content path segment is not portable across supported OSes: " + segment);
    }
    return segment;
  }

  private String localRelativePath(final Path path) {
    return this.localFolder.relativize(path)
        .toString()
        .replace(path.getFileSystem().getSeparator(), "/");
  }

  private record FolderSource(String repositoryFullName, String remotePath, String ref) {
  }

  private record RemoteFile(String relativePath, GHContent content) {
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
            "GitHub folder contains reserved local sync marker: " + path);
      }
      final String normalizedPath = path.toLowerCase(Locale.ROOT);
      if (!path.isEmpty() && !normalizedPaths.add(normalizedPath)) {
        throw new IllegalStateException(
            "GitHub folder contains case-insensitive path collision: " + path);
      }
    }

    private List<String> directoriesByDepth() {
      return this.directories.stream()
          .sorted(Comparator.comparingInt(path -> path.isEmpty() ? 0 : path.split("/").length))
          .toList();
    }
  }
}
