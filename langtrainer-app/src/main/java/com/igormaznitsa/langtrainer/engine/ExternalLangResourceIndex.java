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
import java.util.List;
import java.util.Optional;

public final class ExternalLangResourceIndex {

  private static final String SHARED_INDEX = "common/jsons/index.json";
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
    final Path indexPath = root.resolve(SHARED_INDEX).normalize();
    if (!isRegularFile(indexPath, NO_LINK_OPTIONS) || !indexPath.startsWith(root)) {
      return ClasspathResourceIndexTree.empty();
    }
    try {
      return loadTreeFromIndex(root, module, indexPath);
    } catch (final Exception ex) {
      System.err.println("Can't load external resources from " + root + ": " + ex.getMessage());
      return ClasspathResourceIndexTree.empty();
    }
  }

  private static ClasspathResourceIndexTree loadTreeFromIndex(
      final Path root, final AbstractLangTrainerModule module, final Path indexPath)
      throws IOException {
    final JsonObject index = requireIndexWithResourcesArray(
        GSON.fromJson(readString(indexPath, StandardCharsets.UTF_8), JsonObject.class),
        indexPath);
    final List<DialogDefinition> definitions = new ArrayList<>();
    for (final JsonElement el : index.get("resources").getAsJsonArray()) {
      if (!el.isJsonObject() || !isLeafNode(el.getAsJsonObject())) {
        continue;
      }
      loadDefinition(root, indexPath, el.getAsJsonObject().get("resource").getAsString())
          .filter(module::isResourceAllowed)
          .ifPresent(definitions::add);
    }
    return LangResourceIndexTrees.fromDefinitions(module, definitions);
  }

  private static Optional<DialogDefinition> loadDefinition(
      final Path root, final Path indexPath, final String resourcePath) {
    final Path path = localResourcePath(root, indexPath, resourcePath);
    if (!isRegularFile(path, NO_LINK_OPTIONS)) {
      return Optional.empty();
    }
    try {
      return Optional.of(LangResourceJson.parseFromPath(path));
    } catch (final Exception ex) {
      System.err.println("Can't load external resource " + path + ": " + ex.getMessage());
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
