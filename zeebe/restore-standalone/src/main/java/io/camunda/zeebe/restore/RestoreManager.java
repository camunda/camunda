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
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
  private final BackupMetadataSyncer metadataSyncer;
  private final MeterRegistry meterRegistry;
  @Nullable private final ExporterPositionMapper exporterPositionMapper;
  private final ExecutorService executor;

  /** Restores the default physical tenant, configured by {@code configuration} itself. */
  @VisibleForTesting
  RestoreManager(
      final BrokerCfg configuration,
      final BackupStore backupStore,
      final MeterRegistry meterRegistry) {
    this(
        configuration,
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
        configuration,
        ClusterRestore.localPartitionsOf(
            configuration,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, configuration),
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID),
        backupStore,
        null,
        meterRegistry);
  }

  public RestoreManager(
      final BrokerCfg configuration,
      final String physicalTenantId,
      final BrokerCfg physicalTenantConfiguration,
      final Set<PartitionMetadata> partitions,
      final BackupStore backupStore,
      @Nullable final ExporterPositionMapper exporterPositionMapper,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.physicalTenantId = physicalTenantId;
    this.physicalTenantConfiguration = physicalTenantConfiguration;
    this.partitions = Set.copyOf(partitions);
    this.backupStore = backupStore;
    metadataSyncer = new BackupMetadataSyncer(backupStore, meterRegistry);
    this.exporterPositionMapper = exporterPositionMapper;
    this.meterRegistry = meterRegistry;
    executor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("zeebe-restore-", 0).factory());
  }

  public void restore(final long backupId, final boolean validateConfig)
      throws IOException, ExecutionException, InterruptedException {
    restore(new long[] {backupId}, validateConfig);
  }

  public void restore(final RestoreSelection selection, final boolean validateConfig)
      throws IOException, ExecutionException, InterruptedException {
    if (selection.hasBackupIds()) {
      restore(selection.backupIdsAsArray(), validateConfig);
    } else {
      restore(selection.from(), selection.to(), validateConfig);
    }
  }

  public void restore(
      @Nullable final Instant from, @Nullable final Instant to, final boolean validateConfig)
      throws IOException, ExecutionException, InterruptedException {
    if (exporterPositionMapper == null) {
      if (from == null) {
        throw new IllegalArgumentException(
            "Expected `from` to not be null, but got <null>. When the restore is not using a RDBMS as secondary storage, `from` parameter is required");
      }
      restoreTimeRange(from, to, validateConfig);
    } else {
      restoreRdbms(exporterPositionMapper, from, to, validateConfig);
    }
  }

  private void restoreRdbms(
      final ExporterPositionMapper positionMapper,
      @Nullable final Instant from,
      @Nullable final Instant to,
      final boolean validateConfig)
      throws IOException, ExecutionException, InterruptedException {
    final var partitionCount = physicalTenantConfiguration.getCluster().getPartitionsCount();

    final var exportedPositions = exportedPositions(positionMapper, partitionCount).join();
    LOG.info("Exported positions for all partitions: {}", exportedPositions);

    // Load backup metadata for each partition in parallel
    final var metadataByPartition = loadMetadataForAllPartitions(partitionCount).join();

    final var restorableBackups =
        RestorePointResolver.resolve(metadataByPartition, from, to, exportedPositions);

    // Convert List<CheckpointEntry> to long[] of checkpoint IDs per partition
    final var backupIdsByPartition =
        restorableBackups.backupsByPartitionId().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    e ->
                        e.getValue().stream()
                            .mapToLong(BackupMetadata.CheckpointEntry::checkpointId)
                            .toArray()));

    LOG.info(
        "Restoring RDBMS backups in range [{},{}] to global checkpoint {}: {}",
        from,
        to,
        restorableBackups.globalCheckpointId(),
        backupIdsByPartition);

    restore(backupIdsByPartition, validateConfig);
  }

  public void restoreTimeRange(
      final @Nullable Instant from, final @Nullable Instant to, final boolean validateConfig)
      throws IOException, ExecutionException, InterruptedException {
    final var partitionCount = physicalTenantConfiguration.getCluster().getPartitionsCount();

    // Load backup metadata for each partition in parallel
    final var metadataByPartition = loadMetadataForAllPartitions(partitionCount).join();

    final var restorableBackups = RestorePointResolver.resolve(metadataByPartition, from, to, null);

    // Convert List<CheckpointEntry> to long[] of checkpoint IDs per partition
    final var backupIdsByPartition =
        restorableBackups.backupsByPartitionId().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    e ->
                        e.getValue().stream()
                            .mapToLong(BackupMetadata.CheckpointEntry::checkpointId)
                            .toArray()));

    LOG.info(
        "Restoring time-range backups in range [{},{}] to global checkpoint {}: {}",
        from,
        to,
        restorableBackups.globalCheckpointId(),
        backupIdsByPartition);

    restore(backupIdsByPartition, validateConfig);
  }

  public void restore(final long[] backupIds, final boolean validateConfig)
      throws IOException, ExecutionException, InterruptedException {
    restore(toBackupIdsByPartition(backupIds), validateConfig);
  }

  /**
   * Converts a common array of backup IDs to a map where each partition uses the same backup IDs.
   *
   * @param backupIds the backup IDs to use for all partitions
   * @return a map from partition ID to backup IDs
   */
  private Map<Integer, long[]> toBackupIdsByPartition(final long[] backupIds) {
    final var partitionCount = physicalTenantConfiguration.getCluster().getPartitionsCount();
    return IntStream.rangeClosed(1, partitionCount)
        .boxed()
        .collect(Collectors.toMap(partition -> partition, partition -> backupIds));
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
    final var partitionsToRestore = collectPartitions();
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
            restorePartition(partition, backupIds, validateConfig);
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
      final boolean validateConfig)
      throws IOException, FlushException {
    final BackupValidator validator;
    final RaftPartition raftPartition = partition.partition();

    if (validateConfig) {
      validator =
          new ValidatePartitionCount(physicalTenantConfiguration.getCluster().getPartitionsCount());
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

  private Set<InstrumentedRaftPartition> collectPartitions() {
    final var raftPartitionFactory = new RaftPartitionFactory(configuration);
    return partitions.stream()
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

  private CompletableFuture<Map<Integer, Long>> exportedPositions(
      final ExporterPositionMapper positionMapper, final int partitionCount) {
    return FuturesUtil.parTraverse(
            IntStream.rangeClosed(1, partitionCount).boxed().toList(),
            partition ->
                CompletableFuture.supplyAsync(
                    () -> {
                      final var positionModel = positionMapper.findOne(partition);
                      if (positionModel == null || positionModel.lastExportedPosition() == null) {
                        throw new IllegalArgumentException(
                            "No exported position found for partition " + partition + " in RDBMS");
                      }

                      return Map.entry(partition, positionModel.lastExportedPosition());
                    },
                    executor))
        .thenApply(
            s -> s.stream().collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue)));
  }

  private CompletableFuture<List<BackupMetadata>> loadMetadataForAllPartitions(
      final int partitionCount) {
    return FuturesUtil.parTraverse(
        IntStream.rangeClosed(1, partitionCount).boxed().toList(),
        partition ->
            metadataSyncer
                .load(partition)
                .thenApply(
                    opt ->
                        opt.orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "No backup metadata found for partition " + partition))));
  }

  @Override
  public void close() {
    try {
      backupStore.closeAsync().join();
      metadataSyncer.close();
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
