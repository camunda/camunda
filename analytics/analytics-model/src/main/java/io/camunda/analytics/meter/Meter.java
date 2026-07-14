/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.analytics.dataset.FilterPredicate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The declaration of one metric a dataset computes: a meter {@code type} (see {@link MeterCatalog})
 * over an optional measured {@code field}, with type-specific {@code params} (e.g. percentile
 * ranks, top-k {@code k}, histogram thresholds) and optional per-meter {@code filters}. Pure data —
 * this is what a dataset declaration produces, replacing the hand-written per-metric {@code
 * MetricSpec} wiring.
 *
 * <p>{@code filters} are the SQL {@code FILTER (WHERE …)}-clause analogue: the meter's slot folds
 * only the facts that satisfy <em>all</em> of them (conjunction), while the dataset-level filters
 * still gate which facts reach the cube at all. Filtering happens at fold time (Stage 1), so
 * shuffled deltas and serving rows keep their shape — a filtered meter is the same column, fed by
 * fewer facts. A meter measuring field X can filter {@code NOT_NULL(X)} to skip facts missing the
 * measure instead of folding {@link MeasureRef}'s null-as-0 (the canonical fix for that skew).
 *
 * @param name the output name of this metric within its dataset
 * @param type the meter-type id resolved against the {@link MeterCatalog}
 * @param measureField the fact field measured, or {@code null} for measure-less meters (e.g. count)
 * @param params type-specific parameters as strings
 * @param filters per-meter fold predicates, all of which a fact must satisfy (empty = fold all)
 */
public record Meter(
    String name,
    String type,
    String measureField,
    Map<String, String> params,
    List<FilterPredicate> filters) {

  public Meter {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    params = params == null ? Map.of() : Map.copyOf(params);
    filters = filters == null ? List.of() : List.copyOf(filters);
  }

  /** Unfiltered form, for the existing declaration surface (and older persisted specs). */
  public Meter(
      final String name,
      final String type,
      final String measureField,
      final Map<String, String> params) {
    this(name, type, measureField, params, List.of());
  }

  public static Meter of(final String name, final String type) {
    return new Meter(name, type, null, Map.of());
  }

  public static Meter of(final String name, final String type, final String measureField) {
    return new Meter(name, type, measureField, Map.of());
  }

  /** A copy of this meter whose slot folds only facts satisfying all {@code filters}. */
  public Meter filtered(final FilterPredicate... filters) {
    return new Meter(name, type, measureField, params, List.of(filters));
  }

  /** The measure reference, or throws if this meter declared no measured field. */
  public MeasureRef requireMeasure() {
    if (measureField == null) {
      throw new IllegalArgumentException(
          "meter '" + name + "' (" + type + ") requires a measure field");
    }
    return new MeasureRef(measureField);
  }

  public int intParam(final String key, final int defaultValue) {
    final String value = params.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (final NumberFormatException e) {
      throw invalidParam(key, value, "an integer", e);
    }
  }

  public double doubleParam(final String key, final double defaultValue) {
    final String value = params.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (final NumberFormatException e) {
      throw invalidParam(key, value, "a number", e);
    }
  }

  /** A required string param; throws if absent or blank. */
  public String requireParam(final String key) {
    final String value = params.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "meter '" + name + "' (" + type + ") requires param '" + key + "'");
    }
    return value.trim();
  }

  public double[] doubleArrayParam(final String key, final double[] defaultValue) {
    final String value = params.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Arrays.stream(value.split(","))
          .map(String::trim)
          .mapToDouble(Double::parseDouble)
          .toArray();
    } catch (final NumberFormatException e) {
      throw invalidParam(key, value, "a comma-separated list of numbers", e);
    }
  }

  /** A required comma-separated long array param; throws if absent. */
  public long[] requireLongArrayParam(final String key) {
    final String value = params.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "meter '" + name + "' (" + type + ") requires param '" + key + "'");
    }
    try {
      return Arrays.stream(value.split(",")).map(String::trim).mapToLong(Long::parseLong).toArray();
    } catch (final NumberFormatException e) {
      throw invalidParam(key, value, "a comma-separated list of integers", e);
    }
  }

  private IllegalArgumentException invalidParam(
      final String key, final String value, final String expected, final Throwable cause) {
    return new IllegalArgumentException(
        "meter '"
            + name
            + "' ("
            + type
            + ") param '"
            + key
            + "' must be "
            + expected
            + ", was '"
            + value
            + "'",
        cause);
  }
}
