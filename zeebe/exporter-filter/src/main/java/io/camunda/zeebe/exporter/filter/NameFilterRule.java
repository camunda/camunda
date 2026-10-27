/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single name filter rule.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>{@code type} must not be {@code null}
 *   <li>{@code pattern} must not be {@code null} or blank
 * </ul>
 */
public record NameFilterRule(Type type, String pattern) {

  public NameFilterRule {
    Objects.requireNonNull(type, "type must not be null");

    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("pattern must not be null or blank");
    }
  }

  /**
   * Converts a list of raw strings into {@link NameFilterRule}s of the given type. Null and blank
   * entries are silently ignored.
   */
  public static List<NameFilterRule> parseRules(final List<String> rawList, final Type type) {
    if (rawList == null || rawList.isEmpty()) {
      return Collections.emptyList();
    }
    return rawList.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(pattern -> new NameFilterRule(type, pattern))
        .toList();
  }

  public enum Type {
    EXACT,
    STARTS_WITH,
    ENDS_WITH,
  }
}
