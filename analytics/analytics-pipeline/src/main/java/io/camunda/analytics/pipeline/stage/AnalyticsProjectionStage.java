/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.pipeline.store.AnalyticsBackend;
import io.camunda.analytics.pipeline.store.AnalyticsBackends;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordCodec;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 1 of the staged pipeline: consume {@code zeebe-records}, fold into the Model-A base
 * projection, and seal each active cube-meter's per-source-partition segment deltas into the
 * shuffle (the facts topic).
 *
 * <p>This class only <em>wires</em> the stage: it seeds the active cubes ({@link AnalyticsCubes})
 * and hands one {@link ProjectionStageTask} per source partition to a {@link StreamRuntime}, which
 * owns the poll loop, restore, and the produce-before-commit barrier. Each task owns a per-source-
 * partition RocksDB and drives a declared {@code ProcessorTopology} (base projection → per-cube
 * aggregate nodes → shuffle); its base projection, every open segment and the consumed offset
 * commit as one atomic cut (Model F), so a crash resumes exactly from the committed offset.
 */
public final class AnalyticsProjectionStage {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsProjectionStage.class);
  private static final int MAX_RECORDS = 5000;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration CHECKPOINT_INTERVAL =
      Duration.ofMillis(Long.getLong("analytics.checkpointIntervalMs", 1000L));
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);
  // How often, at a commit boundary, a task checks the dataset catalog for a live reload (ADR 0005:
  // must stay below the provisioning activation debounce so a new cube is picked up before it
  // fills).
  private static final long RELOAD_CHECK_INTERVAL_MS =
      Long.getLong("analytics.reloadCheckIntervalMs", 10_000L);

  private AnalyticsProjectionStage() {}

  public static void main(final String[] args) {
    final EventBridgeClient client = PipelineRuntimes.newClient();
    final ActorScheduler scheduler = PipelineRuntimes.startScheduler();
    final ExecutorService sinkExecutor = PipelineRuntimes.newSinkExecutor();
    final StreamRuntime<SourceRecord> runtime = buildRuntime(client, scheduler, sinkExecutor);
    PipelineRuntimes.run(
        client, scheduler, sinkExecutor, List.of(new PipelineRuntimes.Member("stage1", runtime)));
  }

  /**
   * Builds (but does not run) the Stage 1 runtime on the given shared client, actor scheduler, and
   * sink executor, so a single stage process or the consolidated launcher can run it. Reads its
   * configuration from system properties and provisions the facts topic and metadata plane.
   */
  public static StreamRuntime<SourceRecord> buildRuntime(
      final EventBridgeClient client,
      final ActorScheduler scheduler,
      final ExecutorService sinkExecutor) {
    final String group = System.getProperty("group", "analytics-stage1");
    final String sourceTopic = System.getProperty("sourceTopic", "zeebe-records");
    final String factsTopic = System.getProperty("factsTopic", "analytics-facts");
    final int factsPartitions = Integer.getInteger("factsPartitions", 1);
    final int segmentStride = Integer.getInteger("segmentStride", 1000);
    final int schemaVersion = Integer.getInteger("shuffleSchemaVersion", 1);
    final String instanceId =
        System.getProperty("instanceId", "stage1-" + ProcessHandle.current().pid());

    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    try {
      client.createTopic(factsTopic, factsPartitions, 1).join();
    } catch (final Exception existing) {
      LOG.info("Facts topic {} already exists", factsTopic);
    }

    // Projected (raw) datasets are written straight to the serving store from Stage 1.
    final AnalyticsBackend backend = AnalyticsBackends.fromSystemProperties();

    // The metadata plane is the source of truth: migrate the schema, bootstrap the standard
    // declarations once (idempotent), then load the specs the stage runs.
    final MetadataStore metadataStore = backend.metadataStore();
    metadataStore.migrate();
    AnalyticsCubes.bootstrap(metadataStore);
    // The versioned catalog is shared by this stage's partition tasks; each rebuilds its topology
    // from it at a commit boundary when its version moves (live reload — ADR 0005).
    final DatasetCatalog catalog = new DatasetCatalog(metadataStore);
    final String stateDir = "data/analytics-stage1-" + instanceId;
    final ZeebeRecordCodec codec = new ZeebeRecordCodec();

    final StreamRuntime<SourceRecord> runtime =
        StreamRuntime.<SourceRecord>builder()
            .client(client)
            .group(group)
            .instanceId(instanceId)
            .sourceTopic(sourceTopic)
            .deserializer(
                (payload, partition, offset) ->
                    new SourceRecord(
                        partition, offset, codec.deserialize(payload, partition, offset)))
            // event time = the Zeebe record timestamp, so the runtime advances stream time and
            // finalizes closed windows even for keys that stop receiving records.
            .timestampExtractor(sourceRecord -> sourceRecord.record().getTimestamp())
            // One self-contained task per source partition: its own RocksDB, projection, per-cube
            // sealing aggregations and publisher, owning its durability (restore/commit) — the
            // runtime dedups its resume gap and drives its per-partition atomic commit.
            .taskFactory(
                partition ->
                    ProjectionStageTask.open(
                        partition,
                        client,
                        stateDir,
                        factsTopic,
                        factsPartitions,
                        segmentStride,
                        schemaVersion,
                        catalog,
                        RELOAD_CHECK_INTERVAL_MS,
                        backend.newDatasetStore(),
                        meterRegistry))
            .maxPoll(MAX_RECORDS)
            .pollTimeout(POLL_TIMEOUT)
            .commitInterval(CHECKPOINT_INTERVAL)
            .errorBackoff(ERROR_BACKOFF)
            .actorScheduler(scheduler)
            .sinkExecutor(sinkExecutor)
            .build();

    LOG.info(
        "Analytics Stage 1 '{}': {} -> {} cube(s) + {} table(s) -> {}",
        instanceId,
        sourceTopic,
        catalog.cubes().size(),
        catalog.tables().size(),
        factsTopic);
    return runtime;
  }
}
