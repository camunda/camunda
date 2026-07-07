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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link FilterPredicate} pre-typed once at construction so the hot per-fact check is a primitive
 * compare or a set probe — never a parse or a {@code String.valueOf} allocation. Per operator:
 *
 * <ul>
 *   <li>{@code EQUALS/NOT_EQUALS} — the declared string value is parsed to its canonical
 *       long/boolean form so the comparison is typed for the common field types, and pre-encoded to
 *       UTF-8 so a byte-carried string field ({@link Utf8View}) is a plain byte compare (ADR 0008)
 *       — falling back to the original string comparison when the field's runtime type is unknown
 *       or the filter value is not the canonical rendering of a typed value. Equality parity with
 *       the reference semantics {@code String.valueOf(value).equals(filter.value())} is exact:
 *       UTF-8 byte equality is string equality, and a typed compare fires only when it agrees with
 *       the string compare (canonical renderings); everything else falls back.
 *   <li>{@code LT/LE/GT/GE} — the declared value is parsed once to a long (and to a double when it
 *       has a fractional form), so the per-fact check is one unboxed primitive compare; integral
 *       field against integral bound compares exactly as longs, any double on either side compares
 *       as doubles. A non-numeric field value never matches (ordering is defined on numbers only),
 *       and an unparseable declared value never matches (validation rejects it at admission — see
 *       {@link FilterPredicate#validateValue()}).
 *   <li>{@code IN} — each list element is pre-encoded to UTF-8 so a {@link Utf8View} field is a
 *       hash-set probe with byte-wise equality ({@link Utf8View}'s own hash/equals), a {@code
 *       String} field a plain string-set probe, and an integral field a binary search over the
 *       elements that are canonical longs (equivalent to string membership of the canonical
 *       rendering, since a non-canonical element can never equal one). Other types fall back to
 *       string membership of their canonical rendering.
 *   <li>{@code IS_NULL/NOT_NULL} — pure field-presence checks (absent and null coincide in a {@link
 *       Fact}).
 * </ul>
 */
public final class CompiledFilter {

  private static final long[] NO_LONG_MEMBERS = new long[0];

  private final FilterPredicate filter;
  private final FilterPredicate.Operator operator;

  // --- EQUALS/NOT_EQUALS ---------------------------------------------------------------------
  // The filter value's UTF-8 bytes, encoded once — the byte compare for Utf8View fields.
  private final byte[] utf8Value;
  // Set iff filter.value() is the canonical rendering of the typed value — i.e. the typed compare
  // is guaranteed to agree with the reference string compare. Null means "fall back to strings".
  private final Long longValue;
  private final Boolean booleanValue;

  // --- LT/LE/GT/GE ----------------------------------------------------------------------------
  // Whether the declared value parsed at all; when false the predicate never matches.
  private final boolean numericBound;
  // Whether the bound is an exact long (no fractional form) — the integral-vs-integral fast path.
  private final boolean integralBound;
  private final long longBound;
  private final double doubleBound;

  // --- IN -------------------------------------------------------------------------------------
  // The elements as byte-wise views (Utf8View probe), as strings (String and fallback probe), and
  // the canonical-long subset, sorted (binary-search probe for integral fields).
  private final Set<Utf8View> utf8Members;
  private final Set<String> stringMembers;
  private final long[] longMembers;

  public CompiledFilter(final FilterPredicate filter) {
    this.filter = filter;
    operator = filter.operator();
    final String value = filter.value();

    utf8Value = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
    longValue = value == null ? null : canonicalLong(value);
    booleanValue = value == null ? null : canonicalBoolean(value);

    boolean numeric = false;
    boolean integral = false;
    long asLong = 0L;
    double asDouble = 0.0d;
    if (filter.ordersNumerically()) {
      try {
        asLong = Long.parseLong(value);
        asDouble = asLong;
        integral = true;
        numeric = true;
      } catch (final NumberFormatException e) {
        try {
          asDouble = Double.parseDouble(value);
          numeric = Double.isFinite(asDouble);
        } catch (final NumberFormatException ignored) {
          // an unparseable bound never matches; validation rejects it at admission time
        }
      }
    }
    numericBound = numeric;
    integralBound = integral;
    longBound = asLong;
    doubleBound = asDouble;

    if (operator == FilterPredicate.Operator.IN) {
      final List<String> elements = filter.inValues();
      final Set<Utf8View> utf8 = new HashSet<>();
      final Set<String> strings = new HashSet<>();
      long[] longs = new long[elements.size()];
      int longCount = 0;
      for (final String element : elements) {
        strings.add(element);
        utf8.add(Utf8View.of(element));
        final Long canonical = canonicalLong(element);
        if (canonical != null) {
          longs[longCount++] = canonical;
        }
      }
      longs = Arrays.copyOf(longs, longCount);
      Arrays.sort(longs);
      utf8Members = utf8;
      stringMembers = strings;
      longMembers = longs;
    } else {
      utf8Members = Set.of();
      stringMembers = Set.of();
      longMembers = NO_LONG_MEMBERS;
    }
  }

  /** Whether the fact satisfies this predicate (see {@link FilterPredicate} for the semantics). */
  public boolean matches(final Fact fact) {
    final Object value = fact.get(filter.field());
    return switch (operator) {
      case EQUALS -> value != null && valueEquals(value);
      case NOT_EQUALS -> value == null || !valueEquals(value);
      case LT, LE, GT, GE -> value != null && ordersAgainstBound(value);
      case IN -> value != null && memberOfList(value);
      case IS_NULL -> value == null;
      case NOT_NULL -> value != null;
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

  private boolean ordersAgainstBound(final Object value) {
    if (!numericBound) {
      return false;
    }
    if (isIntegral(value)) {
      final long fieldValue = ((Number) value).longValue();
      return integralBound ? satisfiesLong(fieldValue) : satisfiesDouble(fieldValue);
    }
    if (value instanceof Double || value instanceof Float) {
      // NaN fails every primitive comparison, so a NaN field value never matches.
      return satisfiesDouble(((Number) value).doubleValue());
    }
    return false;
  }

  private boolean satisfiesLong(final long fieldValue) {
    return switch (operator) {
      case LT -> fieldValue < longBound;
      case LE -> fieldValue <= longBound;
      case GT -> fieldValue > longBound;
      case GE -> fieldValue >= longBound;
      default -> false;
    };
  }

  private boolean satisfiesDouble(final double fieldValue) {
    return switch (operator) {
      case LT -> fieldValue < doubleBound;
      case LE -> fieldValue <= doubleBound;
      case GT -> fieldValue > doubleBound;
      case GE -> fieldValue >= doubleBound;
      default -> false;
    };
  }

  private boolean memberOfList(final Object value) {
    if (value instanceof final Utf8View view) {
      return utf8Members.contains(view);
    }
    if (value instanceof final String string) {
      return stringMembers.contains(string);
    }
    if (isIntegral(value)) {
      return longMembers.length > 0
          && Arrays.binarySearch(longMembers, ((Number) value).longValue()) >= 0;
    }
    if (value instanceof final Boolean bool) {
      return stringMembers.contains(bool.toString());
    }
    return stringMembers.contains(String.valueOf(value));
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
