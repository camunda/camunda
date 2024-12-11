/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import java.util.Map;
import java.util.function.Predicate;

public final class ConfigValidation {

  public static Predicate<String> skipEmpty = String::isEmpty;

  private ConfigValidation() {}

  public static <A> A allMatch(
      final String formatIfEmpty, final String formatIfDifferent, final Map<String, A> props) {
    return allMatch(formatIfEmpty, formatIfDifferent, props, (ignored) -> false);
  }

  public static <A> A allMatch(
      final String formatIfEmpty,
      final String formatIfDifferent,
      final Map<String, A> props,
      final Predicate<A> skip) {
    final var filtered = props.values().stream().filter(a -> !skip.test(a)).toList();
    if (filtered.isEmpty()) {
      throw new IllegalArgumentException(formatIfEmpty);
    }
    final A firstPrefix = filtered.getFirst();
    if (filtered.stream().allMatch(p -> p.equals(firstPrefix))) {
      return firstPrefix;
    } else {
      throw new IllegalArgumentException(String.format(formatIfDifferent, props));
    }
  }
}
