package com.igormaznitsa.langtrainer.engine;

/**
 * Dialog shown in a module list: bundled resources vs a file opened by the user.
 */
public record DialogListEntry(DialogDefinition definition, boolean fromExternalFile) {
}
