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
import io.camunda.analytics.serving.catalog.DatasetCatalog;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelopeCodec;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 2 of the staged pipeline: consume the facts topic ({@link ShuffleEnvelope}s of sealed
 * segment deltas produced by Stage 1, partitioned by grouping key so each cell has a single owner),
 * dedup each batch, merge each cell delta once per {@code aggId} into the global windowed
 * aggregate, and converge the idempotent serving sink — so dashboard reads hit one cell (O(1)).
 *
 * <p>This class only <em>wires</em> the stage: it seeds the same active cubes as Stage 1 (identical
 * cube/agg ids) and hands one {@link AggregationStageTask} per facts partition to a {@link
 * StreamRuntime}, which owns the poll loop, restore, and the commit barrier. Each task owns a
 * per-facts-partition RocksDB and drives a one-node {@code ProcessorTopology} (the merge node); the
 * merged cells and the facts-topic offset commit as one atomic cut.
 */
public final class AnalyticsAggregationStage {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsAggregationStage.class);
  private static final int MAX_RECORDS = 5000;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

  private AnalyticsAggregationStage() {}

  public static void main(final String[] args) {
    final EventBridgeClient client = PipelineRuntimes.newClient();
    final ActorScheduler scheduler = PipelineRuntimes.startScheduler();
    final ExecutorService sinkExecutor = PipelineRuntimes.newSinkExecutor();
    final StreamRuntime<ShuffleEnvelope> runtime = buildRuntime(client, scheduler, sinkExecutor);
    PipelineRuntimes.run(
        client, scheduler, sinkExecutor, List.of(new PipelineRuntimes.Member("stage2", runtime)));
  }

  /**
   * Builds (but does not run) the Stage 2 runtime on the given shared client, actor scheduler, and
   * sink executor, so a single stage process or the consolidated launcher can run it. Reads its
   * configuration from system properties and provisions the metadata plane.
   */
  public static StreamRuntime<ShuffleEnvelope> buildRuntime(
      final EventBridgeClient client,
      final ActorScheduler scheduler,
      final ExecutorService sinkExecutor) {
    final AnalyticsPipelineConfig config = AnalyticsPipelineConfig.fromSystemProperties("stage2");

    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    final AnalyticsBackend backend = AnalyticsBackends.fromSystemProperties();

    // Load the same specs Stage 1 bootstrapped from the metadata plane (migrate + bootstrap are
    // idempotent, so this is safe whichever stage starts first on a fresh database).
    final MetadataStore metadataStore = backend.metadataStore();
    metadataStore.migrate();
    AnalyticsCubes.bootstrap(metadataStore);
    // The versioned catalog is shared by this stage's partition tasks; each rebuilds its merge
    // topology from it at a commit boundary when its version moves (live reload — ADR 0005).
    final DatasetCatalog catalog = new DatasetCatalog(metadataStore);

    final StreamRuntime<ShuffleEnvelope> runtime =
        StreamRuntime.<ShuffleEnvelope>builder()
            .client(client)
            .group(config.group())
            .instanceId(config.instanceId())
            .sourceTopic(config.factsTopic())
            .deserializer((payload, partition, offset) -> ShuffleEnvelopeCodec.decode(payload))
            // One self-contained task per facts partition: its own RocksDB cells + offset and the
            // per-aggId mergers, owning its durability (restore/commit).
            .taskFactory(
                partition ->
                    AggregationStageTask.open(
                        partition,
                        config.stateDir(),
                        backend.newDatasetStore(),
                        catalog,
                        config.reloadCheckIntervalMs(),
                        meterRegistry,
                        config.storeTuning()))
            .maxPoll(MAX_RECORDS)
            .pollTimeout(POLL_TIMEOUT)
            .commitInterval(config.checkpointInterval())
            .errorBackoff(ERROR_BACKOFF)
            .actorScheduler(scheduler)
            .sinkExecutor(sinkExecutor)
            .build();

    LOG.info(
        "Analytics Stage 2 '{}': {} -> mergers -> serving sink",
        config.instanceId(),
        config.factsTopic());
    return runtime;
  }
}
