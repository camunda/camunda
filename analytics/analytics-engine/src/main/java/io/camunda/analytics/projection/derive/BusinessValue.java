/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.state.VariableNames;
import io.camunda.analytics.state.immutable.ProjectionState;
import java.util.List;

/**
 * The business-value enrichment of process-instance facts: the numeric value of one designated
 * process variable ({@code analytics.valueVariable}, default {@code amount}), read from the
 * instance's root scope when a PROCESS-level fact is derived. The derivers stamp it as two eager
 * numeric fields — the variable itself rides facts only as a lazily-resolved UTF-8 <em>text</em>
 * view, which no numeric meter (SUM/LEVEL) can measure:
 *
 * <ul>
 *   <li>{@code value} — the positive amount, on activation and end facts alike; a
 *       COMPLETED-filtered SUM over it is "value processed".
 *   <li>{@code valueDelta} — signed like the {@code delta} lifecycle field ({@code +value} on
 *       ACTIVATED, {@code −value} on COMPLETED/TERMINATED); a LEVEL meter summing it is "value
 *       currently in flight".
 * </ul>
 *
 * <p>Both fields are stamped only when the variable is present and numeric — absent otherwise, so
 * the meters' implicit {@code NOT_NULL(measure)} filters skip value-less processes instead of
 * folding phantom zeroes. Start-payload variables land at the root scope <em>before</em> the
 * process element activates (the creation command persists them first), so the activation-side read
 * is safe.
 *
 * <p>The variable is read <em>once</em>, at activation, and materialized on the process row ({@code
 * ElementEntity.value}); both the activation fact and the end fact stamp that materialized value.
 * The end side deliberately never re-reads the variable — a value created or changed mid-flight
 * would otherwise subtract something different from what activation added and leave a permanent
 * residue on the in-flight level. Consequence: a value that only appears mid-flight contributes
 * nothing to either KPI.
 */
public final class BusinessValue {

  private static final String VALUE_VARIABLE =
      System.getProperty("analytics.valueVariable", "amount");

  /** The single-name point-lookup set, encoded once (ADR 0008). */
  private static final VariableNames NAME = VariableNames.of(List.of(VALUE_VARIABLE));

  private BusinessValue() {}

  /**
   * The designated value variable of the given scope as a long, or {@code null} when absent,
   * non-numeric or non-finite (the variable store keeps raw text; a fractional value rounds).
   */
  public static Long read(final ProjectionState state, final long scopeKey) {
    final Utf8View raw = state.variables(scopeKey, NAME).get(VALUE_VARIABLE);
    if (raw == null) {
      return null;
    }
    final String text = raw.toString().trim();
    if (text.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(text);
    } catch (final NumberFormatException notALong) {
      try {
        final double parsed = Double.parseDouble(text);
        // Double.parseDouble accepts "Infinity"/"NaN"/"1e309"; Math.round would turn those into
        // Long.MAX_VALUE or a phantom 0 — one poisoned instance would wreck both value KPIs.
        return Double.isFinite(parsed) ? Math.round(parsed) : null;
      } catch (final NumberFormatException notANumber) {
        return null;
      }
    }
  }
}
