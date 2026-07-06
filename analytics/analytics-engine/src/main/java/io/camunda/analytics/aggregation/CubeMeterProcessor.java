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
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import io.camunda.eventbridge.streaming.shuffle.SegmentCell;
import java.util.List;

/**
 * One cube meter as a Stage-1 windowed-aggregate {@link Processor} node: it gates the shared fact
 * stream down to the facts this cube+meter should fold — the cube's {@link FactType}, at/after the
 * cube's activation ({@link RegisteredDataset#admits}, the forward-only subscription rule),
 * matching every declared filter — then folds each into a {@link SegmentSealingAggregation} that
 * seals per-source-partition segment deltas. Each sealed delta is encoded by the {@link
 * ForwardingSegmentSink} and <em>forwarded</em> downstream as a {@link SegmentCell} to the shuffle
 * sink node — the aggregate emits its results; a separate node owns the transport.
 *
 * <p>{@code checkpoint()} persists the aggregation's open segment (Model F), so committing the full
 * consumed offset never loses the in-flight partial; the sealed deltas reach the shuffle sink
 * synchronously as they are emitted, and that node publishes them on {@code flush()}.
 */
public final class CubeMeterProcessor implements Processor<Fact, SegmentCell> {

  private final FactType factType;
  private final RegisteredDataset dataset;
  private final List<FilterPredicate> filters;
  private final SegmentSealingAggregation<Fact, ?, ?> aggregation;
  private final ForwardingSegmentSink<?> sink;

  public CubeMeterProcessor(
      final FactType factType,
      final RegisteredDataset dataset,
      final List<FilterPredicate> filters,
      final SegmentSealingAggregation<Fact, ?, ?> aggregation,
      final ForwardingSegmentSink<?> sink) {
    this.factType = factType;
    this.dataset = dataset;
    this.filters = List.copyOf(filters);
    this.aggregation = aggregation;
    this.sink = sink;
  }

  @Override
  public void init(final ProcessorContext<SegmentCell> context) {
    // Wire the aggregation's sink to this node's children — sealed cells forward to the shuffle
    // sink.
    sink.bind(context::forward);
  }

  @Override
  public void process(final Fact fact) {
    if (fact.factType() == factType
        && dataset.admits(fact.sourcePartition(), fact.sourcePosition(), fact.eventTime())
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
      final boolean equal = value != null && String.valueOf(value).equals(filter.value());
      final boolean matches =
          switch (filter.operator()) {
            case EQUALS -> equal;
            case NOT_EQUALS -> !equal;
          };
      if (!matches) {
        return false;
      }
    }
    return true;
  }
}
