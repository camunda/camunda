/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.analytics.streaming.StreamProcessor;
import io.camunda.analytics.streaming.aggregate.DurableMaterializedRollup;
import io.camunda.analytics.streaming.aggregate.Rollup;
import io.camunda.analytics.streaming.aggregate.SourceCoordinate;
import io.camunda.analytics.streaming.aggregate.TypeRoutingRollup;
import io.camunda.analytics.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.eventbridge.analytics.element.ElementExecutionFact;
import io.camunda.eventbridge.analytics.element.ElementKey;
import io.camunda.eventbridge.analytics.element.ElementKeyCodec;
import io.camunda.eventbridge.analytics.element.JdbcElementHeatmapSink;
import io.camunda.eventbridge.analytics.fact.ProcessExecutionFact;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.eventbridge.analytics.metric.ExecutionTimeAccumulatorCodec;
import io.camunda.eventbridge.analytics.metric.ExecutionTimeAggregateFunction;
import io.camunda.eventbridge.analytics.metric.JdbcRegionExecutionTimeSink;
import io.camunda.eventbridge.analytics.metric.RegionKey;
import io.camunda.eventbridge.analytics.metric.RegionKeyCodec;
import io.camunda.eventbridge.analytics.projection.ProcessExecutionProjector;
import io.camunda.eventbridge.analytics.projection.StateBackedProjectionStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordConsumer;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the consumer-based analytics as a standalone process against a running Event Bridge: consume
 * {@code zeebe-records}, fold once into a RocksDB base projection ({@link
 * ProcessExecutionProjector}), and fan the derived facts out to two durable windowed rollups built
 * on the streaming library — process-instance execution time grouped by region, and the per-element
 * heatmap. Each rollup holds its windowed aggregate in its own RocksDB store and converges an H2
 * serving table by idempotent full-value upsert ({@link DurableMaterializedRollup}); the source is
 * resumed from the base projection's checkpointed position on restart (start-from-offset).
 *
 * <p>System properties: {@code group} (default {@code analytics-projection}), {@code gateway}
 * (default {@code http://localhost:8080}), {@code sourceTopic} ({@code zeebe-records}), {@code
 * instanceId} (default = PID), {@code jdbcUrl}/{@code jdbcUser} (the shared serving DB).
 */
public final class StandaloneAnalyticsPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneAnalyticsPipeline.class);
  private static final long WINDOW_SIZE_MS = 3_600_000L; // hourly
  private static final long ALLOWED_LATENESS_MS = 60_000L; // grace before a window finalizes
  private static final long REGION_DATASET_ID = 1L; // the webapp's auto-created dataset
  private static final String NO_REGION = "<none>";

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

    final EventBridgeClient client = EventBridgeClient.create(gateway);
    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    // the single base projection (RocksDB local cache) — folded once, serving both metrics
    final StateBackedProjectionStore store =
        StateBackedProjectionStore.rocksDb(
            new File("data/analytics-projection-" + instanceId), meterRegistry);
    final ProcessExecutionProjector projector =
        new ProcessExecutionProjector(store, store.elementStarts());

    // the rollups' durable aggregate state (RocksDB), separate from the base projection
    final RocksDbStateStoreProvider<RollupColumnFamilies> rollupState =
        RocksDbStateStoreProvider.open(
            new File("data/analytics-rollups-" + instanceId), meterRegistry);

    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(jdbcUrl);
    dataSource.setUser(jdbcUser);

    // metric 1 — process-instance execution time by region. The windowed aggregate is durable
    // (RocksDB) and authoritative; each changed cell is upserted as its full value into the serving
    // table by a deterministic key, so re-emit converges instead of double-counting.
    final JdbcRegionExecutionTimeSink regionSink =
        new JdbcRegionExecutionTimeSink(dataSource, REGION_DATASET_ID, WINDOW_SIZE_MS);
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
            TumblingWindows.of(WINDOW_SIZE_MS),
            ALLOWED_LATENESS_MS,
            regionSink,
            rollupState.keyValueStore(
                RollupColumnFamilies.REGION_CELLS, new DbBytes(), new DbBytes()),
            rollupState.keyValueStore(
                RollupColumnFamilies.REGION_OFFSETS, new DbInt(), new DbLong()),
            new RegionKeyCodec(),
            new ExecutionTimeAccumulatorCodec(),
            rollupState::runInTransaction);

    // metric 2 — per-element execution heatmap, same durable/idempotent machinery.
    final JdbcElementHeatmapSink heatmapSink =
        new JdbcElementHeatmapSink(dataSource, WINDOW_SIZE_MS);
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
            TumblingWindows.of(WINDOW_SIZE_MS),
            ALLOWED_LATENESS_MS,
            heatmapSink,
            rollupState.keyValueStore(
                RollupColumnFamilies.HEATMAP_CELLS, new DbBytes(), new DbBytes()),
            rollupState.keyValueStore(
                RollupColumnFamilies.HEATMAP_OFFSETS, new DbInt(), new DbLong()),
            new ElementKeyCodec(),
            new ExecutionTimeAccumulatorCodec(),
            rollupState::runInTransaction);

    // one processor, one fold, fan the facts out to their rollups by type
    final StreamProcessor<ZeebeRecord> processor =
        new StreamProcessor<ZeebeRecord>()
            .register(
                projector,
                List.of(
                    new TypeRoutingRollup<ProcessExecutionFact, ProcessInstanceExecutionTimeFact>(
                        ProcessInstanceExecutionTimeFact.class, regionRollup),
                    new TypeRoutingRollup<ProcessExecutionFact, ElementExecutionFact>(
                        ElementExecutionFact.class, heatmapRollup)));

    final ZeebeRecordConsumer source =
        ZeebeRecordConsumer.subscribe(client, group, instanceId, List.of(sourceTopic)).join();

    final WindowedAnalyticsPipeline pipeline =
        new WindowedAnalyticsPipeline(source, processor, store, sourceTopic);
    pipeline.start();
    LOG.info(
        "Analytics instance '{}' started: {} -> base projection -> durable region + heatmap rollups",
        instanceId,
        sourceTopic);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  pipeline.close();
                  try {
                    rollupState.close();
                    store.close();
                    client.close();
                  } catch (final Exception ignored) {
                    // shutting down
                  }
                }));
    Thread.currentThread().join();
  }
}
