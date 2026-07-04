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
import io.camunda.eventbridge.streaming.aggregate.SegmentSealingAggregation;
import io.camunda.eventbridge.streaming.processor.Processor;
import java.util.List;

/**
 * One cube meter as a Stage-1 windowed-aggregate {@link Processor} node: it gates the shared fact
 * stream down to the facts this cube+meter should fold — the cube's {@link FactType}, at/after the
 * cube's activation ({@link RegisteredDataset#admits}, the forward-only subscription rule),
 * matching every declared filter — then folds each into a {@link SegmentSealingAggregation} that
 * seals per-source-partition segment deltas into the shuffle. A terminal node ({@code Out = Void}):
 * the sealed deltas leave through the aggregation's sink, not the topology.
 *
 * <p>{@code checkpoint()} persists the aggregation's open segment (Model F), so committing the full
 * consumed offset never loses the in-flight partial; {@code flush()} publishes sealed deltas for
 * produce-before-commit.
 */
public final class CubeMeterProcessor implements Processor<Fact, Void> {

  private final FactType factType;
  private final RegisteredDataset dataset;
  private final List<FilterPredicate> filters;
  private final SegmentSealingAggregation<Fact, ?, ?> aggregation;

  public CubeMeterProcessor(
      final FactType factType,
      final RegisteredDataset dataset,
      final List<FilterPredicate> filters,
      final SegmentSealingAggregation<Fact, ?, ?> aggregation) {
    this.factType = factType;
    this.dataset = dataset;
    this.filters = List.copyOf(filters);
    this.aggregation = aggregation;
  }

  @Override
  public void process(final Fact fact) {
    if (fact.factType() == factType
        && dataset.admits(fact.sourcePartition(), fact.sourcePosition())
        && matchesFilters(fact)) {
      aggregation.accept(fact);
    }
  }

  @Override
  public void flush() {
    aggregation.flush();
  }

  @Override
  public void checkpoint() {
    aggregation.checkpoint();
  }

  @Override
  public void close() {
    aggregation.close();
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
}
