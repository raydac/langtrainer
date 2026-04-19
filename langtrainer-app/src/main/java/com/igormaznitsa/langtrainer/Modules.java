package com.igormaznitsa.langtrainer;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.modules.dialog.DialogModule;
import com.igormaznitsa.langtrainer.modules.editor.EditorModule;
import com.igormaznitsa.langtrainer.modules.flygame.FlyGameModule;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum Modules {
  DIALOG(DialogModule::new),
  FLY_GAME(FlyGameModule::new),
  EDITOR(EditorModule::new);

  private final Supplier<AbstractLangTrainerModule> moduleFactory;

  Modules(final Supplier<AbstractLangTrainerModule> moduleFactory) {
    this.moduleFactory = moduleFactory;
  }

  public static List<AbstractLangTrainerModule> createAll() {
    List<AbstractLangTrainerModule> result;
    result = Arrays.stream(values())
        .map(Modules::createModule)
        .collect(Collectors.toList());
    return result;
  }

  public AbstractLangTrainerModule createModule() {
    AbstractLangTrainerModule result;
    result = this.moduleFactory.get();
    return result;
  }
}
