package com.igormaznitsa.langtrainer.modules.flygame;

import com.igormaznitsa.langtrainer.modules.dialog.DialogDataLoader;
import com.igormaznitsa.langtrainer.modules.dialog.DialogDefinition;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class FlyGameDataLoader {

  private static final Pattern FILE_PATTERN = Pattern.compile("^fly_game/(.+)\\.json$");

  private FlyGameDataLoader() {
  }

  public static List<DialogDefinition> loadAll() {
    final List<DialogDefinition> result = new ArrayList<>();
    try (InputStream indexStream = FlyGameDataLoader.class.getResourceAsStream(
        "/fly_game/index.txt")) {
      if (indexStream == null) {
        throw new IllegalStateException("Resource not found: /fly_game/index.txt");
      }
      final String index = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
      index.lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .filter(line -> FILE_PATTERN.matcher(line).matches())
          .map(FlyGameDataLoader::loadOne)
          .sorted(Comparator.comparing(DialogDefinition::menuName))
          .forEach(result::add);
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load fly game word lists", ex);
    }
    return result;
  }

  private static DialogDefinition loadOne(final String resourcePath) {
    final String text;
    try (InputStream stream = FlyGameDataLoader.class.getResourceAsStream("/" + resourcePath)) {
      if (stream == null) {
        throw new IllegalStateException("Resource not found: /" + resourcePath);
      }
      text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load " + resourcePath, ex);
    }
    return DialogDataLoader.parseDialogJson(text);
  }

  public static DialogDefinition loadFromFile(final Path path) {
    try {
      final String text = Files.readString(path, StandardCharsets.UTF_8);
      return DialogDataLoader.parseDialogJson(text);
    } catch (Exception ex) {
      throw new IllegalStateException("Can't load fly game file: " + path, ex);
    }
  }
}
