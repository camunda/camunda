/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.eventbridge.streaming.aggregate.Aggregation;
import io.camunda.eventbridge.streaming.aggregate.SegmentSealingAggregation;
import java.util.List;

/**
 * One cube meter as a Stage-1 {@link Aggregation}: it gates the shared fact stream down to the
 * facts this cube+meter should fold, then delegates to a {@link SegmentSealingAggregation}. A fact
 * passes the gate when it is the cube's {@link FactType}, its source position is at/after the
 * cube's activation ({@link RegisteredDataset#admits}, the replay-deterministic forward-only rule),
 * and it satisfies every declared filter. All other lifecycle calls (flush/checkpoint/…) pass
 * through, and {@link #safeOffset()} exposes the delegate's commit watermark so the shard commits
 * only past sealed segments.
 */
public final class CubeMeterAggregation implements Aggregation<Fact> {

  private final FactType factType;
  private final RegisteredDataset dataset;
  private final List<FilterPredicate> filters;
  private final SegmentSealingAggregation<Fact, ?, ?> delegate;

  public CubeMeterAggregation(
      final FactType factType,
      final RegisteredDataset dataset,
      final List<FilterPredicate> filters,
      final SegmentSealingAggregation<Fact, ?, ?> delegate) {
    this.factType = factType;
    this.dataset = dataset;
    this.filters = List.copyOf(filters);
    this.delegate = delegate;
  }

  @Override
  public void accept(final Fact fact) {
    if (fact.factType() == factType
        && dataset.admits(fact.sourcePartition(), fact.sourcePosition())
        && matchesFilters(fact)) {
      delegate.accept(fact);
    }
  }

  /** The highest source position it is safe to commit for this meter (its delegate's watermark). */
  public long safeOffset() {
    return delegate.safeOffset();
  }

  private boolean matchesFilters(final Fact fact) {
    for (final FilterPredicate filter : filters) {
      final Object value = fact.get(filter.field());
      final boolean matches =
          filter.operator() == FilterPredicate.Operator.EQUALS
              && value != null
              && String.valueOf(value).equals(filter.value());
      if (!matches) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void flush() {
    delegate.flush();
  }

  @Override
  public void checkpoint() {
    delegate.checkpoint();
  }

  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    delegate.advanceStreamTime(streamTimeMs);
  }

  @Override
  public void punctuateWallClock(final long wallClockMs) {
    delegate.punctuateWallClock(wallClockMs);
  }

  @Override
  public boolean needsCheckpoint() {
    return delegate.needsCheckpoint();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
