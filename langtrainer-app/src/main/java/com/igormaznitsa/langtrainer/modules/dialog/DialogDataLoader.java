package com.igormaznitsa.langtrainer.modules.dialog;

import com.igormaznitsa.langtrainer.engine.ClasspathLangResourceIndex;
import com.igormaznitsa.langtrainer.engine.DialogDefinition;
import com.igormaznitsa.langtrainer.engine.LangResourceJson;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class DialogDataLoader {

  private static final Pattern FILE_PATTERN = Pattern.compile("^dialogs/(.+)\\.json$");

  private DialogDataLoader() {
  }

  public static List<DialogDefinition> loadAll() {
    return ClasspathLangResourceIndex.loadAll(
        DialogDataLoader.class,
        "/dialogs/index.txt",
        FILE_PATTERN,
        "Can't load dialog definitions");
  }

  public static DialogDefinition loadFromFile(final Path path) {
    return LangResourceJson.parseFromPath(path);
  }
}
