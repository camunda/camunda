/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.analytics.streaming.aggregate.AggregateFunction;
import io.camunda.analytics.streaming.aggregate.RatioAccumulator;
import io.camunda.analytics.streaming.aggregate.RatioResult;
import io.camunda.eventbridge.analytics.fact.IncidentCohortFact;

/**
 * The forward-looking, per-instance no-incident metric over a start cohort. Reuses {@link
 * RatioAccumulator} where {@code total} = instances started and {@code matched} = instances without
 * an incident. An activation adds {@code (+1 matched, +1 total)} — a newly started instance is
 * assumed clean; the instance's first incident adds {@code (-1 matched, 0 total)} — reclassifying
 * it as having an incident without changing the denominator. Both counts stay additive, so the
 * merge is exact and the ratio {@code matched/total} is the no-incident share.
 *
 * <p>The incident signal fires when the incident is created (not at completion) and only on the
 * first incident per instance, so the metric is forward-looking (running instances count) and
 * distinct (multiple incidents on one instance collapse to one).
 */
public final class NoIncidentCohortAggregateFunction
    implements AggregateFunction<IncidentCohortFact, RatioAccumulator, RatioResult> {

  @Override
  public RatioAccumulator createAccumulator() {
    return RatioAccumulator.empty();
  }

  @Override
  public RatioAccumulator add(final IncidentCohortFact fact, final RatioAccumulator acc) {
    if (fact.activation()) {
      return new RatioAccumulator(acc.matched() + 1L, acc.total() + 1L); // started, clean so far
    }
    return new RatioAccumulator(acc.matched() - 1L, acc.total()); // first incident: no longer clean
  }

  @Override
  public RatioAccumulator merge(final RatioAccumulator a, final RatioAccumulator b) {
    return new RatioAccumulator(a.matched() + b.matched(), a.total() + b.total());
  }

  @Override
  public RatioResult getResult(final RatioAccumulator acc) {
    final double ratio = acc.total() == 0L ? 0.0 : (double) acc.matched() / acc.total();
    return new RatioResult(acc.matched(), acc.total(), ratio);
  }
}
