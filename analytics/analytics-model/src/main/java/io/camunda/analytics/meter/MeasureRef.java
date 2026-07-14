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
 * field reads as {@code null} (which the distinct/top-k sketches already ignore). A boolean field
 * reads as {@code 1}/{@code 0} so a ratio can measure it (e.g. the no-incident share is {@code
 * hadIncident == 0}). The null-as-0 read is <em>load-bearing only for ratios</em>: numeric-measure
 * meter kinds never see it in practice, because {@link MeterCatalog#bind} gives them an implicit
 * {@code NOT_NULL(measure)} filter (SQL semantics — a fact without the measured field does not
 * contribute a phantom 0 observation).
 */
public record MeasureRef(String field) {

  public MeasureRef {
    Objects.requireNonNull(field, "field");
  }

  public long asLong(final FactRow fact) {
    final Object value = fact.get(field);
    if (value instanceof final Number number) {
      return number.longValue();
    }
    return value instanceof final Boolean flag && flag ? 1L : 0L;
  }

  public double asDouble(final FactRow fact) {
    final Object value = fact.get(field);
    if (value instanceof final Number number) {
      return number.doubleValue();
    }
    return value instanceof final Boolean flag && flag ? 1.0 : 0.0;
  }

  /**
   * The measured field as a materialized {@code String} — a mandatory-edge read (top-k sketches
   * genuinely store items). A UTF-8 view decodes lazily (memoized) here.
   */
  public String asString(final FactRow fact) {
    final Object value = fact.get(field);
    return value == null ? null : value.toString();
  }

  /**
   * The measured field's raw value, for meters that consume it natively without materializing —
   * e.g. distinct-count hashes a UTF-8 view's bytes directly (bit-identical to hashing the {@code
   * String}, ADR 0008).
   */
  public Object asValue(final FactRow fact) {
    return fact.get(field);
  }
}
