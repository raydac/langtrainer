package com.igormaznitsa.langtrainer;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.modules.crossword.CrosswordModule;
import com.igormaznitsa.langtrainer.modules.dialog.DialogModule;
import com.igormaznitsa.langtrainer.modules.editor.EditorModule;
import com.igormaznitsa.langtrainer.modules.flygame.FlyGameModule;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public enum Modules {
  DIALOG(DialogModule::new),
  FLY_GAME(FlyGameModule::new),
  CROSSWORD(CrosswordModule::new),
  EDITOR(EditorModule::new);

  private final Supplier<AbstractLangTrainerModule> moduleFactory;

  Modules(final Supplier<AbstractLangTrainerModule> moduleFactory) {
    this.moduleFactory = moduleFactory;
  }

  public static List<AbstractLangTrainerModule> createAll() {
    return Arrays.stream(values())
        .map(Modules::createModule)
        .toList();
  }

  public AbstractLangTrainerModule createModule() {
    return this.moduleFactory.get();
  }
}
