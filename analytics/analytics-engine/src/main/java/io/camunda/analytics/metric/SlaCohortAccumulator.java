/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * Accumulator for the forward-looking SLA-met cohort. All three counts are additive so the merge is
 * exact.
 *
 * <ul>
 *   <li>{@code started} — instances that started in the cohort (the denominator);
 *   <li>{@code met} — of those, the ones that completed normally within the target (the numerator);
 *   <li>{@code settled} — instances that have produced an outcome (completed or terminated) so far,
 *       whether or not they met the target.
 * </ul>
 *
 * <p>{@code settled} is what lets a read bound a not-yet-final cohort: {@code started - settled}
 * instances are still open, so the final ratio lies in {@code [met/started, (met + open)/started]}
 * — a still-open instance can only push the numerator up. The band collapses to a point once every
 * instance is settled.
 */
public record SlaCohortAccumulator(long started, long met, long settled) {

  public static SlaCohortAccumulator empty() {
    return new SlaCohortAccumulator(0L, 0L, 0L);
  }
}
