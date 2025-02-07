/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.RaftStorageConfig;
import io.atomix.raft.storage.log.DelayedFlusher;
import io.atomix.raft.storage.log.RaftLogFlusher;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.raft.ZeebeEntryValidator;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.FlushConfig;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public final class RaftPartitionFactory {
  public static final String GROUP_NAME = "raft-partition";

  private final BrokerCfg brokerCfg;
  private final MeterRegistry meterRegistry;

  public RaftPartitionFactory(final BrokerCfg brokerCfg, final MeterRegistry meterRegistry) {
    this.brokerCfg = brokerCfg;
    this.meterRegistry = meterRegistry;
  }

  public RaftPartition createRaftPartition(final PartitionMetadata partitionMetadata) {
    final var partitionDirectory =
        Paths.get(brokerCfg.getData().getDirectory())
            .resolve(GROUP_NAME)
            .resolve("partitions")
            .resolve(partitionMetadata.id().id().toString());
    try {
      FileUtil.ensureDirectoryExists(partitionDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return createRaftPartition(partitionMetadata, partitionDirectory);
  }

  public RaftPartition createRaftPartition(
      final PartitionMetadata partitionMetadata, final Path partitionDirectory) {
    final var storageConfig = new RaftStorageConfig();
    final var partitionConfig = new RaftPartitionConfig();

    final var maxMessageSize = brokerCfg.getNetwork().getMaxMessageSizeInBytes();
    final var segmentSize = brokerCfg.getData().getLogSegmentSizeInBytes();
    if (segmentSize < maxMessageSize) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the raft segment size greater than the max message size of %s, but was %s.",
              maxMessageSize, segmentSize));
    }
    storageConfig.setSegmentSize(segmentSize);

    storageConfig.setFlusherFactory(
        createFlusherFactory(
            brokerCfg.getCluster().getRaft().getFlush(), brokerCfg.getExperimental()));
    storageConfig.setFreeDiskSpace(
        brokerCfg.getData().getDisk().getFreeSpace().getReplication().toBytes());
    storageConfig.setJournalIndexDensity(brokerCfg.getData().getLogIndexDensity());
    storageConfig.setPreallocateSegmentFiles(
        brokerCfg.getExperimental().getRaft().isPreallocateSegmentFiles());

    partitionConfig.setStorageConfig(storageConfig);
    partitionConfig.setEntryValidator(new ZeebeEntryValidator());
    partitionConfig.setMaxAppendBatchSize(
        (int) brokerCfg.getExperimental().getMaxAppendBatchSizeInBytes());
    partitionConfig.setMaxAppendsPerFollower(
        brokerCfg.getExperimental().getMaxAppendsPerFollower());
    partitionConfig.setPriorityElectionEnabled(
        brokerCfg.getCluster().getRaft().isEnablePriorityElection());
    partitionConfig.setElectionTimeout(brokerCfg.getCluster().getElectionTimeout());
    partitionConfig.setHeartbeatInterval(brokerCfg.getCluster().getHeartbeatInterval());
    partitionConfig.setRequestTimeout(brokerCfg.getExperimental().getRaft().getRequestTimeout());
    partitionConfig.setSnapshotRequestTimeout(
        brokerCfg.getExperimental().getRaft().getSnapshotRequestTimeout());
    partitionConfig.setConfigurationChangeTimeout(
        brokerCfg.getExperimental().getRaft().getConfigurationChangeTimeout());
    partitionConfig.setMaxQuorumResponseTimeout(
        brokerCfg.getExperimental().getRaft().getMaxQuorumResponseTimeout());
    partitionConfig.setMinStepDownFailureCount(
        brokerCfg.getExperimental().getRaft().getMinStepDownFailureCount());
    partitionConfig.setPreferSnapshotReplicationThreshold(
        brokerCfg.getExperimental().getRaft().getPreferSnapshotReplicationThreshold());

    return new RaftPartition(
        partitionMetadata, partitionConfig, partitionDirectory.toFile(), meterRegistry);
  }

  private RaftLogFlusher.Factory createFlusherFactory(
      final FlushConfig config, final ExperimentalCfg experimental) {
    // for backwards compatibility; remove this and flatten when this is removed
    if (experimental.isDisableExplicitRaftFlush()) {
      return createFlusherFactory(new FlushConfig(false, Duration.ZERO));
    }

    return createFlusherFactory(config);
  }

  private RaftLogFlusher.Factory createFlusherFactory(final FlushConfig config) {
    if (config.enabled()) {
      final Duration delayTime = config.delayTime();
      if (delayTime.isZero()) {
        return RaftLogFlusher.Factory::direct;
      }

      return threadFactory -> new DelayedFlusher(threadFactory.createContext(), delayTime);
    }

    Loggers.RAFT.warn(
        """
          Explicit Raft flush is disabled. Data will be flushed to disk only before a snapshot is
          taken. This is generally unsafe and could lead to data loss or corruption. Make sure to
          read the documentation regarding this feature.""");

    return RaftLogFlusher.Factory::noop;
  }
}
