/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.element.ElementExecutionFact;
import io.camunda.analytics.element.ElementKey;
import io.camunda.analytics.element.ElementKeyValue;
import io.camunda.analytics.element.JdbcElementDurationPercentileSink;
import io.camunda.analytics.element.JdbcElementHeatmapSink;
import io.camunda.analytics.fact.IncidentCohortFact;
import io.camunda.analytics.fact.IncidentDurationFact;
import io.camunda.analytics.fact.IncidentFact;
import io.camunda.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.analytics.fact.ProcessInstanceLifecycleFact;
import io.camunda.analytics.fact.SlaCohortFact;
import io.camunda.analytics.metric.DefinitionKey;
import io.camunda.analytics.metric.DefinitionKeyValue;
import io.camunda.analytics.metric.DurationBucketAccumulator;
import io.camunda.analytics.metric.DurationBucketAccumulatorValue;
import io.camunda.analytics.metric.DurationBucketAggregateFunction;
import io.camunda.analytics.metric.ExecutionTimeAccumulator;
import io.camunda.analytics.metric.ExecutionTimeAccumulatorValue;
import io.camunda.analytics.metric.ExecutionTimeAggregateFunction;
import io.camunda.analytics.metric.IncidentKey;
import io.camunda.analytics.metric.IncidentKeyValue;
import io.camunda.analytics.metric.JdbcActivatedInstancesSink;
import io.camunda.analytics.metric.JdbcActiveInstancesSink;
import io.camunda.analytics.metric.JdbcDefinitionDurationPercentileSink;
import io.camunda.analytics.metric.JdbcDefinitionRatioSink;
import io.camunda.analytics.metric.JdbcDurationBucketSink;
import io.camunda.analytics.metric.JdbcIncidentDurationSink;
import io.camunda.analytics.metric.JdbcIncidentFrequencySink;
import io.camunda.analytics.metric.JdbcOpenIncidentsSink;
import io.camunda.analytics.metric.JdbcRegionExecutionTimeSink;
import io.camunda.analytics.metric.JdbcSlaCohortSink;
import io.camunda.analytics.metric.JdbcTenantDistinctProcessSink;
import io.camunda.analytics.metric.JdbcTenantTopProcessesSink;
import io.camunda.analytics.metric.NoIncidentCohortAggregateFunction;
import io.camunda.analytics.metric.RegionKey;
import io.camunda.analytics.metric.RegionKeyValue;
import io.camunda.analytics.metric.SlaCohortAccumulator;
import io.camunda.analytics.metric.SlaCohortAccumulatorValue;
import io.camunda.analytics.metric.SlaCohortAggregateFunction;
import io.camunda.analytics.sketch.DistinctCountAggregateFunction;
import io.camunda.analytics.sketch.HllSketchValue;
import io.camunda.analytics.sketch.ItemsSketchValue;
import io.camunda.analytics.sketch.KllDoublesSketchValue;
import io.camunda.analytics.sketch.QuantileAggregateFunction;
import io.camunda.analytics.sketch.TopKAggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.LongRecordValue;
import io.camunda.eventbridge.streaming.aggregate.RatioAccumulator;
import io.camunda.eventbridge.streaming.aggregate.RatioAccumulatorRecordValue;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.aggregate.StringRecordValue;
import io.camunda.eventbridge.streaming.aggregate.SumAggregateFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.apache.datasketches.frequencies.ItemsSketch;
import org.apache.datasketches.hll.HllSketch;
import org.apache.datasketches.kll.KllDoublesSketch;

/**
 * The single source of truth for the windowed metrics, defined once as {@link MetricSpec}s so Stage
 * 1 (combiner) and Stage 2 (reducer) share the same {@code aggId}, window, accumulator, and codecs.
 * These mirror Optimize's default dashboards: exec-time by region, the element heatmap, duration
 * percentiles (definition + element, tiered), SLA-met and no-incident cohorts, distinct/top-k
 * processes by tenant, active/activated instances, and incident frequency/open/duration.
 */
public final class Metrics {

  public static final long MINUTE_WINDOW_MS = 60_000L;
  public static final long HOUR_WINDOW_MS = 3_600_000L;
  public static final long DAY_WINDOW_MS = 86_400_000L;
  public static final long TOTAL_WINDOW_MS = 10_000L * 365 * 24 * 60 * 60 * 1000;
  public static final long ALLOWED_LATENESS_MS = 30_000L;
  public static final long COHORT_LATENESS_MS = 600_000L;
  private static final long REGION_DATASET_ID = 1L;
  private static final String NO_REGION = "<none>";
  private static final int TOP_K = 10;
  private static final long[] DURATION_BUCKETS_MS = {10_000L, 30_000L, 60_000L, 120_000L};

  // Stable per-metric aggregation ids (never reuse/reorder for existing on-disk data).
  private static final int AGG_REGION = 1;
  private static final int AGG_HEATMAP = 2;
  private static final int AGG_DEF_PCTL_1M = 3;
  private static final int AGG_DEF_PCTL_1H = 4;
  private static final int AGG_DEF_PCTL_1D = 5;
  private static final int AGG_DEF_PCTL_TOTAL = 6;
  private static final int AGG_ELEM_PCTL_1M = 7;
  private static final int AGG_ELEM_PCTL_1H = 8;
  private static final int AGG_ELEM_PCTL_1D = 9;
  private static final int AGG_ELEM_PCTL_TOTAL = 10;
  private static final int AGG_SLA = 11;
  private static final int AGG_DURATION_BUCKET = 12;
  private static final int AGG_NO_INCIDENT = 13;
  private static final int AGG_DISTINCT_1H = 14;
  private static final int AGG_DISTINCT_1D = 15;
  private static final int AGG_TOPK_1M = 16;
  private static final int AGG_TOPK_1H = 17;
  private static final int AGG_TOPK_1D = 18;
  private static final int AGG_TOPK_TOTAL = 19;
  private static final int AGG_ACTIVE = 20;
  private static final int AGG_ACTIVATED = 21;
  private static final int AGG_INCIDENT_FREQ = 22;
  private static final int AGG_INCIDENT_OPEN = 23;
  private static final int AGG_INCIDENT_DUR = 24;

  private static final SourceCoordinate<ProcessInstanceExecutionTimeFact> EXEC_COORD =
      coord(
          ProcessInstanceExecutionTimeFact::sourcePartitionId,
          ProcessInstanceExecutionTimeFact::sourcePosition);
  private static final SourceCoordinate<ElementExecutionFact> ELEMENT_COORD =
      coord(ElementExecutionFact::sourcePartitionId, ElementExecutionFact::sourcePosition);
  private static final SourceCoordinate<SlaCohortFact> SLA_COORD =
      coord(SlaCohortFact::sourcePartitionId, SlaCohortFact::sourcePosition);
  private static final SourceCoordinate<IncidentCohortFact> INCIDENT_COHORT_COORD =
      coord(IncidentCohortFact::sourcePartitionId, IncidentCohortFact::sourcePosition);
  private static final SourceCoordinate<ProcessInstanceLifecycleFact> LIFECYCLE_COORD =
      coord(
          ProcessInstanceLifecycleFact::sourcePartitionId,
          ProcessInstanceLifecycleFact::sourcePosition);
  private static final SourceCoordinate<IncidentFact> INCIDENT_COORD =
      coord(IncidentFact::sourcePartitionId, IncidentFact::sourcePosition);
  private static final SourceCoordinate<IncidentDurationFact> INCIDENT_DUR_COORD =
      coord(IncidentDurationFact::sourcePartitionId, IncidentDurationFact::sourcePosition);

  private Metrics() {}

  /**
   * All windowed metric specs. {@code slaMs} is the duration-SLA threshold for the SLA-met cohort.
   */
  public static List<MetricSpec<?, ?, ?>> specs(final long slaMs) {
    final List<MetricSpec<?, ?, ?>> specs = new ArrayList<>();

    // 1 — process-instance execution time by region.
    specs.add(
        new MetricSpec<ProcessInstanceExecutionTimeFact, RegionKey, ExecutionTimeAccumulator>(
            AGG_REGION,
            ProcessInstanceExecutionTimeFact.class,
            new ExecutionTimeAggregateFunction<>(ProcessInstanceExecutionTimeFact::durationMs),
            fact ->
                new RegionKey(
                    fact.variables().getOrDefault("region", NO_REGION),
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId()),
            ProcessInstanceExecutionTimeFact::endTime,
            EXEC_COORD,
            MINUTE_WINDOW_MS,
            ALLOWED_LATENESS_MS,
            new RegionKeyValue(),
            new ExecutionTimeAccumulatorValue(),
            noDrain(),
            ds -> {
              final var initSink =
                  new JdbcRegionExecutionTimeSink(ds, REGION_DATASET_ID, MINUTE_WINDOW_MS);
              initSink.initSchema();
              return initSink;
            }));

    // 2 — per-element execution heatmap.
    specs.add(
        new MetricSpec<ElementExecutionFact, ElementKey, ExecutionTimeAccumulator>(
            AGG_HEATMAP,
            ElementExecutionFact.class,
            new ExecutionTimeAggregateFunction<>(ElementExecutionFact::durationMs),
            Metrics::elementKey,
            ElementExecutionFact::completionTimeMs,
            ELEMENT_COORD,
            MINUTE_WINDOW_MS,
            ALLOWED_LATENESS_MS,
            new ElementKeyValue(),
            new ExecutionTimeAccumulatorValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcElementHeatmapSink(ds, MINUTE_WINDOW_MS);
              initSink.initSchema();
              return initSink;
            }));

    // 3-6 — definition duration percentiles (KLL), tiered.
    addDefinitionPercentileTier(specs, AGG_DEF_PCTL_1M, MINUTE_WINDOW_MS, "1m");
    addDefinitionPercentileTier(specs, AGG_DEF_PCTL_1H, HOUR_WINDOW_MS, "1h");
    addDefinitionPercentileTier(specs, AGG_DEF_PCTL_1D, DAY_WINDOW_MS, "1d");
    addDefinitionPercentileTier(specs, AGG_DEF_PCTL_TOTAL, TOTAL_WINDOW_MS, "total");

    // 7-10 — element duration percentiles (KLL), tiered.
    addElementPercentileTier(specs, AGG_ELEM_PCTL_1M, MINUTE_WINDOW_MS, "1m");
    addElementPercentileTier(specs, AGG_ELEM_PCTL_1H, HOUR_WINDOW_MS, "1h");
    addElementPercentileTier(specs, AGG_ELEM_PCTL_1D, DAY_WINDOW_MS, "1d");
    addElementPercentileTier(specs, AGG_ELEM_PCTL_TOTAL, TOTAL_WINDOW_MS, "total");

    // 11 — SLA-met percentage by definition (start cohort, drained when settled).
    specs.add(
        new MetricSpec<SlaCohortFact, DefinitionKey, SlaCohortAccumulator>(
            AGG_SLA,
            SlaCohortFact.class,
            new SlaCohortAggregateFunction(slaMs),
            fact ->
                new DefinitionKey(
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId()),
            SlaCohortFact::startTime,
            SLA_COORD,
            MINUTE_WINDOW_MS,
            COHORT_LATENESS_MS,
            new DefinitionKeyValue(),
            new SlaCohortAccumulatorValue(),
            acc -> acc.started() > 0 && acc.settled() >= acc.started(),
            ds -> {
              final var initSink = new JdbcSlaCohortSink(ds, MINUTE_WINDOW_MS, slaMs);
              initSink.initSchema();
              return initSink;
            }));

    // 12 — completion-time distribution by definition (start cohort).
    specs.add(
        new MetricSpec<SlaCohortFact, DefinitionKey, DurationBucketAccumulator>(
            AGG_DURATION_BUCKET,
            SlaCohortFact.class,
            new DurationBucketAggregateFunction(DURATION_BUCKETS_MS),
            fact ->
                new DefinitionKey(
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId()),
            SlaCohortFact::startTime,
            SLA_COORD,
            MINUTE_WINDOW_MS,
            COHORT_LATENESS_MS,
            new DefinitionKeyValue(),
            new DurationBucketAccumulatorValue(),
            acc -> acc.started() > 0 && acc.settled() >= acc.started(),
            ds -> {
              final var initSink = new JdbcDurationBucketSink(ds, MINUTE_WINDOW_MS);
              initSink.initSchema();
              return initSink;
            }));

    // 13 — no-incident percentage by definition (start cohort; version dropped to 0).
    specs.add(
        new MetricSpec<IncidentCohortFact, DefinitionKey, RatioAccumulator>(
            AGG_NO_INCIDENT,
            IncidentCohortFact.class,
            new NoIncidentCohortAggregateFunction(),
            fact ->
                new DefinitionKey(
                    fact.bpmnProcessId(), fact.processDefinitionKey(), 0, fact.tenantId()),
            IncidentCohortFact::startTime,
            INCIDENT_COHORT_COORD,
            MINUTE_WINDOW_MS,
            COHORT_LATENESS_MS,
            new DefinitionKeyValue(),
            new RatioAccumulatorRecordValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcDefinitionRatioSink(ds, MINUTE_WINDOW_MS, "no_incident");
              initSink.initSchema();
              return initSink;
            }));

    // 14-15 — distinct processes per tenant (HLL), tiered.
    addDistinctTier(specs, AGG_DISTINCT_1H, HOUR_WINDOW_MS, "1h");
    addDistinctTier(specs, AGG_DISTINCT_1D, DAY_WINDOW_MS, "1d");

    // 16-19 — top processes per tenant (frequent items), tiered.
    addTopProcessesTier(specs, AGG_TOPK_1M, MINUTE_WINDOW_MS, "1m");
    addTopProcessesTier(specs, AGG_TOPK_1H, HOUR_WINDOW_MS, "1h");
    addTopProcessesTier(specs, AGG_TOPK_1D, DAY_WINDOW_MS, "1d");
    addTopProcessesTier(specs, AGG_TOPK_TOTAL, TOTAL_WINDOW_MS, "total");

    // 20 — active instances (all-time gauge of +1/-1 deltas by definition).
    specs.add(
        new MetricSpec<ProcessInstanceLifecycleFact, DefinitionKey, Long>(
            AGG_ACTIVE,
            ProcessInstanceLifecycleFact.class,
            new SumAggregateFunction<>(ProcessInstanceLifecycleFact::delta),
            Metrics::lifecycleDefinitionKey,
            ProcessInstanceLifecycleFact::timestamp,
            LIFECYCLE_COORD,
            TOTAL_WINDOW_MS,
            ALLOWED_LATENESS_MS,
            new DefinitionKeyValue(),
            new LongRecordValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcActiveInstancesSink(ds);
              initSink.initSchema();
              return initSink;
            }));

    // 21 — activated instances (windowed count of activations).
    specs.add(
        new MetricSpec<ProcessInstanceLifecycleFact, DefinitionKey, Long>(
            AGG_ACTIVATED,
            ProcessInstanceLifecycleFact.class,
            new SumAggregateFunction<>(fact -> fact.delta() > 0 ? 1L : 0L),
            Metrics::lifecycleDefinitionKey,
            ProcessInstanceLifecycleFact::timestamp,
            LIFECYCLE_COORD,
            MINUTE_WINDOW_MS,
            ALLOWED_LATENESS_MS,
            new DefinitionKeyValue(),
            new LongRecordValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcActivatedInstancesSink(ds, MINUTE_WINDOW_MS);
              initSink.initSchema();
              return initSink;
            }));

    // 22 — incident frequency per flow node (windowed count of +1s).
    specs.add(
        new MetricSpec<IncidentFact, IncidentKey, Long>(
            AGG_INCIDENT_FREQ,
            IncidentFact.class,
            new SumAggregateFunction<>(fact -> fact.delta() > 0 ? 1L : 0L),
            Metrics::incidentKey,
            IncidentFact::timestamp,
            INCIDENT_COORD,
            MINUTE_WINDOW_MS,
            ALLOWED_LATENESS_MS,
            new IncidentKeyValue(),
            new LongRecordValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcIncidentFrequencySink(ds, MINUTE_WINDOW_MS);
              initSink.initSchema();
              return initSink;
            }));

    // 23 — open incidents per flow node (all-time gauge of +1/-1).
    specs.add(
        new MetricSpec<IncidentFact, IncidentKey, Long>(
            AGG_INCIDENT_OPEN,
            IncidentFact.class,
            new SumAggregateFunction<>(IncidentFact::delta),
            Metrics::incidentKey,
            IncidentFact::timestamp,
            INCIDENT_COORD,
            TOTAL_WINDOW_MS,
            ALLOWED_LATENESS_MS,
            new IncidentKeyValue(),
            new LongRecordValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcOpenIncidentsSink(ds);
              initSink.initSchema();
              return initSink;
            }));

    // 24 — incident open->resolve duration per flow node.
    specs.add(
        new MetricSpec<IncidentDurationFact, IncidentKey, ExecutionTimeAccumulator>(
            AGG_INCIDENT_DUR,
            IncidentDurationFact.class,
            new ExecutionTimeAggregateFunction<>(IncidentDurationFact::durationMs),
            fact -> new IncidentKey(fact.bpmnProcessId(), fact.elementId(), fact.tenantId()),
            IncidentDurationFact::resolvedTimeMs,
            INCIDENT_DUR_COORD,
            MINUTE_WINDOW_MS,
            ALLOWED_LATENESS_MS,
            new IncidentKeyValue(),
            new ExecutionTimeAccumulatorValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcIncidentDurationSink(ds, MINUTE_WINDOW_MS);
              initSink.initSchema();
              return initSink;
            }));

    return specs;
  }

  private static void addDefinitionPercentileTier(
      final List<MetricSpec<?, ?, ?>> specs,
      final int aggId,
      final long windowMs,
      final String label) {
    specs.add(
        new MetricSpec<ProcessInstanceExecutionTimeFact, DefinitionKey, KllDoublesSketch>(
            aggId,
            ProcessInstanceExecutionTimeFact.class,
            new QuantileAggregateFunction<>(fact -> (double) fact.durationMs()),
            Metrics::definitionKey,
            ProcessInstanceExecutionTimeFact::endTime,
            EXEC_COORD,
            windowMs,
            ALLOWED_LATENESS_MS,
            new DefinitionKeyValue(),
            new KllDoublesSketchValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcDefinitionDurationPercentileSink(ds, windowMs, label);
              initSink.initSchema();
              return initSink;
            }));
  }

  private static void addElementPercentileTier(
      final List<MetricSpec<?, ?, ?>> specs,
      final int aggId,
      final long windowMs,
      final String label) {
    specs.add(
        new MetricSpec<ElementExecutionFact, ElementKey, KllDoublesSketch>(
            aggId,
            ElementExecutionFact.class,
            new QuantileAggregateFunction<>(fact -> (double) fact.durationMs()),
            Metrics::elementKey,
            ElementExecutionFact::completionTimeMs,
            ELEMENT_COORD,
            windowMs,
            ALLOWED_LATENESS_MS,
            new ElementKeyValue(),
            new KllDoublesSketchValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcElementDurationPercentileSink(ds, windowMs, label);
              initSink.initSchema();
              return initSink;
            }));
  }

  private static void addDistinctTier(
      final List<MetricSpec<?, ?, ?>> specs,
      final int aggId,
      final long windowMs,
      final String label) {
    specs.add(
        new MetricSpec<ProcessInstanceExecutionTimeFact, String, HllSketch>(
            aggId,
            ProcessInstanceExecutionTimeFact.class,
            new DistinctCountAggregateFunction<>(ProcessInstanceExecutionTimeFact::bpmnProcessId),
            ProcessInstanceExecutionTimeFact::tenantId,
            ProcessInstanceExecutionTimeFact::endTime,
            EXEC_COORD,
            windowMs,
            ALLOWED_LATENESS_MS,
            new StringRecordValue(),
            new HllSketchValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcTenantDistinctProcessSink(ds, windowMs, label);
              initSink.initSchema();
              return initSink;
            }));
  }

  private static void addTopProcessesTier(
      final List<MetricSpec<?, ?, ?>> specs,
      final int aggId,
      final long windowMs,
      final String label) {
    specs.add(
        new MetricSpec<ProcessInstanceExecutionTimeFact, String, ItemsSketch<String>>(
            aggId,
            ProcessInstanceExecutionTimeFact.class,
            new TopKAggregateFunction<>(
                ProcessInstanceExecutionTimeFact::bpmnProcessId,
                TOP_K,
                TopKAggregateFunction.DEFAULT_MAX_MAP_SIZE),
            ProcessInstanceExecutionTimeFact::tenantId,
            ProcessInstanceExecutionTimeFact::endTime,
            EXEC_COORD,
            windowMs,
            ALLOWED_LATENESS_MS,
            new StringRecordValue(),
            new ItemsSketchValue(),
            noDrain(),
            ds -> {
              final var initSink = new JdbcTenantTopProcessesSink(ds, windowMs, label);
              initSink.initSchema();
              return initSink;
            }));
  }

  private static DefinitionKey definitionKey(final ProcessInstanceExecutionTimeFact fact) {
    return new DefinitionKey(
        fact.bpmnProcessId(), fact.processDefinitionKey(), fact.version(), fact.tenantId());
  }

  private static DefinitionKey lifecycleDefinitionKey(final ProcessInstanceLifecycleFact fact) {
    return new DefinitionKey(
        fact.bpmnProcessId(), fact.processDefinitionKey(), fact.version(), fact.tenantId());
  }

  private static ElementKey elementKey(final ElementExecutionFact fact) {
    return new ElementKey(
        fact.bpmnProcessId(),
        fact.processDefinitionKey(),
        fact.version(),
        fact.tenantId(),
        fact.elementId(),
        fact.elementType());
  }

  private static IncidentKey incidentKey(final IncidentFact fact) {
    return new IncidentKey(fact.bpmnProcessId(), fact.elementId(), fact.tenantId());
  }

  private static <ACC> Predicate<ACC> noDrain() {
    return acc -> false;
  }

  private static <F> SourceCoordinate<F> coord(
      final ToIntFunction<F> partition, final ToLongFunction<F> position) {
    return new SourceCoordinate<>() {
      @Override
      public int partition(final F fact) {
        return partition.applyAsInt(fact);
      }

      @Override
      public long position(final F fact) {
        return position.applyAsLong(fact);
      }
    };
  }
}
