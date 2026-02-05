/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

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

  public enum Type {
    EXACT,
    STARTS_WITH,
    ENDS_WITH,
  }
}
