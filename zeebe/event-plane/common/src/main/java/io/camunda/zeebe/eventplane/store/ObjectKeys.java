/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

/** What every store requires of the keys and prefixes it is given. */
final class ObjectKeys {

  private ObjectKeys() {}

  /** A key is segments, none of them empty, {@code .} or {@code ..}, separated by slashes. */
  static String requireKey(final String key) {
    if (!hasValidSegments(key)) {
      throw new InvalidObjectNameException(
          "a key of non-empty segments, other than '.' and '..', separated by '/'", key);
    }
    return key;
  }

  /** A prefix is empty or a directory: a key that ends in a slash. */
  static String requireDirectory(final String prefix) {
    if (!prefix.isEmpty()
        && !(prefix.endsWith("/") && hasValidSegments(prefix.substring(0, prefix.length() - 1)))) {
      throw new InvalidObjectNameException(
          "an empty prefix, or one of non-empty segments, other than '.' and '..', that ends in '/'",
          prefix);
    }
    return prefix;
  }

  /**
   * The directory that a prefix starts in: everything up to its last slash. The rest of the prefix
   * is the start of a name in that directory, and may be anything.
   */
  static String directoryOf(final String prefix) {
    return requireDirectory(prefix.substring(0, prefix.lastIndexOf('/') + 1));
  }

  static int requireLimit(final int limit) {
    if (limit < 0) {
      throw new IllegalArgumentException("Expected a limit of 0 or more, but got " + limit);
    }
    return limit;
  }

  private static boolean hasValidSegments(final String key) {
    if (key.isEmpty() || key.indexOf('\0') >= 0) {
      return false;
    }
    int start = 0;
    while (start <= key.length()) {
      int end = key.indexOf('/', start);
      if (end < 0) {
        end = key.length();
      }
      final var segment = key.substring(start, end);
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        return false;
      }
      start = end + 1;
    }
    return true;
  }
}
