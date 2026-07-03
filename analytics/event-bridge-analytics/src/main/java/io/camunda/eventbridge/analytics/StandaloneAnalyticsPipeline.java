/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.analytics.sketch.DistinctCountAggregateFunction;
import io.camunda.analytics.sketch.HllSketchCodec;
import io.camunda.analytics.sketch.ItemsSketchCodec;
import io.camunda.analytics.sketch.KllDoublesSketchCodec;
import io.camunda.analytics.sketch.QuantileAggregateFunction;
import io.camunda.analytics.sketch.TopKAggregateFunction;
import io.camunda.eventbridge.analytics.element.ElementExecutionFact;
import io.camunda.eventbridge.analytics.element.ElementKey;
import io.camunda.eventbridge.analytics.element.ElementKeyCodec;
import io.camunda.eventbridge.analytics.element.JdbcElementDurationPercentileSink;
import io.camunda.eventbridge.analytics.element.JdbcElementHeatmapSink;
import io.camunda.eventbridge.analytics.fact.IncidentCohortFact;
import io.camunda.eventbridge.analytics.fact.IncidentDurationFact;
import io.camunda.eventbridge.analytics.fact.IncidentFact;
import io.camunda.eventbridge.analytics.fact.ProcessDefinitionFact;
import io.camunda.eventbridge.analytics.fact.ProcessExecutionFact;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceLifecycleFact;
import io.camunda.eventbridge.analytics.fact.SlaCohortFact;
import io.camunda.eventbridge.analytics.metric.DefinitionKey;
import io.camunda.eventbridge.analytics.metric.DefinitionKeyCodec;
import io.camunda.eventbridge.analytics.metric.DurationBucketAccumulatorCodec;
import io.camunda.eventbridge.analytics.metric.DurationBucketAggregateFunction;
import io.camunda.eventbridge.analytics.metric.ExecutionTimeAccumulatorCodec;
import io.camunda.eventbridge.analytics.metric.ExecutionTimeAggregateFunction;
import io.camunda.eventbridge.analytics.metric.IncidentKey;
import io.camunda.eventbridge.analytics.metric.IncidentKeyCodec;
import io.camunda.eventbridge.analytics.metric.JdbcActivatedInstancesSink;
import io.camunda.eventbridge.analytics.metric.JdbcActiveInstancesSink;
import io.camunda.eventbridge.analytics.metric.JdbcDefinitionDurationPercentileSink;
import io.camunda.eventbridge.analytics.metric.JdbcDefinitionRatioSink;
import io.camunda.eventbridge.analytics.metric.JdbcDurationBucketSink;
import io.camunda.eventbridge.analytics.metric.JdbcIncidentDurationSink;
import io.camunda.eventbridge.analytics.metric.JdbcIncidentFrequencySink;
import io.camunda.eventbridge.analytics.metric.JdbcOpenIncidentsSink;
import io.camunda.eventbridge.analytics.metric.JdbcProcessDefinitionSink;
import io.camunda.eventbridge.analytics.metric.JdbcRegionExecutionTimeSink;
import io.camunda.eventbridge.analytics.metric.JdbcSlaCohortSink;
import io.camunda.eventbridge.analytics.metric.JdbcTenantDistinctProcessSink;
import io.camunda.eventbridge.analytics.metric.JdbcTenantTopProcessesSink;
import io.camunda.eventbridge.analytics.metric.NoIncidentCohortAggregateFunction;
import io.camunda.eventbridge.analytics.metric.RegionKey;
import io.camunda.eventbridge.analytics.metric.RegionKeyCodec;
import io.camunda.eventbridge.analytics.metric.SlaCohortAccumulatorCodec;
import io.camunda.eventbridge.analytics.metric.SlaCohortAggregateFunction;
import io.camunda.eventbridge.analytics.projection.AnalyticsColumnFamilies;
import io.camunda.eventbridge.analytics.projection.ProcessExecutionProjector;
import io.camunda.eventbridge.analytics.projection.StateBackedProjectionStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.ProjectionStage;
import io.camunda.eventbridge.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.Codec;
import io.camunda.eventbridge.streaming.aggregate.DurableMaterializedRollup;
import io.camunda.eventbridge.streaming.aggregate.KeySelector;
import io.camunda.eventbridge.streaming.aggregate.LongCodec;
import io.camunda.eventbridge.streaming.aggregate.RatioAccumulatorCodec;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.aggregate.Rollup;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.aggregate.StringCodec;
import io.camunda.eventbridge.streaming.aggregate.SumAggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.TypeRoutingRollup;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToLongFunction;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the consumer-based analytics as a standalone process against a running Event Bridge: consume
 * {@code zeebe-records}, fold once into a RocksDB base projection ({@link
 * ProcessExecutionProjector}), and fan the derived facts out to a set of durable windowed rollups
 * built on the streaming library. These mirror the metrics on Optimize's default dashboards:
 * process-instance execution time (avg/min/max) by region, the per-element duration heatmap,
 * duration percentiles (p50/p75/p90/p99) by process definition and by element, the SLA-met and
 * no-incident percentages by definition, plus distinct-process and top-process (heavy-hitter)
 * rollups by tenant. The base projection and every rollup share one RocksDB (keyed by a per-rollup
 * id), so one checkpoint transaction commits them and the consumed offset as a single atomic cut;
 * each rollup converges an H2 serving table by idempotent full-value upsert ({@link
 * DurableMaterializedRollup}); the source is resumed from the base projection's checkpointed
 * position on restart (start-from-offset).
 *
 * <p>System properties: {@code group} (default {@code analytics-projection}), {@code gateway}
 * (default {@code http://localhost:8080}), {@code sourceTopic} ({@code zeebe-records}), {@code
 * instanceId} (default = PID), {@code jdbcUrl}/{@code jdbcUser} (the shared serving DB), {@code
 * slaMs} (the duration-SLA threshold for the SLA-met percentage, default five minutes).
 */
public final class StandaloneAnalyticsPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneAnalyticsPipeline.class);
  private static final long MINUTE_WINDOW_MS = 60_000L; // 1-minute windows for the live metrics
  private static final long HOUR_WINDOW_MS = 3_600_000L; // hourly window (distinct + the 1h tier)
  private static final long DAY_WINDOW_MS = 86_400_000L; // daily window (the coarse 1d tier)
  // A single all-time bucket: a window larger than any timestamp so windowStart is always 0. Used
  // for the coarsest granularity of the time hierarchy (exact all-time percentiles, one row/key).
  private static final long TOTAL_WINDOW_MS = 10_000L * 365 * 24 * 60 * 60 * 1000;
  private static final long ALLOWED_LATENESS_MS = 30_000L; // grace before a window finalizes
  // Start cohorts (SLA, no-incident) are keyed by start time but also receive a much later signal
  // (the completion outcome / the instance's first incident). Keep those windows open well past the
  // longest instance run so that late signal lands before the window is pruned — otherwise it would
  // re-create the pruned cell from scratch and the overwrite-sink would wipe the started count.
  // Must
  // exceed the maximum instance duration (incl. replay watermark skew).
  private static final long COHORT_LATENESS_MS = 600_000L;
  private static final long REGION_DATASET_ID = 1L; // the webapp's auto-created dataset
  private static final String NO_REGION = "<none>";
  private static final int TOP_K = 10; // heavy hitters retained per tenant/window
  private static final long DEFAULT_SLA_MS = 300_000L; // "SLA met" threshold: instance under 5 min
  // Duration bands for the completion-time distribution: ≤10s, ≤30s, ≤60s, ≤120s, >120s.
  private static final long[] DURATION_BUCKETS_MS = {10_000L, 30_000L, 60_000L, 120_000L};

  private StandaloneAnalyticsPipeline() {}

  public static void main(final String[] args) throws InterruptedException {
    final String group = System.getProperty("group", "analytics-projection");
    final String gateway = System.getProperty("gateway", "http://localhost:8080");
    final String sourceTopic = System.getProperty("sourceTopic", "zeebe-records");
    final String instanceId =
        System.getProperty("instanceId", "projector-" + ProcessHandle.current().pid());
    final String jdbcUrl =
        System.getProperty("jdbcUrl", "jdbc:h2:file:./data/analytics-dataset;DB_CLOSE_DELAY=-1");
    final String jdbcUser = System.getProperty("jdbcUser", "sa");
    final long slaMs = Long.getLong("slaMs", DEFAULT_SLA_MS);

    final EventBridgeClient client = EventBridgeClient.create(gateway);
    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    // one RocksDB for the whole stage: the base projection AND every rollup share it, so a single
    // checkpoint transaction commits them and the offset as one atomic cut.
    final RocksDbStateStoreProvider<AnalyticsColumnFamilies> provider =
        RocksDbStateStoreProvider.open(new File("data/analytics-" + instanceId), meterRegistry);
    final StateBackedProjectionStore store = StateBackedProjectionStore.fromProvider(provider);
    final ProcessExecutionProjector projector =
        new ProcessExecutionProjector(store, store.elementStarts());

    // all rollups share one cell store and one offset store, distinguished by a stable rollupId
    // (assigned by construction order below); a new metric/dataset is a new id, not a new CF.
    final KeyValueStore<DbBytes, DbBytes> rollupCells =
        provider.keyValueStore(AnalyticsColumnFamilies.ROLLUP_CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbBytes, DbLong> rollupOffsets =
        provider.keyValueStore(AnalyticsColumnFamilies.ROLLUP_OFFSETS, new DbBytes(), new DbLong());
    final AtomicInteger rollupIds = new AtomicInteger(1);

    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(jdbcUrl);
    dataSource.setUser(jdbcUser);

    // metric 1 — process-instance execution time by region. The windowed aggregate is durable
    // (RocksDB) and authoritative; each changed cell is upserted as its full value into the serving
    // table by a deterministic key, so re-emit converges instead of double-counting.
    final JdbcRegionExecutionTimeSink regionSink =
        new JdbcRegionExecutionTimeSink(dataSource, REGION_DATASET_ID, MINUTE_WINDOW_MS);
    regionSink.initSchema();
    final SourceCoordinate<ProcessInstanceExecutionTimeFact> regionCoordinate =
        new SourceCoordinate<>() {
          @Override
          public int partition(final ProcessInstanceExecutionTimeFact fact) {
            return fact.sourcePartitionId();
          }

          @Override
          public long position(final ProcessInstanceExecutionTimeFact fact) {
            return fact.sourcePosition();
          }
        };
    final Rollup<ProcessInstanceExecutionTimeFact> regionRollup =
        new DurableMaterializedRollup<>(
            rollupIds.getAndIncrement(),
            new ExecutionTimeAggregateFunction<>(ProcessInstanceExecutionTimeFact::durationMs),
            fact ->
                new RegionKey(
                    fact.variables().getOrDefault("region", NO_REGION),
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId()),
            ProcessInstanceExecutionTimeFact::endTime,
            regionCoordinate,
            TumblingWindows.of(MINUTE_WINDOW_MS),
            ALLOWED_LATENESS_MS,
            regionSink,
            rollupCells,
            rollupOffsets,
            new RegionKeyCodec(),
            new ExecutionTimeAccumulatorCodec(),
            provider::runInTransaction);

    // metric 2 — per-element execution heatmap, same durable/idempotent machinery.
    final JdbcElementHeatmapSink heatmapSink =
        new JdbcElementHeatmapSink(dataSource, MINUTE_WINDOW_MS);
    heatmapSink.initSchema();
    final SourceCoordinate<ElementExecutionFact> heatmapCoordinate =
        new SourceCoordinate<>() {
          @Override
          public int partition(final ElementExecutionFact fact) {
            return fact.sourcePartitionId();
          }

          @Override
          public long position(final ElementExecutionFact fact) {
            return fact.sourcePosition();
          }
        };
    final Rollup<ElementExecutionFact> heatmapRollup =
        new DurableMaterializedRollup<>(
            rollupIds.getAndIncrement(),
            new ExecutionTimeAggregateFunction<>(ElementExecutionFact::durationMs),
            fact ->
                new ElementKey(
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId(),
                    fact.elementId(),
                    fact.elementType()),
            ElementExecutionFact::completionTimeMs,
            heatmapCoordinate,
            TumblingWindows.of(MINUTE_WINDOW_MS),
            ALLOWED_LATENESS_MS,
            heatmapSink,
            rollupCells,
            rollupOffsets,
            new ElementKeyCodec(),
            new ExecutionTimeAccumulatorCodec(),
            provider::runInTransaction);

    // metric 3 — process-instance duration percentiles (p50/p75/p90/p99) by process definition,
    // from the same completion fact as metric 1. These are the ranks Optimize's default duration
    // tiles use; the windowing supplies the control-chart trend over time. The accumulator is a KLL
    // sketch, so its merge is a sketch union — it pre-aggregates and combines across partitions
    // exactly as the additive metrics do.
    //
    // Time hierarchy: the SAME facts feed a rollup at every tier (1m/1h/1d and a single all-time
    // bucket), run in parallel — no background compaction. Because the sketch merge is associative,
    // aggregating raw facts into a coarse window equals merging the fine windows underneath it, so
    // every tier is exact; a range read then merges the coarsest tier that still resolves the range
    // instead of thousands of 1m sketch blobs (see DashboardRepository#granularityFor).
    final List<Tier> defPctlTiers =
        List.of(
            new Tier("1m", MINUTE_WINDOW_MS),
            new Tier("1h", HOUR_WINDOW_MS),
            new Tier("1d", DAY_WINDOW_MS),
            new Tier("total", TOTAL_WINDOW_MS));
    final List<Rollup<ProcessExecutionFact>> defPercentileRollups = new ArrayList<>();
    for (final Tier tier : defPctlTiers) {
      final JdbcDefinitionDurationPercentileSink sink =
          new JdbcDefinitionDurationPercentileSink(dataSource, tier.windowMs(), tier.label());
      sink.initSchema();
      defPercentileRollups.add(
          new TypeRoutingRollup<ProcessExecutionFact, ProcessInstanceExecutionTimeFact>(
              ProcessInstanceExecutionTimeFact.class,
              buildRollup(
                  rollupIds.getAndIncrement(),
                  new QuantileAggregateFunction<>(fact -> (double) fact.durationMs()),
                  StandaloneAnalyticsPipeline::definitionKey,
                  ProcessInstanceExecutionTimeFact::endTime,
                  regionCoordinate,
                  tier.windowMs(),
                  sink,
                  rollupCells,
                  rollupOffsets,
                  new DefinitionKeyCodec(),
                  new KllDoublesSketchCodec(),
                  provider::runInTransaction)));
    }

    // metric 4 — per-element (flow-node) duration percentiles, complementing the heatmap's
    // avg/min/max so the default flownode-duration heatmap can show p50 per node. Same time
    // hierarchy as the definition percentiles.
    final List<Tier> elemPctlTiers =
        List.of(
            new Tier("1m", MINUTE_WINDOW_MS),
            new Tier("1h", HOUR_WINDOW_MS),
            new Tier("1d", DAY_WINDOW_MS),
            new Tier("total", TOTAL_WINDOW_MS));
    final List<Rollup<ProcessExecutionFact>> elementPercentileRollups = new ArrayList<>();
    for (final Tier tier : elemPctlTiers) {
      final JdbcElementDurationPercentileSink sink =
          new JdbcElementDurationPercentileSink(dataSource, tier.windowMs(), tier.label());
      sink.initSchema();
      elementPercentileRollups.add(
          new TypeRoutingRollup<ProcessExecutionFact, ElementExecutionFact>(
              ElementExecutionFact.class,
              buildRollup(
                  rollupIds.getAndIncrement(),
                  new QuantileAggregateFunction<>(fact -> (double) fact.durationMs()),
                  fact ->
                      new ElementKey(
                          fact.bpmnProcessId(),
                          fact.processDefinitionKey(),
                          fact.version(),
                          fact.tenantId(),
                          fact.elementId(),
                          fact.elementType()),
                  ElementExecutionFact::completionTimeMs,
                  heatmapCoordinate,
                  tier.windowMs(),
                  sink,
                  rollupCells,
                  rollupOffsets,
                  new ElementKeyCodec(),
                  new KllDoublesSketchCodec(),
                  provider::runInTransaction)));
    }

    // metric 5 — % of instances meeting the duration SLA, by definition (Optimize's percentSLAMet),
    // measured forward-looking over the START cohort: the denominator is instances that started in
    // the window, the numerator those that completed normally within the target. Keyed and windowed
    // by START time so both signals land in the same cohort; the window is kept open for the length
    // of the SLA target (allowed lateness = slaMs) so a still-running instance that blows its
    // deadline lowers the ratio at cohort maturity rather than staying invisible until it finishes.
    final SourceCoordinate<SlaCohortFact> slaCoordinate =
        new SourceCoordinate<>() {
          @Override
          public int partition(final SlaCohortFact fact) {
            return fact.sourcePartitionId();
          }

          @Override
          public long position(final SlaCohortFact fact) {
            return fact.sourcePosition();
          }
        };
    final JdbcSlaCohortSink slaCohortSink =
        new JdbcSlaCohortSink(dataSource, MINUTE_WINDOW_MS, slaMs);
    slaCohortSink.initSchema();
    final Rollup<SlaCohortFact> slaCohortRollup =
        new DurableMaterializedRollup<>(
            rollupIds.getAndIncrement(),
            new SlaCohortAggregateFunction(slaMs),
            fact ->
                new DefinitionKey(
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId()),
            SlaCohortFact::startTime,
            slaCoordinate,
            TumblingWindows.of(MINUTE_WINDOW_MS),
            COHORT_LATENESS_MS, // keep the start window open past the longest run for late outcomes
            slaCohortSink,
            rollupCells,
            rollupOffsets,
            new DefinitionKeyCodec(),
            new SlaCohortAccumulatorCodec(),
            provider::runInTransaction,
            acc -> acc.started() > 0 && acc.settled() >= acc.started()); // drained: all settled

    // completion-time distribution per START cohort: of the instances that started in a window, how
    // many finished in each duration band. A superset of SLA-met (the one-threshold case); reuses
    // the same cohort signals (activation + outcome duration) and drains the same way.
    final JdbcDurationBucketSink durationBucketSink =
        new JdbcDurationBucketSink(dataSource, MINUTE_WINDOW_MS);
    durationBucketSink.initSchema();
    final Rollup<SlaCohortFact> durationBucketRollup =
        new DurableMaterializedRollup<>(
            rollupIds.getAndIncrement(),
            new DurationBucketAggregateFunction(DURATION_BUCKETS_MS),
            fact ->
                new DefinitionKey(
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId()),
            SlaCohortFact::startTime,
            slaCoordinate,
            TumblingWindows.of(MINUTE_WINDOW_MS),
            COHORT_LATENESS_MS,
            durationBucketSink,
            rollupCells,
            rollupOffsets,
            new DefinitionKeyCodec(),
            new DurationBucketAccumulatorCodec(),
            provider::runInTransaction,
            acc -> acc.started() > 0 && acc.settled() >= acc.started()); // drained: all settled

    // metric 6 — % of instances without an incident, by definition (Optimize's percentNoIncidents),
    // measured forward-looking over the START cohort: the denominator is instances that started in
    // the window and the numerator those with no incident. The instance's first incident is stamped
    // with its start time (so it lands in the same cohort) and fires when the incident is created —
    // so a running instance's incident lowers the share immediately, and it counts once per
    // instance (distinct). Version is dropped from the key (0) since the incident record lacks it;
    // the read groups by process anyway.
    final SourceCoordinate<IncidentCohortFact> noIncidentCoordinate =
        new SourceCoordinate<>() {
          @Override
          public int partition(final IncidentCohortFact fact) {
            return fact.sourcePartitionId();
          }

          @Override
          public long position(final IncidentCohortFact fact) {
            return fact.sourcePosition();
          }
        };
    final JdbcDefinitionRatioSink incidentRatioSink =
        new JdbcDefinitionRatioSink(dataSource, MINUTE_WINDOW_MS, "no_incident");
    incidentRatioSink.initSchema();
    final Rollup<IncidentCohortFact> incidentRatioRollup =
        new DurableMaterializedRollup<>(
            rollupIds.getAndIncrement(),
            new NoIncidentCohortAggregateFunction(),
            fact ->
                new DefinitionKey(
                    fact.bpmnProcessId(), fact.processDefinitionKey(), 0, fact.tenantId()),
            IncidentCohortFact::startTime,
            noIncidentCoordinate,
            TumblingWindows.of(MINUTE_WINDOW_MS),
            COHORT_LATENESS_MS,
            incidentRatioSink,
            rollupCells,
            rollupOffsets,
            new DefinitionKeyCodec(),
            new RatioAccumulatorCodec(),
            provider::runInTransaction);

    // metric 7 — distinct number of processes active per tenant, from the same fact. The
    // accumulator is an HLL sketch; the count is approximate but the register-wise merge is exact.
    // Kept on an HOURLY window (cardinality is a slow-moving figure — a per-minute distinct count
    // over a low-cardinality set is noise).
    // Distinct is a mergeable HLL sketch too, but it stays coarse: cardinality is slow-moving, so
    // the finest tier is hourly (a per-minute distinct count over a low-cardinality set is noise),
    // with a 1d tier above it so a long range merges days rather than thousands of hours.
    final List<Tier> distinctTiers =
        List.of(new Tier("1h", HOUR_WINDOW_MS), new Tier("1d", DAY_WINDOW_MS));
    final List<Rollup<ProcessExecutionFact>> distinctRollups = new ArrayList<>();
    for (final Tier tier : distinctTiers) {
      final JdbcTenantDistinctProcessSink sink =
          new JdbcTenantDistinctProcessSink(dataSource, tier.windowMs(), tier.label());
      sink.initSchema();
      distinctRollups.add(
          new TypeRoutingRollup<ProcessExecutionFact, ProcessInstanceExecutionTimeFact>(
              ProcessInstanceExecutionTimeFact.class,
              buildRollup(
                  rollupIds.getAndIncrement(),
                  new DistinctCountAggregateFunction<>(
                      ProcessInstanceExecutionTimeFact::bpmnProcessId),
                  ProcessInstanceExecutionTimeFact::tenantId,
                  ProcessInstanceExecutionTimeFact::endTime,
                  regionCoordinate,
                  tier.windowMs(),
                  sink,
                  rollupCells,
                  rollupOffsets,
                  new StringCodec(),
                  new HllSketchCodec(),
                  provider::runInTransaction)));
    }

    // metric 8 — top processes by volume per tenant, from the same fact. The accumulator is a
    // frequent-items sketch; its frequency merge is commutative and associative, so it runs at
    // every
    // tier of the time hierarchy (1m/1h/1d for range reads, total all-time), each storing its
    // sketch
    // for merge-on-read.
    final List<Tier> topkTiers =
        List.of(
            new Tier("1m", MINUTE_WINDOW_MS),
            new Tier("1h", HOUR_WINDOW_MS),
            new Tier("1d", DAY_WINDOW_MS),
            new Tier("total", TOTAL_WINDOW_MS));
    final List<Rollup<ProcessExecutionFact>> topProcessesRollups = new ArrayList<>();
    for (final Tier tier : topkTiers) {
      final JdbcTenantTopProcessesSink sink =
          new JdbcTenantTopProcessesSink(dataSource, tier.windowMs(), tier.label());
      sink.initSchema();
      topProcessesRollups.add(
          new TypeRoutingRollup<ProcessExecutionFact, ProcessInstanceExecutionTimeFact>(
              ProcessInstanceExecutionTimeFact.class,
              buildRollup(
                  rollupIds.getAndIncrement(),
                  new TopKAggregateFunction<>(
                      ProcessInstanceExecutionTimeFact::bpmnProcessId,
                      TOP_K,
                      TopKAggregateFunction.DEFAULT_MAX_MAP_SIZE),
                  ProcessInstanceExecutionTimeFact::tenantId,
                  ProcessInstanceExecutionTimeFact::endTime,
                  regionCoordinate,
                  tier.windowMs(),
                  sink,
                  rollupCells,
                  rollupOffsets,
                  new StringCodec(),
                  new ItemsSketchCodec(),
                  provider::runInTransaction)));
    }

    // instance lifecycle — the projector emits +1 on activation and -1 on completion/termination.
    final SourceCoordinate<ProcessInstanceLifecycleFact> lifecycleCoordinate =
        new SourceCoordinate<>() {
          @Override
          public int partition(final ProcessInstanceLifecycleFact fact) {
            return fact.sourcePartitionId();
          }

          @Override
          public long position(final ProcessInstanceLifecycleFact fact) {
            return fact.sourcePosition();
          }
        };

    // active instances — a range-independent gauge: sum of the +1/-1 deltas in one all-time bucket
    // per definition = the current in-flight count.
    final JdbcActiveInstancesSink activeSink = new JdbcActiveInstancesSink(dataSource);
    activeSink.initSchema();
    final Rollup<ProcessInstanceLifecycleFact> activeRollup =
        new DurableMaterializedRollup<>(
            rollupIds.getAndIncrement(),
            new SumAggregateFunction<>(ProcessInstanceLifecycleFact::delta),
            fact ->
                new DefinitionKey(
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId()),
            ProcessInstanceLifecycleFact::timestamp,
            lifecycleCoordinate,
            TumblingWindows.of(TOTAL_WINDOW_MS),
            ALLOWED_LATENESS_MS,
            activeSink,
            rollupCells,
            rollupOffsets,
            new DefinitionKeyCodec(),
            new LongCodec(),
            provider::runInTransaction);

    // activated instances — a windowed count of activations (the +1s); additive, so a read sums it
    // over the selected range to get "instances started in this range".
    final JdbcActivatedInstancesSink activatedSink =
        new JdbcActivatedInstancesSink(dataSource, MINUTE_WINDOW_MS);
    activatedSink.initSchema();
    final Rollup<ProcessInstanceLifecycleFact> activatedRollup =
        new DurableMaterializedRollup<>(
            rollupIds.getAndIncrement(),
            new SumAggregateFunction<>(fact -> fact.delta() > 0 ? 1L : 0L),
            fact ->
                new DefinitionKey(
                    fact.bpmnProcessId(),
                    fact.processDefinitionKey(),
                    fact.version(),
                    fact.tenantId()),
            ProcessInstanceLifecycleFact::timestamp,
            lifecycleCoordinate,
            TumblingWindows.of(MINUTE_WINDOW_MS),
            ALLOWED_LATENESS_MS,
            activatedSink,
            rollupCells,
            rollupOffsets,
            new DefinitionKeyCodec(),
            new LongCodec(),
            provider::runInTransaction);

    // incidents — the projector emits +1 on create and -1 on resolve, per flow node.
    final SourceCoordinate<IncidentFact> incidentCoordinate =
        new SourceCoordinate<>() {
          @Override
          public int partition(final IncidentFact fact) {
            return fact.sourcePartitionId();
          }

          @Override
          public long position(final IncidentFact fact) {
            return fact.sourcePosition();
          }
        };
    final KeySelector<IncidentFact, IncidentKey> incidentKey =
        fact -> new IncidentKey(fact.bpmnProcessId(), fact.elementId(), fact.tenantId());

    // incident frequency — a windowed count of incidents raised (the +1s) per flow node; additive,
    // so a read sums it over the range and can group by flow node for the BPMN heatmap.
    final JdbcIncidentFrequencySink incidentFrequencySink =
        new JdbcIncidentFrequencySink(dataSource, MINUTE_WINDOW_MS);
    incidentFrequencySink.initSchema();
    final Rollup<IncidentFact> incidentFrequencyRollup =
        buildRollup(
            rollupIds.getAndIncrement(),
            new SumAggregateFunction<>(fact -> fact.delta() > 0 ? 1L : 0L),
            incidentKey,
            IncidentFact::timestamp,
            incidentCoordinate,
            MINUTE_WINDOW_MS,
            incidentFrequencySink,
            rollupCells,
            rollupOffsets,
            new IncidentKeyCodec(),
            new LongCodec(),
            provider::runInTransaction);

    // open incidents — a range-independent gauge: created minus resolved in one all-time bucket per
    // flow node = the number of incidents currently open.
    final JdbcOpenIncidentsSink openIncidentsSink = new JdbcOpenIncidentsSink(dataSource);
    openIncidentsSink.initSchema();
    final Rollup<IncidentFact> openIncidentsRollup =
        buildRollup(
            rollupIds.getAndIncrement(),
            new SumAggregateFunction<>(IncidentFact::delta),
            incidentKey,
            IncidentFact::timestamp,
            incidentCoordinate,
            TOTAL_WINDOW_MS,
            openIncidentsSink,
            rollupCells,
            rollupOffsets,
            new IncidentKeyCodec(),
            new LongCodec(),
            provider::runInTransaction);

    // incident duration — open→resolve time per flow node (count/total/max → avg on read), for the
    // incident-duration heatmap. Windowed by resolve time; reuses the execution-time aggregate.
    final SourceCoordinate<IncidentDurationFact> incidentDurationCoordinate =
        new SourceCoordinate<>() {
          @Override
          public int partition(final IncidentDurationFact fact) {
            return fact.sourcePartitionId();
          }

          @Override
          public long position(final IncidentDurationFact fact) {
            return fact.sourcePosition();
          }
        };
    final JdbcIncidentDurationSink incidentDurationSink =
        new JdbcIncidentDurationSink(dataSource, MINUTE_WINDOW_MS);
    incidentDurationSink.initSchema();
    final Rollup<IncidentDurationFact> incidentDurationRollup =
        buildRollup(
            rollupIds.getAndIncrement(),
            new ExecutionTimeAggregateFunction<>(IncidentDurationFact::durationMs),
            fact -> new IncidentKey(fact.bpmnProcessId(), fact.elementId(), fact.tenantId()),
            IncidentDurationFact::resolvedTimeMs,
            incidentDurationCoordinate,
            MINUTE_WINDOW_MS,
            incidentDurationSink,
            rollupCells,
            rollupOffsets,
            new IncidentKeyCodec(),
            new ExecutionTimeAccumulatorCodec(),
            provider::runInTransaction);

    // definitions — sink each deployed process's BPMN so the dashboard can render the model behind
    // the flow-node heatmap. Not windowed: a direct idempotent upsert by definition key.
    final JdbcProcessDefinitionSink definitionSink = new JdbcProcessDefinitionSink(dataSource);
    definitionSink.initSchema();

    // one processor, one fold, fan the facts out to their rollups by type. The process-instance
    // metrics all consume the same completion fact — the fold derives it once and each routing
    // entry hands it to its own durable rollup; the element metrics likewise share the element
    // fact; the definition sink takes the deployed-process facts. The tiered sketch metrics
    // contribute one routing entry per time-hierarchy tier.
    final List<Rollup<ProcessExecutionFact>> rollups = new ArrayList<>();
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, ProcessInstanceExecutionTimeFact>(
            ProcessInstanceExecutionTimeFact.class, regionRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, ElementExecutionFact>(
            ElementExecutionFact.class, heatmapRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, ProcessDefinitionFact>(
            ProcessDefinitionFact.class, definitionSink));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, ProcessInstanceLifecycleFact>(
            ProcessInstanceLifecycleFact.class, activeRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, ProcessInstanceLifecycleFact>(
            ProcessInstanceLifecycleFact.class, activatedRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, IncidentFact>(
            IncidentFact.class, incidentFrequencyRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, IncidentFact>(
            IncidentFact.class, openIncidentsRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, IncidentDurationFact>(
            IncidentDurationFact.class, incidentDurationRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, SlaCohortFact>(
            SlaCohortFact.class, slaCohortRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, SlaCohortFact>(
            SlaCohortFact.class, durationBucketRollup));
    rollups.add(
        new TypeRoutingRollup<ProcessExecutionFact, IncidentCohortFact>(
            IncidentCohortFact.class, incidentRatioRollup));
    rollups.addAll(defPercentileRollups);
    rollups.addAll(elementPercentileRollups);
    rollups.addAll(distinctRollups);
    rollups.addAll(topProcessesRollups);
    final StreamProcessor<ZeebeRecord> processor =
        new StreamProcessor<ZeebeRecord>().add(new ProjectionStage<>(projector, rollups));

    final ZeebeRecordConsumer source =
        ZeebeRecordConsumer.subscribe(client, group, instanceId, List.of(sourceTopic)).join();

    final WindowedAnalyticsPipeline pipeline =
        new WindowedAnalyticsPipeline(
            source, processor, store, sourceTopic, provider::runInTransaction);
    pipeline.start();
    LOG.info(
        "Analytics instance '{}' started: {} -> base projection -> durable rollups "
            + "(exec-time, heatmap, duration percentiles, SLA + no-incident ratios, distinct, top-k)",
        instanceId,
        sourceTopic);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  pipeline.close();
                  try {
                    provider.close(); // the one RocksDB for the base projection and all rollups
                    client.close();
                  } catch (final Exception ignored) {
                    // shutting down
                  }
                }));
    Thread.currentThread().join();
  }

  /** Groups a process-instance fact by its process definition (id, key, version, tenant). */
  private static DefinitionKey definitionKey(final ProcessInstanceExecutionTimeFact fact) {
    return new DefinitionKey(
        fact.bpmnProcessId(), fact.processDefinitionKey(), fact.version(), fact.tenantId());
  }

  /**
   * One tier of a metric's time hierarchy: the window size, the {@code granularity} label the sink
   * stamps on the row, and the pair of RocksDB column families holding that tier's cells and dedup
   * offsets. Each tier is an independent rollup fed the same facts.
   */
  private record Tier(String label, long windowMs) {}

  /** Builds one durable windowed rollup with a stable id over the shared cell/offset stores. */
  private static <F, K, ACC> Rollup<F> buildRollup(
      final int rollupId,
      final AggregateFunction<F, ACC, ?> aggregate,
      final KeySelector<F, K> keySelector,
      final ToLongFunction<F> eventTime,
      final SourceCoordinate<F> coordinate,
      final long windowMs,
      final ResultSink<Windowed<K>, ACC> sink,
      final KeyValueStore<DbBytes, DbBytes> cells,
      final KeyValueStore<DbBytes, DbLong> offsets,
      final Codec<K> keyCodec,
      final Codec<ACC> accCodec,
      final TransactionRunner tx) {
    return new DurableMaterializedRollup<>(
        rollupId,
        aggregate,
        keySelector,
        eventTime,
        coordinate,
        TumblingWindows.of(windowMs),
        ALLOWED_LATENESS_MS,
        sink,
        cells,
        offsets,
        keyCodec,
        accCodec,
        tx);
  }
}
