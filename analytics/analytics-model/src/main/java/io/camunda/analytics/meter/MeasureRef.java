/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.analytics.dimension.FactRow;
import java.util.Objects;

/**
 * A reference to the fact field a meter measures, read through the {@link FactRow} seam by name.
 * The generic replacement for the per-metric value extractors (e.g. a {@code ToLongFunction<F>}
 * duration lambda) — the measured field is data, not code.
 *
 * <p>Null policy: a missing/{@code null} numeric field reads as {@code 0} and a missing string
 * field reads as {@code null} (which the distinct/top-k sketches already ignore). Robust
 * absent-value filtering is a later concern; a meter's measure is expected present on the facts it
 * consumes.
 */
public record MeasureRef(String field) {

  public MeasureRef {
    Objects.requireNonNull(field, "field");
  }

  public long asLong(final FactRow fact) {
    return fact.get(field) instanceof final Number number ? number.longValue() : 0L;
  }

  public double asDouble(final FactRow fact) {
    return fact.get(field) instanceof final Number number ? number.doubleValue() : 0.0;
  }

  public String asString(final FactRow fact) {
    final Object value = fact.get(field);
    return value == null ? null : value.toString();
  }
}
