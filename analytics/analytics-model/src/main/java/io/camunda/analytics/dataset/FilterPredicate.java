/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A WHERE predicate of a {@link DatasetDeclaration}: keep only facts whose {@code field} relates to
 * {@code value} by {@code operator}. The value is carried as a string and coerced to the field's
 * type when applied (once, at filter compilation — never per fact).
 *
 * <p>Semantics per operator. In a fact an absent field and a {@code null} field coincide (the null
 * bucket), so "absent" below covers both:
 *
 * <table>
 *   <caption>operator semantics</caption>
 *   <tr><th>operator</th><th>value</th><th>absent field / null bucket</th><th>type mismatch</th></tr>
 *   <tr><td>{@link Operator#EQUALS}</td><td>any string</td><td>never matches</td>
 *       <td>none — every field type compares by its canonical string rendering
 *           ({@code String.valueOf(value).equals(filter.value())})</td></tr>
 *   <tr><td>{@link Operator#NOT_EQUALS}</td><td>any string</td><td>always matches</td>
 *       <td>none — the exact negation of {@code EQUALS}</td></tr>
 *   <tr><td>{@link Operator#LT}, {@link Operator#LE}, {@link Operator#GT}, {@link Operator#GE}</td>
 *       <td>numeric (validated at compile/provisioning)</td><td>never matches</td>
 *       <td>a non-numeric field value (string, boolean, …) never matches — ordering is defined on
 *           numbers only, even for numeric-looking strings</td></tr>
 *   <tr><td>{@link Operator#IN}</td><td>comma-separated list, elements trimmed, at least one
 *       non-blank element (validated at compile/provisioning; elements cannot contain commas)</td>
 *       <td>never matches</td>
 *       <td>none — membership is by the field value's canonical string rendering (numeric fields
 *           match numeric-canonical elements, typed)</td></tr>
 *   <tr><td>{@link Operator#IS_NULL}</td><td>none (must be absent/blank)</td><td>matches</td>
 *       <td>n/a — a pure presence check</td></tr>
 *   <tr><td>{@link Operator#NOT_NULL}</td><td>none (must be absent/blank)</td><td>never
 *       matches</td><td>n/a — a pure presence check</td></tr>
 * </table>
 */
public record FilterPredicate(String field, Operator operator, String value) {

  public enum Operator {
    EQUALS,
    NOT_EQUALS,
    /** Less than a numeric bound. */
    LT,
    /** Less than or equal to a numeric bound. */
    LE,
    /** Greater than a numeric bound. */
    GT,
    /** Greater than or equal to a numeric bound. */
    GE,
    /** Member of a comma-separated value list. */
    IN,
    /** The field is absent (the null bucket). */
    IS_NULL,
    /** The field is present. */
    NOT_NULL
  }

  public FilterPredicate {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(operator, "operator");
    if (field.isBlank()) {
      throw new IllegalArgumentException("filter field must not be blank");
    }
    if (operator == Operator.IS_NULL || operator == Operator.NOT_NULL) {
      // A presence check carries no comparison value; tolerate a blank one from the wire.
      if (value != null && !value.isBlank()) {
        throw new IllegalArgumentException(
            "filter on '"
                + field
                + "' ("
                + operator
                + ") must not carry a value, was '"
                + value
                + "'");
      }
      value = null;
    } else {
      Objects.requireNonNull(value, "value");
    }
  }

  public static FilterPredicate equals(final String field, final String value) {
    return new FilterPredicate(field, Operator.EQUALS, value);
  }

  public static FilterPredicate notEquals(final String field, final String value) {
    return new FilterPredicate(field, Operator.NOT_EQUALS, value);
  }

  public static FilterPredicate lessThan(final String field, final String value) {
    return new FilterPredicate(field, Operator.LT, value);
  }

  public static FilterPredicate lessOrEqual(final String field, final String value) {
    return new FilterPredicate(field, Operator.LE, value);
  }

  public static FilterPredicate greaterThan(final String field, final String value) {
    return new FilterPredicate(field, Operator.GT, value);
  }

  public static FilterPredicate greaterOrEqual(final String field, final String value) {
    return new FilterPredicate(field, Operator.GE, value);
  }

  /** Membership in {@code values}, a comma-separated list (elements are trimmed). */
  public static FilterPredicate in(final String field, final String values) {
    return new FilterPredicate(field, Operator.IN, values);
  }

  public static FilterPredicate isNull(final String field) {
    return new FilterPredicate(field, Operator.IS_NULL, null);
  }

  public static FilterPredicate notNull(final String field) {
    return new FilterPredicate(field, Operator.NOT_NULL, null);
  }

  /**
   * Whether this predicate orders against a numeric bound ({@code LT/LE/GT/GE}). Deliberately not
   * named like a getter: dataset specs are persisted by plain Jackson introspection, which would
   * otherwise serialize this derived flag as a phantom property and break the round trip.
   */
  public boolean ordersNumerically() {
    return operator == Operator.LT
        || operator == Operator.LE
        || operator == Operator.GT
        || operator == Operator.GE;
  }

  /**
   * The {@code IN} list elements per the shared param-list convention (see {@link
   * io.camunda.analytics.meter.Meter}'s array params): split on commas, trimmed, blanks dropped.
   */
  public List<String> inValues() {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(element -> !element.isEmpty())
        .toList();
  }

  /**
   * The value-shape check for this predicate's operator — an ordering operator needs a numeric
   * value, {@code IN} a non-empty list. Throws a plain {@link IllegalArgumentException} carrying
   * the filter context; {@link DatasetCompiler} wraps it into a {@link DatasetValidationException}
   * with the dataset context (the same admission gate as meter params), so a bad declaration is
   * rejected at compile/provisioning time instead of silently never matching.
   */
  public void validateValue() {
    if (ordersNumerically() && !isNumeric(value)) {
      throw new IllegalArgumentException(
          "filter on '"
              + field
              + "' ("
              + operator
              + "): value must be numeric, was '"
              + value
              + "'");
    }
    if (operator == Operator.IN && inValues().isEmpty()) {
      throw new IllegalArgumentException(
          "filter on '"
              + field
              + "' (IN): value must be a non-empty comma-separated list, was '"
              + value
              + "'");
    }
  }

  /** Whether {@code value} parses as a long or a finite double. */
  private static boolean isNumeric(final String value) {
    try {
      Long.parseLong(value);
      return true;
    } catch (final NumberFormatException e) {
      // fall through to the double form
    }
    try {
      return Double.isFinite(Double.parseDouble(value));
    } catch (final NumberFormatException e) {
      return false;
    }
  }
}
