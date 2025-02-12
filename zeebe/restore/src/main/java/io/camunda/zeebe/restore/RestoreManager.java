/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.restore;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistributionResolver;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.impl.rocksdb.ChecksumProviderRocksDBImpl;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
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
  private final MeterRegistry meterRegistry;

  public RestoreManager(
      final BrokerCfg configuration,
      final BackupStore backupStore,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.backupStore = backupStore;
    this.meterRegistry = meterRegistry;
  }

  public CompletableFuture<Void> restore(final long backupId, final boolean validateConfig) {
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

    final var partitionIds = partitionToRestore.stream().map(p -> p.partition().id().id()).toList();
    LOG.info("Restoring partitions {}", partitionIds);

    return CompletableFuture.allOf(
            partitionToRestore.stream()
                .map(partition -> restorePartition(partition, backupId, validateConfig))
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
      final InstrumentedRaftPartition partition,
      final long backupId,
      final boolean validateConfig) {
    final BackupValidator validator;
    final RaftPartition raftPartition = partition.partition();

    if (validateConfig) {
      validator = new ValidatePartitionCount(configuration.getCluster().getPartitionsCount());
    } else {
      LOG.warn("Restoring without validating backup");
      validator = BackupValidator.none();
    }

    final var registry = partition.registry();
    return new PartitionRestoreService(
            backupStore, raftPartition, new ChecksumProviderRocksDBImpl(), partition.registry())
        .restore(backupId, validator)
        .thenAccept(backup -> logSuccessfulRestore(backup, raftPartition.id().id(), backupId))
        .whenComplete((ok, error) -> MicrometerUtil.discard(registry));
  }

  private Set<InstrumentedRaftPartition> collectPartitions() {
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
        .map(metadata -> createRaftPartition(metadata, raftPartitionFactory))
        .collect(Collectors.toSet());
  }

  private InstrumentedRaftPartition createRaftPartition(
      final PartitionMetadata metadata, final RaftPartitionFactory factory) {
    final var partitionRegistry = new CompositeMeterRegistry();
    final var partitionId = metadata.id().id().toString();
    partitionRegistry.config().commonTags(PartitionKeyNames.PARTITION.asString(), partitionId);
    partitionRegistry.add(meterRegistry);

    return new InstrumentedRaftPartition(
        factory.createRaftPartition(metadata, partitionRegistry), partitionRegistry);
  }

  static final class ValidatePartitionCount implements BackupValidator {
    private final int expectedPartitionCount;

    ValidatePartitionCount(final int expectedPartitionCount) {
      this.expectedPartitionCount = expectedPartitionCount;
    }

    @Override
    public BackupStatus validateStatus(final BackupStatus status) throws BackupNotValidException {
      final var descriptor =
          status
              .descriptor()
              .orElseThrow(
                  () -> new BackupNotValidException(status, "Backup does not have a descriptor"));
      if (descriptor.numberOfPartitions() != expectedPartitionCount) {
        throw new BackupNotValidException(
            status,
            "Expected backup to have %d partitions, but has %d"
                .formatted(expectedPartitionCount, descriptor.numberOfPartitions()));
      }
      return status;
    }
  }

  private record InstrumentedRaftPartition(
      RaftPartition partition, CompositeMeterRegistry registry) {}
}
