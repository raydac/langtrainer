package com.igormaznitsa.langtrainer;

import com.igormaznitsa.langtrainer.api.AbstractLangTrainerModule;
import com.igormaznitsa.langtrainer.modules.dialog.DialogModule;
import com.igormaznitsa.langtrainer.modules.test.TestModule;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum Modules {
  DIALOG(DialogModule::new),
  TEST(TestModule::new);

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
