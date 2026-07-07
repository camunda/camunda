/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.fact.Fact;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A {@link FilterPredicate} pre-typed once at construction: the declared string value is parsed to
 * its canonical long/boolean form so the hot per-fact comparison is a typed compare (no {@code
 * String.valueOf} allocation) for the common field types, and pre-encoded to UTF-8 so a
 * byte-carried string field ({@link Utf8View}) is a plain byte compare (ADR 0008) — falling back to
 * the original string comparison when the field's runtime type is unknown or the filter value is
 * not the canonical rendering of a typed value. Equality parity with the reference semantics {@code
 * String.valueOf(value).equals(filter.value())} is exact: UTF-8 byte equality is string equality,
 * and a typed compare fires only when it agrees with the string compare (canonical renderings);
 * everything else falls back.
 */
public final class CompiledFilter {

  private final FilterPredicate filter;

  // The filter value's UTF-8 bytes, encoded once — the byte compare for Utf8View fields.
  private final byte[] utf8Value;
  // Set iff filter.value() is the canonical rendering of the typed value — i.e. the typed compare
  // is guaranteed to agree with the reference string compare. Null means "fall back to strings".
  private final Long longValue;
  private final Boolean booleanValue;

  public CompiledFilter(final FilterPredicate filter) {
    this.filter = filter;
    utf8Value = filter.value().getBytes(StandardCharsets.UTF_8);
    longValue = canonicalLong(filter.value());
    booleanValue = canonicalBoolean(filter.value());
  }

  /** Whether the fact satisfies this predicate (a missing field never {@code EQUALS}). */
  public boolean matches(final Fact fact) {
    final Object value = fact.get(filter.field());
    final boolean equal = value != null && valueEquals(value);
    return switch (filter.operator()) {
      case EQUALS -> equal;
      case NOT_EQUALS -> !equal;
    };
  }

  private boolean valueEquals(final Object value) {
    if (value instanceof final Utf8View view) {
      return Arrays.equals(view.utf8(), utf8Value);
    }
    if (value instanceof final String string) {
      return string.equals(filter.value());
    }
    if (longValue != null && isIntegral(value)) {
      return ((Number) value).longValue() == longValue;
    }
    if (value instanceof final Boolean bool) {
      return bool.equals(booleanValue);
    }
    return String.valueOf(value).equals(filter.value());
  }

  private static boolean isIntegral(final Object value) {
    return value instanceof Long
        || value instanceof Integer
        || value instanceof Short
        || value instanceof Byte;
  }

  /** {@code value} parsed as a long iff it is the canonical {@code Long.toString} rendering. */
  private static Long canonicalLong(final String value) {
    try {
      final long parsed = Long.parseLong(value);
      return Long.toString(parsed).equals(value) ? parsed : null;
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  /** {@code value} as a boolean iff it is exactly {@code "true"} or {@code "false"}. */
  private static Boolean canonicalBoolean(final String value) {
    if ("true".equals(value)) {
      return Boolean.TRUE;
    }
    if ("false".equals(value)) {
      return Boolean.FALSE;
    }
    return null;
  }
}
