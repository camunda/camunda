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
import io.camunda.zeebe.broker.eventplane.EventPlaneStores;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.eventplane.history.RestoredHistory;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.util.CloseableSilently;
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
import java.util.UUID;
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
 *
 * <p>With {@code zeebe.broker.experimental.eventPlane.restoreFromBucket}, it restores the
 * partitions from the Event Plane's bucket instead, and needs no backup store: see {@link
 * #restoreFromBucket()}.
 */
@NullMarked
public class RestoreManager implements CloseableSilently {
  private static final Logger LOG = LoggerFactory.getLogger(RestoreManager.class);
  private final BrokerCfg configuration;
  private final String physicalTenantId;
  private final BrokerCfg physicalTenantConfiguration;
  private final Set<PartitionMetadata> partitions;
  private final @Nullable BackupStore backupStore;
  private final @Nullable BackupMetadataSyncer metadataSyncer;
  private final MeterRegistry meterRegistry;
  @Nullable private final ExporterPositionMapper exporterPositionMapper;
  private final ExecutorService executor;

  /**
   * Restores the default physical tenant, configured by {@code configuration} itself.
   *
   * @param backupStore the backups to restore from, or null to restore from the Event Plane's
   *     bucket only
   */
  public RestoreManager(
      final BrokerCfg configuration,
      final @Nullable BackupStore backupStore,
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
      final @Nullable BackupStore backupStore,
      @Nullable final ExporterPositionMapper exporterPositionMapper,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.physicalTenantId = physicalTenantId;
    this.physicalTenantConfiguration = physicalTenantConfiguration;
    this.partitions = Set.copyOf(partitions);
    this.backupStore = backupStore;
    metadataSyncer =
        backupStore == null ? null : new BackupMetadataSyncer(backupStore, meterRegistry);
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
   * <p>With the Event Plane's {@code restoreFromBucket}, it restores these partitions from the
   * bucket instead, and ignores the backup IDs.
   *
   * @param backupIdsByPartition map from partition ID to the backup IDs to restore for that
   *     partition
   * @param validateConfig whether to validate the backup configuration
   */
  public void restore(final Map<Integer, long[]> backupIdsByPartition, final boolean validateConfig)
      throws IOException, ExecutionException, InterruptedException {
    final var partitionCount = backupIdsByPartition.size();
    if (configuration.getExperimental().getEventPlane().isRestoreFromBucket()) {
      restoreFromBucket(partitionCount);
      return;
    }
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

  /**
   * Restores the tenant's local partitions from the Event Plane's bucket, each to {@code
   * zeebe.broker.experimental.eventPlane.restoreTo} or to the latest the bucket holds, and leaves
   * each the history it goes on in: the one the operator names in {@code restoreHistory}, taking
   * over the bucket's current history at the restored checkpoint.
   */
  public void restoreFromBucket() throws IOException, ExecutionException, InterruptedException {
    restoreFromBucket(physicalTenantConfiguration.getCluster().getPartitionsCount());
  }

  private void restoreFromBucket(final int partitionCount)
      throws IOException, ExecutionException, InterruptedException {
    final var eventPlane = configuration.getExperimental().getEventPlane();
    final var history = restoreHistory();
    final var target =
        eventPlane.getRestoreTo() == null ? BucketRestoreService.LATEST : eventPlane.getRestoreTo();
    final var tasks = new ArrayList<Callable<Void>>();
    for (final var partition : collectPartitions(partitionCount)) {
      tasks.add(
          () -> {
            restorePartitionFromBucket(partition, history, target);
            return null;
          });
    }
    for (final var result : executor.invokeAll(tasks)) {
      result.get(); // throw exception if any of the tasks failed
    }
  }

  private void restorePartitionFromBucket(
      final InstrumentedRaftPartition partition, final UUID history, final long target)
      throws IOException, FlushException {
    final var raftPartition = partition.partition();
    final var eventPlane = configuration.getExperimental().getEventPlane();
    try (final var store = EventPlaneStores.open(eventPlane.getStore())) {
      final var restored =
          new BucketRestoreService(
                  store,
                  bucketClusterId(eventPlane, store),
                  raftPartition,
                  configuration.getCluster().getNodeId(),
                  new RocksDBSnapshotFileInfoProvider(),
                  partition.registry())
              .restore(target);
      LOG.info(
          "Successfully restored partition {} from the Event Plane's bucket: {}",
          raftPartition.id().number(),
          restored);
      // the Event Plane goes on in a new history, which takes the bucket's current one over
      new RestoredHistory(
              history,
              Instant.now(),
              restored.history(),
              restored.checkpoint().index(),
              restored.checkpoint().position())
          .writeTo(raftPartition.dataDirectory().toPath());
    } finally {
      MicrometerUtil.close(partition.registry());
    }
  }

  /**
   * The cluster id in the bucket's object names: the configured one, else the only cluster the
   * bucket has, as the cluster's generated id isn't known before its topology is restored.
   */
  public static String bucketClusterId(final EventPlaneCfg eventPlane, final ObjectStore store)
      throws IOException {
    final var configured = eventPlane.getClusterId();
    if (configured != null) {
      return configured;
    }
    final var clusters = new ArrayList<String>();
    for (final var prefix : store.listPrefixes("")) {
      final var name = ObjectNames.decode(prefix.substring(0, prefix.length() - 1));
      if (!name.startsWith("_")) {
        clusters.add(name);
      }
    }
    if (clusters.size() != 1) {
      throw new IllegalArgumentException(
          "Expected zeebe.broker.experimental.eventPlane.clusterId, or a bucket of one cluster, but got none and the clusters %s"
              .formatted(clusters));
    }
    return clusters.getFirst();
  }

  /** The history the Event Plane goes on in after a restore from the bucket. */
  private UUID restoreHistory() {
    final var restoreHistory = configuration.getExperimental().getEventPlane().getRestoreHistory();
    if (restoreHistory == null) {
      throw new IllegalArgumentException(
          "Expected the Event Plane's history after the restore, in"
              + " zeebe.broker.experimental.eventPlane.restoreHistory, but got none");
    }
    return UUID.fromString(restoreHistory);
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
            requireBackups(backupStore),
            partition.partition(),
            configuration.getCluster().getNodeId(),
            new RocksDBSnapshotFileInfoProvider(),
            partition.registry());
    try {
      final var eventPlaneHistory = eventPlaneHistory();
      final var checkpoint = restoreService.restore(backupIds, validator);
      LOG.info(
          "Successfully restored partition {} from backups {}.",
          raftPartition.id().number(),
          backupIds);
      if (eventPlaneHistory != null) {
        // the Event Plane goes on in a new history, from the checkpoint
        new RestoredHistory(
                eventPlaneHistory, Instant.now(), null, checkpoint.index(), checkpoint.position())
            .writeTo(raftPartition.dataDirectory().toPath());
      }
    } finally {
      MicrometerUtil.close(registry);
    }
  }

  /** The Event Plane's history after the restore, which the operator names, if it is on. */
  private @Nullable UUID eventPlaneHistory() {
    final var eventPlane = configuration.getExperimental().getEventPlane();
    if (!eventPlane.isEnabled()) {
      return null;
    }
    if (eventPlane.getRestoreHistory() == null) {
      throw new IllegalArgumentException(
          "Expected the Event Plane's history after the restore, in"
              + " zeebe.broker.experimental.eventPlane.restoreHistory, but got none");
    }
    return UUID.fromString(eventPlane.getRestoreHistory());
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
            requireBackups(metadataSyncer)
                .load(partition)
                .thenApply(
                    opt ->
                        opt.orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "No backup metadata found for partition " + partition))));
  }

  /** What restoring from backups needs, which a restore from the bucket alone doesn't have. */
  private static <T> T requireBackups(final @Nullable T backups) {
    if (backups == null) {
      throw new IllegalStateException(
          "Expected a backup store to restore from, but got none: this restore is from the Event"
              + " Plane's bucket");
    }
    return backups;
  }

  @Override
  public void close() {
    try {
      if (backupStore != null) {
        backupStore.closeAsync().join();
      }
      if (metadataSyncer != null) {
        metadataSyncer.close();
      }
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
