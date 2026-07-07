/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.eventbridge.streaming.state.rocksdb.StoreTuning;
import java.time.Duration;

/**
 * The single configuration surface of the staged pipeline: every system property the stage mains
 * read, with its default, in one place — instead of each stage re-reading the same properties with
 * duplicated defaults. Only the consumer-group and instance identity default per stage (via {@code
 * stage}); everything else is shared so both stages always agree on the topics and intervals that
 * connect them.
 *
 * @param stage the stage discriminator ({@code stage1}/{@code stage2}) the identity defaults and
 *     the state directory derive from
 * @param group the consumer group ({@code group}, default {@code analytics-<stage>})
 * @param instanceId this member's stable identity ({@code instanceId}, default {@code
 *     <stage>-<pid>})
 * @param sourceTopic the Zeebe-records topic Stage 1 consumes ({@code sourceTopic})
 * @param factsTopic the shuffle topic between the stages ({@code factsTopic})
 * @param factsPartitions the facts topic's partition count when Stage 1 provisions it ({@code
 *     factsPartitions})
 * @param segmentStride the shuffle segment stride in source offsets ({@code segmentStride})
 * @param shuffleSchemaVersion the shuffle envelope schema version Stage 1 stamps ({@code
 *     shuffleSchemaVersion})
 * @param checkpointInterval how often a task commits its atomic cut ({@code
 *     analytics.checkpointIntervalMs})
 * @param reloadCheckIntervalMs how often, at a commit boundary, a task checks the dataset catalog
 *     for a live reload ({@code analytics.reloadCheckIntervalMs} — ADR 0005: must stay below the
 *     provisioning activation debounce so a new cube is picked up before it fills)
 * @param stateConsistencyChecks whether the tasks' RocksDBs verify write preconditions and foreign
 *     keys ({@code analytics.state.consistencyChecks}, default {@code false}: the stores are
 *     disposable projections of the source log — a corrupt store is deleted and replayed, so the
 *     per-write cost buys nothing here)
 * @param stateDeleteAwareCompaction whether the tasks' RocksDBs periodically recompact old SST
 *     files to purge accumulated tombstones ({@code analytics.state.deleteAwareCompaction}, default
 *     {@code true}: scope clears make the stores tombstone-heavy)
 */
public record AnalyticsPipelineConfig(
    String stage,
    String group,
    String instanceId,
    String sourceTopic,
    String factsTopic,
    int factsPartitions,
    int segmentStride,
    int shuffleSchemaVersion,
    Duration checkpointInterval,
    long reloadCheckIntervalMs,
    boolean stateConsistencyChecks,
    boolean stateDeleteAwareCompaction) {

  /** Reads the configuration for one stage from system properties, with the shared defaults. */
  public static AnalyticsPipelineConfig fromSystemProperties(final String stage) {
    return new AnalyticsPipelineConfig(
        stage,
        System.getProperty("group", "analytics-" + stage),
        System.getProperty("instanceId", stage + "-" + ProcessHandle.current().pid()),
        System.getProperty("sourceTopic", "zeebe-records"),
        System.getProperty("factsTopic", "analytics-facts"),
        Integer.getInteger("factsPartitions", 1),
        Integer.getInteger("segmentStride", 1000),
        Integer.getInteger("shuffleSchemaVersion", 1),
        Duration.ofMillis(Long.getLong("analytics.checkpointIntervalMs", 1000L)),
        Long.getLong("analytics.reloadCheckIntervalMs", 10_000L),
        Boolean.parseBoolean(System.getProperty("analytics.state.consistencyChecks", "false")),
        Boolean.parseBoolean(System.getProperty("analytics.state.deleteAwareCompaction", "true")));
  }

  /** The per-instance state directory this stage's RocksDBs live under. */
  public String stateDir() {
    return "data/analytics-" + stage + "-" + instanceId;
  }

  /** The RocksDB tuning the analytics tasks open their state stores with. */
  public StoreTuning storeTuning() {
    return new StoreTuning(stateConsistencyChecks, stateDeleteAwareCompaction);
  }
}
