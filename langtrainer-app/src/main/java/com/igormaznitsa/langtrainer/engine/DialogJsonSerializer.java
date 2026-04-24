package com.igormaznitsa.langtrainer.engine;

import java.util.List;

public final class DialogJsonSerializer {

  private DialogJsonSerializer() {
  }

  public static String toPrettyJson(final DialogDefinition definition) {
    final StringBuilder sb = new StringBuilder();
    appendDocumentHeader(definition, sb);
    appendLinesArray(definition.lines(), sb);
    appendInputEquIfNonEmpty(definition.inputEqu(), sb);
    sb.append("}\n");
    return sb.toString();
  }

  private static void appendDocumentHeader(final DialogDefinition definition,
                                           final StringBuilder sb) {
    sb.append("{\n");
    sb.append("  \"menuName\": ").append(quote(definition.menuName())).append(",\n");
    sb.append("  \"description\": ").append(quote(definition.description())).append(",\n");
    sb.append("  \"langA\": ").append(quote(definition.langA())).append(",\n");
    sb.append("  \"langB\": ").append(quote(definition.langB())).append(",\n");
  }

  private static void appendLinesArray(final List<DialogLine> lines, final StringBuilder sb) {
    sb.append("  \"lines\": [\n");
    for (int i = 0; i < lines.size(); i++) {
      final DialogLine line = lines.get(i);
      sb.append("    {\n");
      sb.append("      \"A\": ").append(quote(line.a())).append(",\n");
      sb.append("      \"B\": ").append(quote(line.b())).append("\n");
      sb.append("    }");
      if (i < lines.size() - 1) {
        sb.append(',');
      }
      sb.append('\n');
    }
    sb.append("  ]");
  }

  private static void appendInputEquIfNonEmpty(
      final List<InputEquivalenceRow> equ, final StringBuilder sb) {
    if (equ.isEmpty()) {
      sb.append('\n');
      return;
    }
    sb.append(",\n  \"inputEqu\": [\n");
    for (int i = 0; i < equ.size(); i++) {
      final InputEquivalenceRow row = equ.get(i);
      sb.append("    {\n");
      sb.append("      \"key\": ").append(stringArrayJson(row.key())).append(",\n");
      sb.append("      \"value\": ").append(stringArrayJson(row.value())).append("\n");
      sb.append("    }");
      if (i < equ.size() - 1) {
        sb.append(',');
      }
      sb.append('\n');
    }
    sb.append("  ]\n");
  }

  private static String stringArrayJson(final List<String> items) {
    final StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(quote(items.get(i)));
    }
    sb.append(']');
    return sb.toString();
  }

  private static String quote(final String raw) {
    return "\"" + jsonEscape(raw) + "\"";
  }

  private static String jsonEscape(final String s) {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '"' -> sb.append("\\\"");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
