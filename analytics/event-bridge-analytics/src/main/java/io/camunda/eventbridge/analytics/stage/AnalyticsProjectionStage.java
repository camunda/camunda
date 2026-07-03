/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.streaming.StreamRuntime;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecordCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 1 of the staged pipeline: consume {@code zeebe-records}, fold once into the base projection
 * via the generic {@code AnalyticsFactProjector}, and seal each active cube-meter's per-source-
 * partition segment deltas into the shuffle (the facts topic).
 *
 * <p>This class only <em>wires</em> the stage: it seeds the active cubes ({@link AnalyticsCubes})
 * and hands one {@link CubeProjectionShard} per source partition to a {@link StreamRuntime}, which
 * owns the poll loop, restore, and the produce-before-commit barrier. One RocksDB holds the base
 * projection and the sealed segment cells; the shard publishes the sealed deltas before its
 * segment-safe offset advances, so a crash replays the open segments rather than losing them.
 */
public final class AnalyticsProjectionStage {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsProjectionStage.class);
  private static final int MAX_RECORDS = 5000;
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration CHECKPOINT_INTERVAL =
      Duration.ofMillis(Long.getLong("analytics.checkpointIntervalMs", 1000L));
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

  private AnalyticsProjectionStage() {}

  public static void main(final String[] args) {
    final String group = System.getProperty("group", "analytics-stage1");
    final String gateway = System.getProperty("gateway", "http://localhost:8080");
    final String sourceTopic = System.getProperty("sourceTopic", "zeebe-records");
    final String factsTopic = System.getProperty("factsTopic", "analytics-facts");
    final int factsPartitions = Integer.getInteger("factsPartitions", 1);
    final int segmentStride = Integer.getInteger("segmentStride", 1000);
    final int schemaVersion = Integer.getInteger("shuffleSchemaVersion", 1);
    final String instanceId =
        System.getProperty("instanceId", "stage1-" + ProcessHandle.current().pid());

    final EventBridgeClient client = EventBridgeClient.create(gateway);
    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    try {
      client.createTopic(factsTopic, factsPartitions, 1).join();
    } catch (final Exception existing) {
      LOG.info("Facts topic {} already exists", factsTopic);
    }

    final List<ActiveCube> cubes = AnalyticsCubes.seed();
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
            // One self-contained shard per source partition: its own RocksDB, projection, per-cube
            // sealing aggregations and publisher, owning its durability (restore/commit) — the
            // runtime dedups its resume gap and drives its per-partition atomic commit.
            .taskFactory(
                partition ->
                    CubeProjectionShard.open(
                        partition,
                        client,
                        stateDir,
                        factsTopic,
                        factsPartitions,
                        segmentStride,
                        schemaVersion,
                        cubes,
                        meterRegistry))
            .maxPoll(MAX_RECORDS)
            .pollTimeout(POLL_TIMEOUT)
            .commitInterval(CHECKPOINT_INTERVAL)
            .errorBackoff(ERROR_BACKOFF)
            .build();

    LOG.info(
        "Analytics Stage 1 '{}': {} -> {} cube(s) -> {}",
        instanceId,
        sourceTopic,
        cubes.size(),
        factsTopic);
    Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop, "stage1-shutdown"));
    runtime.run();
  }
}
