/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class LikePattern {

  private LikePattern() {}

  /**
   * Turns a {@code $like} value into a pattern: {@code *} matches any run of characters, {@code ?}
   * one character, and a backslash makes the next character literal. Returns empty when the value
   * needs more than {@code maxLength} characters, as it then matches no candidate of at most that
   * length. Characters are code points, as {@code ?} becomes a regex {@code .}, which matches one.
   *
   * <p>The value is caller input, so the pattern must stay cheap to compile and match: adjacent
   * {@code .*} backtrack exponentially in their number, and compiling a long pattern is
   * superlinear. Collapsing runs of {@code *} and giving up past {@code maxLength} characters bound
   * the pattern by {@code maxLength}, whatever the value's size.
   *
   * @throws IllegalArgumentException if {@code maxLength} is negative
   */
  static Optional<Pattern> compile(final String like, final int maxLength) {
    if (maxLength < 0) {
      throw new IllegalArgumentException("maxLength must not be negative, was " + maxLength);
    }
    final var regex = new StringBuilder();
    boolean inRun = false;
    int consumed = 0;
    for (int i = 0; i < like.length(); ) {
      final int c = like.codePointAt(i);
      i += Character.charCount(c);
      if (c == '*') {
        if (!inRun) {
          regex.append(".*");
        }
        inRun = true;
        continue;
      }
      inRun = false;
      if (++consumed > maxLength) {
        return Optional.empty();
      }
      if (c == '\\' && i < like.length()) {
        final int escaped = like.codePointAt(i);
        i += Character.charCount(escaped);
        regex.append(quote(escaped));
      } else if (c == '?') {
        regex.append('.');
      } else {
        regex.append(quote(c));
      }
    }
    return Optional.of(Pattern.compile(regex.toString()));
  }

  // quoting a whole code point keeps a surrogate pair together, so it can match itself
  private static String quote(final int codePoint) {
    return Pattern.quote(Character.toString(codePoint));
  }
}
