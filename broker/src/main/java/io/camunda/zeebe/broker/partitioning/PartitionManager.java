/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.raft.partition.RaftPartitionGroup.Builder;
import io.atomix.utils.concurrent.Futures;
import io.camunda.zeebe.broker.clustering.atomix.AtomixFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.NetworkCfg;
import io.camunda.zeebe.logstreams.impl.log.ZeebeEntryValidator;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.agrona.IoUtil;

public class PartitionManager {
  protected volatile CompletableFuture<Void> closeFuture;
  private final ManagedPartitionService partitions;
  private final ManagedPartitionGroup partitionGroup;

  protected PartitionManager(
      final BrokerCfg configuration,
      final ReceivableSnapshotStoreFactory snapshotStoreFactory,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService) {

    partitionGroup = buildRaftPartitionGroup(configuration, snapshotStoreFactory);

    partitions = buildPartitionService(membershipService, communicationService);
  }

  // TODO remove static modifier
  public static RaftPartitionGroup buildRaftPartitionGroup(
      final BrokerCfg configuration, final ReceivableSnapshotStoreFactory snapshotStoreFactory) {

    final DataCfg dataConfiguration = configuration.getData();
    final String rootDirectory = dataConfiguration.getDirectory();
    try {
      FileUtil.ensureDirectoryExists(new File(rootDirectory).toPath());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    final File raftDirectory = new File(rootDirectory, AtomixFactory.GROUP_NAME);
    IoUtil.ensureDirectoryExists(raftDirectory, "Raft data directory");

    final ClusterCfg clusterCfg = configuration.getCluster();
    final var experimentalCfg = configuration.getExperimental();
    final DataCfg dataCfg = configuration.getData();
    final NetworkCfg networkCfg = configuration.getNetwork();

    final Builder partitionGroupBuilder =
        RaftPartitionGroup.builder(AtomixFactory.GROUP_NAME)
            .withNumPartitions(clusterCfg.getPartitionsCount())
            .withPartitionSize(clusterCfg.getReplicationFactor())
            .withMembers(getRaftGroupMembers(clusterCfg))
            .withDataDirectory(raftDirectory)
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

  public synchronized CompletableFuture<Void> start() {
    if (closeFuture != null) {
      return Futures.exceptionalFuture(
          new IllegalStateException(
              "Atomix instance " + (closeFuture.isDone() ? "shutdown" : "shutting down")));
    }

    return partitions.start().thenApply(ps -> null);
  }

  public synchronized CompletableFuture<Void> stop() {
    if (closeFuture == null) {
      closeFuture = partitions.stop().thenApply(ps -> null);
    }

    return closeFuture;
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("partitionGroup", partitionGroup).toString();
  }

  /** Builds a partition service. */
  private ManagedPartitionService buildPartitionService(
      final ClusterMembershipService clusterMembershipService,
      final ClusterCommunicationService messagingService) {

    return new DefaultPartitionService(clusterMembershipService, messagingService, partitionGroup);
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
