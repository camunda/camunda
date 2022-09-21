/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.restore;

import io.atomix.cluster.MemberId;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.partitioning.RaftPartitionGroupFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreManager {
  private static final Logger LOG = LoggerFactory.getLogger(RestoreManager.class);
  private final BrokerCfg configuration;
  private final BackupStore backupStore;

  public RestoreManager(final BrokerCfg configuration, final BackupStore backupStore) {
    this.configuration = configuration;
    this.backupStore = backupStore;
  }

  public CompletableFuture<Void> restore(final long backupId) {
    final var brokerIds =
        IntStream.range(0, configuration.getCluster().getClusterSize())
            .boxed()
            .collect(Collectors.toSet());
    final var partitionToRestore = collectPartitions();
    final var localBrokerId = configuration.getCluster().getNodeId();

    final var partitionIds = partitionToRestore.stream().map(p -> p.id().id()).toList();
    LOG.info("Restoring partitions {}", partitionIds);

    return CompletableFuture.allOf(
        partitionToRestore.stream()
            .map(partition -> restorePartition(partition, backupId, brokerIds, localBrokerId))
            .toArray(CompletableFuture[]::new));
  }

  private void logSuccessfulRestore(
      final BackupDescriptor backup, final int partitionId, final long backupId) {
    LOG.info(
        "Successfully restored partition {} from backup {}. Backup description: {}",
        partitionId,
        backupId,
        backup);
  }

  private CompletableFuture<Void> restorePartition(
      final RaftPartition partition,
      final long backupId,
      final Set<Integer> brokerIds,
      final int localBrokerId) {
    return new PartitionRestoreService(backupStore, partition, brokerIds, localBrokerId)
        .restore(backupId)
        .thenApply(
            backup -> {
              logSuccessfulRestore(backup, partition.id().id(), backupId);
              return null;
            });
  }

  private Set<RaftPartition> collectPartitions() {

    final var factory = new RaftPartitionGroupFactory();
    // snapshot store factory can be null because we are not going start the partitions.
    final var partitionsGroup = factory.buildRaftPartitionGroup(configuration, null);

    final var localBrokerId = configuration.getCluster().getNodeId();
    final var localMember = MemberId.from(String.valueOf(localBrokerId));
    return partitionsGroup.getPartitions().stream()
        .map(RaftPartition.class::cast)
        .filter(partition -> partition.getMetadata().members().contains(localMember))
        .collect(Collectors.toSet());
  }
}
