package com.igormaznitsa.langtrainer.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LangResourceJson {

  private static final Pattern STRING_FIELD_PATTERN =
      Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern LINE_PATTERN = Pattern.compile(
      "\\{\\s*\"A\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"B\"\\s*:\\s*\"([^\"]*)\"\\s*\\}");

  private LangResourceJson() {
  }

  public static DialogDefinition parse(final String jsonText) {
    final String menuName = extractStringField(jsonText, "menuName");
    final String description = extractStringField(jsonText, "description");
    final String langA = extractStringField(jsonText, "langA");
    final String langB = extractStringField(jsonText, "langB");
    final List<DialogLine> lines = extractLines(jsonText);
    final List<InputEquivalenceRow> inputEqu = extractInputEqu(jsonText);
    return new DialogDefinition(menuName, description, langA, langB, lines, inputEqu);
  }

  public static DialogDefinition parseFromPath(final Path path) {
    try {
      return parse(Files.readString(path, StandardCharsets.UTF_8));
    } catch (final IOException ex) {
      throw new IllegalStateException("Can't load JSON file: " + path, ex);
    }
  }

  private static List<InputEquivalenceRow> extractInputEqu(final String text) {
    final int keyIdx = text.indexOf("\"inputEqu\"");
    if (keyIdx < 0) {
      return List.of();
    }
    final int openBracket = text.indexOf('[', keyIdx);
    if (openBracket < 0) {
      return List.of();
    }
    final String arrayInner = contentInsideBalancedBracket(text, openBracket, '[', ']');
    if (arrayInner == null) {
      throw new IllegalStateException("Malformed inputEqu array");
    }
    return parseInputEquObjects(arrayInner);
  }

  private static List<InputEquivalenceRow> parseInputEquObjects(final String arrayInner) {
    final List<InputEquivalenceRow> rows = new ArrayList<>();
    int i = 0;
    final int n = arrayInner.length();
    while (i < n) {
      while (i < n &&
          (Character.isWhitespace(arrayInner.charAt(i)) || arrayInner.charAt(i) == ',')) {
        i++;
      }
      if (i >= n) {
        break;
      }
      if (arrayInner.charAt(i) != '{') {
        throw new IllegalStateException("Malformed inputEqu: expected '{' at offset " + i);
      }
      final int close = indexOfMatchingBrace(arrayInner, i);
      if (close < 0) {
        throw new IllegalStateException("Malformed inputEqu: unclosed '{'");
      }
      final String objBody = arrayInner.substring(i + 1, close);
      rows.add(parseInputEquObject(objBody));
      i = close + 1;
    }
    return List.copyOf(rows);
  }

  private static InputEquivalenceRow parseInputEquObject(final String objBody) {
    final List<String> keys = extractNamedStringArray(objBody, "key");
    final List<String> values = extractNamedStringArray(objBody, "value");
    return new InputEquivalenceRow(keys, values);
  }

  private static List<String> extractNamedStringArray(final String obj, final String fieldName) {
    final String needle = "\"" + fieldName + "\"";
    int idx = obj.indexOf(needle);
    if (idx < 0) {
      return List.of();
    }
    idx = obj.indexOf('[', idx);
    if (idx < 0) {
      return List.of();
    }
    final String inner = contentInsideBalancedBracket(obj, idx, '[', ']');
    if (inner == null) {
      return List.of();
    }
    return parseJsonStringLiteralsInArray(inner);
  }

  private static List<String> parseJsonStringLiteralsInArray(final String inner) {
    final List<String> result = new ArrayList<>();
    int i = 0;
    final int n = inner.length();
    while (i < n) {
      while (i < n && (Character.isWhitespace(inner.charAt(i)) || inner.charAt(i) == ',')) {
        i++;
      }
      if (i >= n) {
        break;
      }
      if (inner.charAt(i) != '"') {
        throw new IllegalStateException("Expected string literal in inputEqu array");
      }
      final StringBuilder sb = new StringBuilder();
      final int after = readJsonStringChars(inner, i + 1, sb);
      result.add(sb.toString());
      i = after;
    }
    return result;
  }

  private static int readJsonStringChars(final String s, int start, final StringBuilder out) {
    int i = start;
    while (i < s.length()) {
      final char c = s.charAt(i);
      if (c == '\\') {
        i++;
        if (i >= s.length()) {
          break;
        }
        final char esc = s.charAt(i);
        switch (esc) {
          case '"', '\\', '/' -> out.append(esc);
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'u' -> {
            if (i + 5 <= s.length()) {
              final String hex = s.substring(i + 1, i + 5);
              out.append((char) Integer.parseInt(hex, 16));
              i += 4;
            }
          }
          default -> out.append(esc);
        }
        i++;
      } else if (c == '"') {
        return i + 1;
      } else {
        out.append(c);
        i++;
      }
    }
    return i;
  }

  private static int indexOfMatchingBrace(final String s, final int openBraceIdx) {
    int depth = 0;
    for (int i = openBraceIdx; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '"') {
        i = skipJsonString(s, i);
        i--;
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static int skipJsonString(final String s, final int openQuoteIdx) {
    int i = openQuoteIdx + 1;
    while (i < s.length()) {
      final char c = s.charAt(i);
      if (c == '\\') {
        i += 2;
        continue;
      }
      if (c == '"') {
        return i + 1;
      }
      i++;
    }
    return s.length();
  }

  private static String contentInsideBalancedBracket(
      final String s,
      final int openIdx,
      final char open,
      final char close) {
    if (openIdx >= s.length() || s.charAt(openIdx) != open) {
      return null;
    }
    int depth = 0;
    for (int i = openIdx; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '"') {
        i = skipJsonString(s, i);
        i--;
        continue;
      }
      if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) {
          return s.substring(openIdx + 1, i);
        }
      }
    }
    return null;
  }

  private static String extractStringField(final String text, final String fieldName) {
    final Pattern pattern = Pattern.compile(STRING_FIELD_PATTERN.pattern().formatted(fieldName));
    final Matcher matcher = pattern.matcher(text);
    if (!matcher.find()) {
      throw new IllegalStateException("Field not found: " + fieldName);
    }
    return matcher.group(1);
  }

  private static List<DialogLine> extractLines(final String text) {
    final List<DialogLine> result = new ArrayList<>();
    final Matcher matcher = LINE_PATTERN.matcher(text);
    while (matcher.find()) {
      result.add(new DialogLine(matcher.group(1), matcher.group(2)));
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("Dialog lines are empty");
    }
    return result;
  }
}
