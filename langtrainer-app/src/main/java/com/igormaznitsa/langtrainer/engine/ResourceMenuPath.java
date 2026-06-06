package com.igormaznitsa.langtrainer.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses and formats optional {@code path} fields on dialog JSON resources.
 */
public final class ResourceMenuPath {

  /**
   * Splits path segments on {@code /} or {@code \} only.
   */
  private static final Pattern SEGMENT_DELIMITER = Pattern.compile("[/\\\\]+");

  private ResourceMenuPath() {
  }

  public static List<String> parseSegments(final String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return List.of();
    }
    final String[] parts = SEGMENT_DELIMITER.split(rawPath.strip());
    final List<String> segments = new ArrayList<>();
    for (final String part : parts) {
      final String trimmed = part.strip();
      if (!trimmed.isEmpty()) {
        if (".".equals(trimmed) || "..".equals(trimmed)) {
          throw new IllegalStateException("Resource menu path segment is not allowed: " + trimmed);
        }
        segments.add(trimmed);
      }
    }
    return List.copyOf(segments);
  }

  public static String canonicalSegmentKey(final String segment) {
    return segment.strip().toLowerCase(Locale.ROOT);
  }

  /**
   * Menu label: first alphabetic character uppercased, following letters lowercased; non-letters
   * before the first letter are unchanged.
   */
  public static String displaySegment(final String segment) {
    final String trimmed = segment.strip();
    if (trimmed.isEmpty()) {
      return trimmed;
    }
    final int firstLetter = indexOfFirstLetter(trimmed);
    if (firstLetter < 0) {
      return trimmed;
    }
    final StringBuilder label = new StringBuilder(trimmed.length());
    label.append(trimmed, 0, firstLetter);
    label.append(Character.toUpperCase(trimmed.charAt(firstLetter)));
    if (firstLetter + 1 < trimmed.length()) {
      label.append(trimmed.substring(firstLetter + 1).toLowerCase(Locale.ROOT));
    }
    return label.toString();
  }

  private static int indexOfFirstLetter(final String text) {
    for (int i = 0; i < text.length(); i++) {
      if (Character.isLetter(text.charAt(i))) {
        return i;
      }
    }
    return -1;
  }
}
