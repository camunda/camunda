/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores the partitions of one physical tenant from that tenant's backups.
 *
 * <p>Which partitions those are follows from the physical tenant's own configuration: its partition
 * count decides how many there are, and its id is the partition-group segment of the directory each
 * one is restored into ({@code <dataDirectory>/<physicalTenantId>/partitions/<n>}), so two tenants
 * restoring into the same data directory never collide.
 *
 * <p>Restoring a whole cluster — every physical tenant, plus the shared data directory and the
 * cluster configuration file that are not any one tenant's — is {@link ClusterRestore}'s job.
 */
@NullMarked
public class RestoreManager implements CloseableSilently {
  private static final Logger LOG = LoggerFactory.getLogger(RestoreManager.class);
  private final BrokerCfg configuration;
  private final String physicalTenantId;
  private final BrokerCfg physicalTenantConfiguration;
  private final Set<PartitionMetadata> partitions;
  private final BackupStore backupStore;
  private final MeterRegistry meterRegistry;
  private final ExecutorService executor;

  public RestoreManager(
      final BrokerCfg configuration,
      final String physicalTenantId,
      final BrokerCfg physicalTenantConfiguration,
      final Set<PartitionMetadata> partitions,
      final BackupStore backupStore,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.physicalTenantId = physicalTenantId;
    this.physicalTenantConfiguration = physicalTenantConfiguration;
    this.partitions = Set.copyOf(partitions);
    this.backupStore = backupStore;
    this.meterRegistry = meterRegistry;
    executor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("zeebe-restore-", 0).factory());
  }

  /**
   * Restores partitions from backups, where each partition may restore from different backup IDs.
   *
   * <p>This is useful when partitions have different safe start checkpoints based on their exported
   * positions, but all need to reach the same global checkpoint.
   *
   * <p>Neither the shared data directory nor the cluster configuration file is touched here: both
   * belong to the whole node rather than to this physical tenant, and {@link ClusterRestore} owns
   * them. Verifying the data directory per tenant would fail the second tenant on the first
   * tenant's freshly restored partitions, and emptying it on failure would discard them.
   *
   * @param backupIdsByPartition map from partition ID to the backup IDs to restore for that
   *     partition
   * @param validateConfig whether to validate the backup configuration
   */
  public void restore(final Map<Integer, long[]> backupIdsByPartition, final boolean validateConfig)
      throws IOException, ExecutionException, InterruptedException {
    final var partitionCount = backupIdsByPartition.size();
    final var partitionsToRestore = collectPartitions(partitionCount);
    final var tasks = new ArrayList<Callable<Void>>(partitionsToRestore.size());
    for (final var partition : partitionsToRestore) {
      final var partitionId = partition.partition().id().number();
      final var backupIds = backupIdsByPartition.get(partitionId);
      if (backupIds == null || backupIds.length == 0) {
        throw new IllegalArgumentException(
            "No backup IDs provided for partition %d of physical tenant '%s'"
                .formatted(partitionId, physicalTenantId));
      }
      tasks.add(
          () -> {
            restorePartition(partition, backupIds, partitionCount, validateConfig);
            return null;
          });
    }
    for (final var result : executor.invokeAll(tasks)) {
      result.get(); // throw exception if any of the tasks failed
    }
  }

  private void restorePartition(
      final InstrumentedRaftPartition partition,
      final long[] backupIds,
      final int partitionCount,
      final boolean validateConfig)
      throws IOException, FlushException {
    final BackupValidator validator;
    final RaftPartition raftPartition = partition.partition();

    if (validateConfig) {
      validator = new ValidatePartitionCount(partitionCount);
    } else {
      LOG.warn("Restoring without validating backup");
      validator = BackupValidator.none();
    }

    final var registry = partition.registry();
    final var restoreService =
        new PartitionRestoreService(
            backupStore,
            partition.partition(),
            configuration.getCluster().getNodeId(),
            new RocksDBSnapshotFileInfoProvider(),
            partition.registry());
    try {
      restoreService.restore(backupIds, validator);
      LOG.info(
          "Successfully restored partition {} from backups {}.",
          raftPartition.id().number(),
          backupIds);
    } finally {
      MicrometerUtil.close(registry);
    }
  }

  /** The local partitions up to {@code partitionCount}; the ones above it are not in the backup. */
  private Set<InstrumentedRaftPartition> collectPartitions(final int partitionCount) {
    final var raftPartitionFactory = new RaftPartitionFactory(physicalTenantConfiguration);
    return partitions.stream()
        .filter(metadata -> metadata.id().number() <= partitionCount)
        .map(metadata -> createRaftPartition(metadata, raftPartitionFactory))
        .collect(Collectors.toSet());
  }

  private InstrumentedRaftPartition createRaftPartition(
      final PartitionMetadata metadata, final RaftPartitionFactory factory) {
    final var partitionId = metadata.id();
    final var partitionRegistry =
        MicrometerUtil.wrap(meterRegistry, PartitionKeyNames.tags(partitionId));

    return new InstrumentedRaftPartition(
        factory.createRaftPartition(metadata, partitionRegistry), partitionRegistry);
  }

  @Override
  public void close() {
    try {
      backupStore.closeAsync().join();
      executor.shutdown();
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      // Not much we can do here, just report in case it's a bug in the shutdown process.
      LOG.warn("Interrupted while waiting for executor to shutdown", e);
      Thread.currentThread().interrupt();
    }
  }

  private record InstrumentedRaftPartition(
      RaftPartition partition, CompositeMeterRegistry registry) {}
}
