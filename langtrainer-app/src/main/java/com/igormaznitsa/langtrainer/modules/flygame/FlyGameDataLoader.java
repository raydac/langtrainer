package com.igormaznitsa.langtrainer.modules.flygame;

import com.igormaznitsa.langtrainer.engine.ClasspathLangResourceIndex;
import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.LangResourceJson;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class FlyGameDataLoader {

  private static final Pattern FILE_PATTERN = Pattern.compile("^fly_game/(.+)\\.json$");

  private FlyGameDataLoader() {
  }

  public static List<DialogDefinition> loadAll() {
    return ClasspathLangResourceIndex.loadAll(
        FlyGameDataLoader.class,
        "/fly_game/index.txt",
        FILE_PATTERN,
        "Can't load fly game word lists");
  }

  public static DialogDefinition loadFromFile(final Path path) {
    return LangResourceJson.parseFromPath(path);
  }
}
