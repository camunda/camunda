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
 * One cube as a Stage-1 windowed-aggregate {@link Processor} node (ADR 0009): it gates the shared
 * fact stream down to the facts this cube should fold — the cube's {@link FactType}, at/after the
 * cube's activation ({@link RegisteredDataset#admits}, the forward-only subscription rule),
 * matching every declared filter — then folds each into the cube's <em>composite</em> {@link
 * SegmentSealingAggregation} (one accumulator slot per meter), which seals per-source-partition
 * segment deltas. The gate and the key extraction therefore run once per fact per dataset, not once
 * per meter. Each sealed delta is encoded by the {@link ForwardingSegmentSink} and
 * <em>forwarded</em> downstream as a {@link SegmentCell} to the shuffle sink node — the aggregate
 * emits its results; a separate node owns the transport.
 *
 * <p>{@code checkpoint()} persists the aggregation's open segment (Model F), so committing the full
 * consumed offset never loses the in-flight partial; the sealed deltas reach the shuffle sink
 * synchronously as they are emitted, and that node publishes them on {@code flush()}.
 */
public final class CubeAggregationProcessor implements Processor<Fact, SegmentCell> {

  private final FactType factType;
  private final RegisteredDataset dataset;
  private final List<CompiledFilter> filters;
  private final SegmentSealingAggregation<Fact, ?, ?> aggregation;
  private final ForwardingSegmentSink<?> sink;

  public CubeAggregationProcessor(
      final FactType factType,
      final RegisteredDataset dataset,
      final List<FilterPredicate> filters,
      final SegmentSealingAggregation<Fact, ?, ?> aggregation,
      final ForwardingSegmentSink<?> sink) {
    this.factType = factType;
    this.dataset = dataset;
    this.filters = filters.stream().map(CompiledFilter::new).toList();
    this.aggregation = aggregation;
    this.sink = sink;
  }

  /** The bound fact type this cube folds — the stage routes only matching facts here. */
  public FactType factType() {
    return factType;
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
    for (final CompiledFilter filter : filters) {
      if (!filter.matches(fact)) {
        return false;
      }
    }
    return true;
  }
}
