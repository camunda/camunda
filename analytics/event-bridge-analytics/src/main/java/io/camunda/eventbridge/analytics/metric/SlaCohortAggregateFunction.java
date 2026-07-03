/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.eventbridge.analytics.fact.SlaCohortFact;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.RatioResult;

/**
 * The forward-looking SLA-met metric over a <em>start cohort</em>: {@code total} counts every
 * instance that started in the window (the activation signal) and {@code matched} counts those that
 * completed normally within the target ({@code durationMs <= slaMs}). Breaches = {@code total -
 * matched} therefore include both late completions and instances still running past their deadline
 * — the latter simply never contribute a {@code matched}. Both counts are additive, so the merge is
 * exact and combines across partitions and (on read) across windows.
 *
 * <p>Unlike a completion-windowed ratio, this does not go green while slow instances pile up
 * unfinished: an instance that blows the SLA lowers its start cohort's ratio as soon as the cohort
 * matures (the rollup's allowed lateness is set to the SLA target), whether or not it ever
 * completes.
 */
public final class SlaCohortAggregateFunction
    implements AggregateFunction<SlaCohortFact, SlaCohortAccumulator, RatioResult> {

  private final long slaMs;

  public SlaCohortAggregateFunction(final long slaMs) {
    this.slaMs = slaMs;
  }

  @Override
  public SlaCohortAccumulator createAccumulator() {
    return SlaCohortAccumulator.empty();
  }

  @Override
  public SlaCohortAccumulator add(final SlaCohortFact fact, final SlaCohortAccumulator acc) {
    if (fact.activation()) {
      // an instance started in this cohort — the denominator
      return new SlaCohortAccumulator(acc.started() + 1L, acc.met(), acc.settled());
    }
    // an outcome for an instance in this cohort: it is now settled, and counts as met only if it
    // completed normally within the target.
    final boolean met = fact.completedNormally() && fact.durationMs() <= slaMs;
    return new SlaCohortAccumulator(acc.started(), acc.met() + (met ? 1L : 0L), acc.settled() + 1L);
  }

  @Override
  public SlaCohortAccumulator merge(final SlaCohortAccumulator a, final SlaCohortAccumulator b) {
    return new SlaCohortAccumulator(
        a.started() + b.started(), a.met() + b.met(), a.settled() + b.settled());
  }

  @Override
  public RatioResult getResult(final SlaCohortAccumulator acc) {
    final double ratio = acc.started() == 0L ? 0.0 : (double) acc.met() / acc.started();
    return new RatioResult(acc.met(), acc.started(), ratio);
  }
}
