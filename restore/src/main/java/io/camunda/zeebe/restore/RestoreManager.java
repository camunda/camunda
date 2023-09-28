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
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistributionResolver;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
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
    final Path dataDirectory = Path.of(configuration.getData().getDirectory());
    try {
      if (!FileUtil.isEmpty(dataDirectory)) {
        LOG.error(
            "Brokers's data directory {} is not empty. Aborting restore to avoid overwriting data. Please restart with a clean directory.",
            dataDirectory);
        return CompletableFuture.failedFuture(
            new DirectoryNotEmptyException(dataDirectory.toString()));
      }
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }

    final var partitionToRestore = collectPartitions();

    final var partitionIds = partitionToRestore.stream().map(p -> p.id().id()).toList();
    LOG.info("Restoring partitions {}", partitionIds);

    return CompletableFuture.allOf(
            partitionToRestore.stream()
                .map(partition -> restorePartition(partition, backupId))
                .toArray(CompletableFuture[]::new))
        .exceptionallyComposeAsync(error -> logFailureAndDeleteDataDirectory(dataDirectory, error));
  }

  private CompletableFuture<Void> logFailureAndDeleteDataDirectory(
      final Path dataDirectory, final Throwable error) {
    LOG.error("Failed to restore broker. Deleting data directory {}", dataDirectory, error);
    try {
      FileUtil.deleteFolderContents(dataDirectory);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
    // Must fail because restore failed
    return CompletableFuture.failedFuture(error);
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
      final RaftPartition partition, final long backupId) {
    return new PartitionRestoreService(backupStore, partition)
        .restore(backupId)
        .thenAccept(backup -> logSuccessfulRestore(backup, partition.id().id(), backupId));
  }

  private Set<RaftPartition> collectPartitions() {
    final var localBrokerId = configuration.getCluster().getNodeId();
    final var localMember = MemberId.from(String.valueOf(localBrokerId));
    final var clusterTopology =
        new PartitionDistribution(
            PartitionDistributionResolver.getStaticConfiguration(
                    configuration.getCluster(),
                    configuration.getExperimental().getPartitioning(),
                    localMember)
                .generatePartitionDistribution());
    final var raftPartitionFactory = new RaftPartitionFactory(configuration);

    return clusterTopology.partitions().stream()
        .filter(partitionMetadata -> partitionMetadata.members().contains(localMember))
        .map(raftPartitionFactory::createRaftPartition)
        .collect(Collectors.toSet());
  }
}
