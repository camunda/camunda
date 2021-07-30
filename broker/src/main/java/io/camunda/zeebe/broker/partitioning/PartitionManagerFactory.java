/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.raft.partition.RaftPartitionGroup.Builder;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.NetworkCfg;
import io.camunda.zeebe.logstreams.impl.log.ZeebeEntryValidator;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PartitionManagerFactory {

  public static final String GROUP_NAME = "raft-partition";

  public static PartitionManagerActor fromBrokerConfiguration(
      final BrokerCfg configuration,
      final ClusterServices clusterServices,
      final ReceivableSnapshotStoreFactory snapshotStoreFactory) {

    final var partitionGroup = buildRaftPartitionGroup(configuration, snapshotStoreFactory);

    return new PartitionManagerActor(
        partitionGroup,
        clusterServices.getMembershipService(),
        clusterServices.getCommunicationService());
  }

  public static RaftPartitionGroup buildRaftPartitionGroup(
      final BrokerCfg configuration, final ReceivableSnapshotStoreFactory snapshotStoreFactory) {

    final DataCfg dataConfiguration = configuration.getData();
    final String rootDirectory = dataConfiguration.getDirectory();
    final var rootPath = Paths.get(rootDirectory);
    try {
      FileUtil.ensureDirectoryExists(rootPath);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    final var raftDataDirectory = rootPath.resolve(GROUP_NAME);

    try {
      FileUtil.ensureDirectoryExists(raftDataDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create Raft data directory", e);
    }

    final ClusterCfg clusterCfg = configuration.getCluster();
    final var experimentalCfg = configuration.getExperimental();
    final DataCfg dataCfg = configuration.getData();
    final NetworkCfg networkCfg = configuration.getNetwork();

    final Builder partitionGroupBuilder =
        RaftPartitionGroup.builder(GROUP_NAME)
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
            .withPriorityElection(experimentalCfg.isEnablePriorityElection());

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

  private static List<String> getRaftGroupMembers(final ClusterCfg clusterCfg) {
    final int clusterSize = clusterCfg.getClusterSize();
    // node ids are always 0 to clusterSize - 1
    final List<String> members = new ArrayList<>();
    for (int i = 0; i < clusterSize; i++) {
      members.add(Integer.toString(i));
    }
    return members;
  }
}
