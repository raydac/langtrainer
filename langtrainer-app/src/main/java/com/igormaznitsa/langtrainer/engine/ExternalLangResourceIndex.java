package com.igormaznitsa.langtrainer.engine;

import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readString;
import static java.util.Objects.requireNonNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExternalLangResourceIndex {

  private static final Logger LOG = Logger.getLogger(ExternalLangResourceIndex.class.getName());
  private static final String SHARED_INDEX = "common/jsons/index.json";
  private static final String ROOT_INDEX = "index.json";
  private static final LinkOption[] NO_LINK_OPTIONS = {LinkOption.NOFOLLOW_LINKS};
  private static final Gson GSON = new Gson();

  private ExternalLangResourceIndex() {
  }

  public static ClasspathResourceIndexTree loadSharedTree(
      final Path externalRoot, final AbstractLangTrainerModule module) {
    requireNonNull(module, "module must not be null");
    final Path root = requireNonNull(externalRoot, "externalRoot must not be null")
        .toAbsolutePath()
        .normalize();
    final List<Path> indexPaths = resolveExternalIndexes(root);
    if (indexPaths.isEmpty()) {
      return ClasspathResourceIndexTree.empty();
    }
    final List<DialogDefinition> definitions = new ArrayList<>();
    final Set<Path> loadedResourcePaths = new LinkedHashSet<>();
    for (final Path indexPath : indexPaths) {
      try {
        definitions.addAll(loadDefinitionsFromIndex(root, module, indexPath, loadedResourcePaths));
      } catch (final Exception ex) {
        LOG.log(Level.WARNING, "Can't load external resources from " + indexPath, ex);
      }
    }
    return LangResourceIndexTrees.fromDefinitions(module, definitions);
  }

  private static List<Path> resolveExternalIndexes(final Path root) {
    return List.of(SHARED_INDEX, ROOT_INDEX).stream()
        .map(root::resolve)
        .map(Path::normalize)
        .filter(path -> path.startsWith(root))
        .filter(path -> isRegularFile(path, NO_LINK_OPTIONS))
        .toList();
  }

  private static List<DialogDefinition> loadDefinitionsFromIndex(
      final Path root,
      final AbstractLangTrainerModule module,
      final Path indexPath,
      final Set<Path> loadedResourcePaths)
      throws IOException {
    final JsonObject index = requireIndexWithResourcesArray(
        GSON.fromJson(readString(indexPath, StandardCharsets.UTF_8), JsonObject.class),
        indexPath);
    final List<DialogDefinition> definitions = new ArrayList<>();
    for (final JsonElement el : index.get("resources").getAsJsonArray()) {
      if (!el.isJsonObject() || !isLeafNode(el.getAsJsonObject())) {
        continue;
      }
      final Path resourcePath =
          localResourcePath(root, indexPath, el.getAsJsonObject().get("resource").getAsString());
      if (!loadedResourcePaths.add(resourcePath)) {
        continue;
      }
      loadDefinition(resourcePath)
          .filter(module::isResourceAllowed)
          .ifPresent(definitions::add);
    }
    return List.copyOf(definitions);
  }

  private static Optional<DialogDefinition> loadDefinition(final Path path) {
    if (!isRegularFile(path, NO_LINK_OPTIONS)) {
      return Optional.empty();
    }
    try {
      return Optional.of(LangResourceJson.parseFromPath(path));
    } catch (final Exception ex) {
      LOG.log(Level.WARNING, "Can't load external resource " + path, ex);
      return Optional.empty();
    }
  }

  private static Path localResourcePath(
      final Path root, final Path indexPath, final String resourcePath) {
    if (resourcePath == null || resourcePath.isBlank()) {
      throw new IllegalStateException("Missing resource path in " + indexPath);
    }
    final String normalized = resourcePath.strip();
    final Path path = normalized.startsWith("/")
        ? root.resolve(normalized.substring(1)).normalize()
        : indexPath.getParent().resolve(normalized).normalize();
    if (!path.startsWith(root)) {
      throw new IllegalStateException(
          "External resource path escapes local folder: " + resourcePath);
    }
    if (!path.getFileName().toString().endsWith(".json")) {
      throw new IllegalStateException(
          "External resource path must point to a JSON file: " + resourcePath);
    }
    return path;
  }

  private static JsonObject requireIndexWithResourcesArray(
      final JsonObject root, final Path indexPath) {
    if (root == null || !root.has("resources") || !root.get("resources").isJsonArray()) {
      throw new IllegalStateException("Invalid external resource index: " + indexPath);
    }
    return root;
  }

  private static boolean isLeafNode(final JsonObject entry) {
    return entry.has("resource") && entry.get("resource").isJsonPrimitive();
  }
}
