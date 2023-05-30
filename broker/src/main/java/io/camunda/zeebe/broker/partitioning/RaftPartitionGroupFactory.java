/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.raft.partition.PartitionDistributor;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.raft.partition.RaftPartitionGroup.Builder;
import io.atomix.raft.partition.RoundRobinPartitionDistributor;
import io.camunda.zeebe.broker.partitioning.distribution.FixedPartitionDistributor;
import io.camunda.zeebe.broker.partitioning.distribution.FixedPartitionDistributorBuilder;
import io.camunda.zeebe.broker.raft.ZeebeEntryValidator;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.NetworkCfg;
import io.camunda.zeebe.broker.system.configuration.PartitioningCfg;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class RaftPartitionGroupFactory {

  public RaftPartitionGroup buildRaftPartitionGroup(
      final BrokerCfg configuration, final ReceivableSnapshotStoreFactory snapshotStoreFactory) {

    final DataCfg dataConfiguration = configuration.getData();
    final String rootDirectory = dataConfiguration.getDirectory();
    final var rootPath = Paths.get(rootDirectory);
    try {
      FileUtil.ensureDirectoryExists(rootPath);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    final var raftDataDirectory = rootPath.resolve(PartitionManagerImpl.GROUP_NAME);

    try {
      FileUtil.ensureDirectoryExists(raftDataDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create Raft data directory", e);
    }

    final ClusterCfg clusterCfg = configuration.getCluster();
    final var experimentalCfg = configuration.getExperimental();
    final DataCfg dataCfg = configuration.getData();
    final NetworkCfg networkCfg = configuration.getNetwork();

    final var partitionDistributor =
        buildPartitionDistributor(configuration.getExperimental().getPartitioning());
    final Builder partitionGroupBuilder =
        RaftPartitionGroup.builder(PartitionManagerImpl.GROUP_NAME)
            .withNumPartitions(clusterCfg.getPartitionsCount())
            .withPartitionSize(clusterCfg.getReplicationFactor())
            .withMembers(getRaftGroupMembers(clusterCfg))
            .withDataDirectory(raftDataDirectory.toFile())
            .withSnapshotStoreFactory(snapshotStoreFactory)
            .withMaxAppendBatchSize((int) experimentalCfg.getMaxAppendBatchSizeInBytes())
            .withMaxAppendsPerFollower(experimentalCfg.getMaxAppendsPerFollower())
            .withEntryValidator(new ZeebeEntryValidator())
            .withFlushExplicitly(!experimentalCfg.isDisableExplicitRaftFlush())
            .withFreeDiskSpace(dataCfg.getFreeDiskSpaceReplicationWatermark())
            .withJournalIndexDensity(dataCfg.getLogIndexDensity())
            .withPriorityElection(clusterCfg.getRaft().isEnablePriorityElection())
            .withPartitionDistributor(partitionDistributor)
            .withElectionTimeout(clusterCfg.getElectionTimeout())
            .withHeartbeatInterval(clusterCfg.getHeartbeatInterval())
            .withRequestTimeout(experimentalCfg.getRaft().getRequestTimeout())
            .withSnapshotRequestTimeout(experimentalCfg.getRaft().getSnapshotRequestTimeout())
            .withMaxQuorumResponseTimeout(experimentalCfg.getRaft().getMaxQuorumResponseTimeout())
            .withMinStepDownFailureCount(experimentalCfg.getRaft().getMinStepDownFailureCount())
            .withPreferSnapshotReplicationThreshold(
                experimentalCfg.getRaft().getPreferSnapshotReplicationThreshold())
            .withPreallocateSegmentFiles(experimentalCfg.getRaft().isPreallocateSegmentFiles());

    final int maxMessageSize = (int) networkCfg.getMaxMessageSizeInBytes();

    final var segmentSize = dataCfg.getLogSegmentSizeInBytes();
    if (segmentSize < maxMessageSize) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the raft segment size greater than the max message size of %s, but was %s.",
              maxMessageSize, segmentSize));
    }

    partitionGroupBuilder.withSegmentSize(segmentSize);

    return partitionGroupBuilder.build();
  }

  private List<String> getRaftGroupMembers(final ClusterCfg clusterCfg) {
    final int clusterSize = clusterCfg.getClusterSize();
    // node ids are always 0 to clusterSize - 1
    final List<String> members = new ArrayList<>();
    for (int i = 0; i < clusterSize; i++) {
      members.add(Integer.toString(i));
    }
    return members;
  }

  private PartitionDistributor buildPartitionDistributor(final PartitioningCfg config) {
    switch (config.getScheme()) {
      case FIXED:
        return buildFixedPartitionDistributor(config);
      case ROUND_ROBIN:
      default:
        return new RoundRobinPartitionDistributor();
    }
  }

  private FixedPartitionDistributor buildFixedPartitionDistributor(final PartitioningCfg config) {
    final var distributionBuilder =
        new FixedPartitionDistributorBuilder(PartitionManagerImpl.GROUP_NAME);

    for (final var partition : config.getFixed()) {
      for (final var node : partition.getNodes()) {
        distributionBuilder.assignMember(
            partition.getPartitionId(), node.getNodeId(), node.getPriority());
      }
    }

    return distributionBuilder.build();
  }
}
