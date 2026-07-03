/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * The declaration of one metric a dataset computes: a meter {@code type} (see {@link MeterCatalog})
 * over an optional measured {@code field}, with type-specific {@code params} (e.g. percentile
 * ranks, top-k {@code k}, histogram thresholds). Pure data — this is what a dataset declaration
 * produces, replacing the hand-written per-metric {@code MetricSpec} wiring.
 *
 * @param name the output name of this metric within its dataset
 * @param type the meter-type id resolved against the {@link MeterCatalog}
 * @param measureField the fact field measured, or {@code null} for measure-less meters (e.g. count)
 * @param params type-specific parameters as strings
 */
public record Meter(String name, String type, String measureField, Map<String, String> params) {

  public Meter {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    params = params == null ? Map.of() : Map.copyOf(params);
  }

  public static Meter of(final String name, final String type) {
    return new Meter(name, type, null, Map.of());
  }

  public static Meter of(final String name, final String type, final String measureField) {
    return new Meter(name, type, measureField, Map.of());
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
    return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
  }

  public double[] doubleArrayParam(final String key, final double[] defaultValue) {
    final String value = params.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .mapToDouble(Double::parseDouble)
        .toArray();
  }

  /** A required comma-separated long array param; throws if absent. */
  public long[] requireLongArrayParam(final String key) {
    final String value = params.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "meter '" + name + "' (" + type + ") requires param '" + key + "'");
    }
    return Arrays.stream(value.split(",")).map(String::trim).mapToLong(Long::parseLong).toArray();
  }
}
