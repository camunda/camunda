/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.analytics.dataset.CompiledFilter;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.projection.ProjectionMetrics;
import io.camunda.eventbridge.streaming.aggregate.SegmentSealingAggregation;
import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import io.camunda.eventbridge.streaming.shuffle.SegmentCell;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>Durability is the owning task's concern: it freezes and persists the aggregation's open
 * segment (Model F) inside its commit cut, so committing the full consumed offset never loses the
 * in-flight partial; the sealed deltas reach the shuffle sink synchronously as they are emitted,
 * and that node publishes them on {@code flush()}.
 */
public final class CubeAggregationProcessor
    implements Processor<Fact, SegmentCell>, ProjectionMetrics.CubeGateStats {

  /**
   * Admitted facts a cube must have seen with <em>zero</em> folds before the silent-empty-cube
   * alarm trips: a real dataset whose filters legitimately match nothing rarely sees this many
   * type-matched, activation-admitted facts, while a misdeclared filter (the incident: a
   * subprocess-scoped variable filter that can never be visible on the fact) crosses it quickly.
   */
  public static final long SILENT_FACT_THRESHOLD = 1_000L;

  private static final Logger LOG = LoggerFactory.getLogger(CubeAggregationProcessor.class);

  private final FactType factType;
  private final RegisteredDataset dataset;
  private final List<FilterPredicate> declaredFilters;
  private final List<CompiledFilter> filters;
  private final SegmentSealingAggregation<Fact, ?, ?> aggregation;
  private final ForwardingSegmentSink<?> sink;
  private final ProjectionMetrics metrics;

  // Plain longs on purpose: single-writer (the partition's actor thread), checked at commit
  // boundaries, exposed through racy-read gauges where staleness is harmless. No allocation and
  // no volatile store on the per-fact hot path.
  private long factsInspected;
  private long factsFolded;
  private boolean silenceWarned;

  public CubeAggregationProcessor(
      final FactType factType,
      final RegisteredDataset dataset,
      final List<FilterPredicate> filters,
      final SegmentSealingAggregation<Fact, ?, ?> aggregation,
      final ForwardingSegmentSink<?> sink) {
    this(factType, dataset, filters, aggregation, sink, ProjectionMetrics.NOOP);
  }

  public CubeAggregationProcessor(
      final FactType factType,
      final RegisteredDataset dataset,
      final List<FilterPredicate> filters,
      final SegmentSealingAggregation<Fact, ?, ?> aggregation,
      final ForwardingSegmentSink<?> sink,
      final ProjectionMetrics metrics) {
    this.factType = factType;
    this.dataset = dataset;
    declaredFilters = List.copyOf(filters);
    this.filters = filters.stream().map(CompiledFilter::new).toList();
    this.aggregation = aggregation;
    this.sink = sink;
    this.metrics = metrics;
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
    if (fact.factType() != factType
        || !dataset.admits(fact.sourcePartition(), fact.sourcePosition(), fact.eventTime())) {
      return;
    }
    // Inspected = type-matched and activation-admitted, so the silent alarm measures exactly what
    // the declared filters rejected — not the activation gap of a freshly-provisioned cube.
    factsInspected++;
    if (matchesFilters(fact)) {
      factsFolded++;
      aggregation.accept(fact);
    }
  }

  @Override
  public String datasetName() {
    return dataset.declaration().name();
  }

  @Override
  public long factsInspected() {
    return factsInspected;
  }

  @Override
  public long factsFolded() {
    return factsFolded;
  }

  @Override
  public boolean silent() {
    return factsFolded == 0 && factsInspected >= SILENT_FACT_THRESHOLD;
  }

  /**
   * Commit-boundary check (never per fact): WARN once when this cube has inspected a meaningful
   * number of admitted facts and folded <em>none</em> — a dataset whose filters match nothing stays
   * empty with zero signal otherwise (a real past incident: a filter on a subprocess-scoped
   * variable that is never visible at the fact's root scope silently matched nothing). The matching
   * gauge ({@code analytics.projection.cube.silent}) stays up until a fold happens.
   */
  public void warnIfSilent() {
    if (silenceWarned || !silent()) {
      return;
    }
    silenceWarned = true;
    metrics.datasetEmptyAlarm(datasetName());
    LOG.warn(
        "Cube '{}' (id {}) inspected {} admitted {} facts and folded NONE — its declared filters"
            + " match nothing (e.g. a filter on a variable that is not visible at this fact's"
            + " scope). The cube stays silently empty until its declaration is fixed. Filters: {}",
        datasetName(),
        dataset.cubeId(),
        factsInspected,
        factType,
        declaredFilters);
  }

  @Override
  public void flush() {
    aggregation.flush();
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
