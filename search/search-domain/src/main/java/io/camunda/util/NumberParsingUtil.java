/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class NumberParsingUtil {

  private NumberParsingUtil() {
    // utility class
  }

  /**
   * Parses the given collection of strings to a list of longs. Non-numeric strings are ignored.
   *
   * @param values the collection of strings to parse
   * @return a list of parsed longs
   */
  public static List<Long> parseLongs(final Collection<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    return values.stream()
        .filter(Objects::nonNull)
        .map(NumberParsingUtil::tryParseLong)
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<Long> tryParseLong(final String value) {
    try {
      return Optional.of(Long.parseLong(value));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }
}
