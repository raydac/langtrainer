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
   * Splits path segments on {@code /}, {@code \}, or whitespace only.
   */
  private static final Pattern SEGMENT_DELIMITER = Pattern.compile("[/\\\\\\s]+");

  private ResourceMenuPath() {
  }

  public static List<String> parseSegments(final String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return List.of();
    }
    final String[] parts = SEGMENT_DELIMITER.split(rawPath.strip());
    final List<String> segments = new ArrayList<>();
    for (final String part : parts) {
      if (!part.isEmpty()) {
        segments.add(part);
      }
    }
    return List.copyOf(segments);
  }

  public static String canonicalSegmentKey(final String segment) {
    return segment.toLowerCase(Locale.ROOT);
  }

  /**
   * First character uppercased, remainder lowercased (locale {@link Locale#ROOT}).
   */
  public static String displaySegment(final String segment) {
    if (segment.isEmpty()) {
      return segment;
    }
    if (segment.length() == 1) {
      return segment.toUpperCase(Locale.ROOT);
    }
    return segment.substring(0, 1).toUpperCase(Locale.ROOT)
        + segment.substring(1).toLowerCase(Locale.ROOT);
  }
}
