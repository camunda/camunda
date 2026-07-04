/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.store.MetadataStore;
import io.camunda.eventbridge.analytics.store.AnalyticsBackend;
import io.camunda.eventbridge.analytics.store.AnalyticsBackends;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelopeCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 2 of the staged pipeline: consume the facts topic ({@link ShuffleEnvelope}s of sealed
 * segment deltas produced by Stage 1, partitioned by grouping key so each cell has a single owner),
 * dedup each batch, merge each cell delta once per {@code aggId} into the global windowed
 * aggregate, and converge the idempotent serving sink — so dashboard reads hit one cell (O(1)).
 *
 * <p>This class only <em>wires</em> the stage: it seeds the same active cubes as Stage 1 (identical
 * cube/agg ids) and hands one {@link CubeAggregationShard} per facts partition to a {@link
 * StreamRuntime}, which owns the poll loop, restore, and the commit barrier. Each shard owns a
 * per-facts-partition RocksDB and drives a one-node {@code ProcessorTopology} (the merge node); the
 * merged cells and the facts-topic offset commit as one atomic cut.
 */
public final class AnalyticsAggregationStage {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsAggregationStage.class);
  private static final int MAX_RECORDS = 5000;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration CHECKPOINT_INTERVAL =
      Duration.ofMillis(Long.getLong("analytics.checkpointIntervalMs", 1000L));
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

  private AnalyticsAggregationStage() {}

  public static void main(final String[] args) {
    final String group = System.getProperty("group", "analytics-stage2");
    final String gateway = System.getProperty("gateway", "http://localhost:8080");
    final String factsTopic = System.getProperty("factsTopic", "analytics-facts");
    final String instanceId =
        System.getProperty("instanceId", "stage2-" + ProcessHandle.current().pid());

    final EventBridgeClient client = EventBridgeClient.create(gateway);
    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    final AnalyticsBackend backend = AnalyticsBackends.fromSystemProperties();

    // Load the same specs Stage 1 bootstrapped from the metadata plane (migrate + bootstrap are
    // idempotent, so this is safe whichever stage starts first on a fresh database).
    final MetadataStore metadataStore = backend.metadataStore();
    metadataStore.migrate();
    AnalyticsCubes.bootstrap(metadataStore);
    final List<ActiveCube> cubes = AnalyticsCubes.loadCubes(metadataStore);
    final String stateDir = "data/analytics-stage2-" + instanceId;

    final StreamRuntime<ShuffleEnvelope> runtime =
        StreamRuntime.<ShuffleEnvelope>builder()
            .client(client)
            .group(group)
            .instanceId(instanceId)
            .sourceTopic(factsTopic)
            .deserializer((payload, partition, offset) -> ShuffleEnvelopeCodec.decode(payload))
            // One self-contained shard per facts partition: its own RocksDB cells + offset and the
            // per-aggId mergers, owning its durability (restore/commit).
            .taskFactory(
                partition ->
                    CubeAggregationShard.open(
                        partition, stateDir, backend.newDatasetStore(), cubes, meterRegistry))
            .maxPoll(MAX_RECORDS)
            .pollTimeout(POLL_TIMEOUT)
            .commitInterval(CHECKPOINT_INTERVAL)
            .errorBackoff(ERROR_BACKOFF)
            .build();

    LOG.info("Analytics Stage 2 '{}': {} -> mergers -> serving sink", instanceId, factsTopic);
    Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop, "stage2-shutdown"));
    runtime.run();
  }
}
