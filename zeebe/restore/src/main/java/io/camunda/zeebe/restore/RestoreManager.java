/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.StaticConfigurationGenerator;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.impl.rocksdb.ChecksumProviderRocksDBImpl;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.restore.RestorePointResolver.RestorableBackups;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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

@NullMarked
public class RestoreManager implements CloseableSilently {
  private static final Logger LOG = LoggerFactory.getLogger(RestoreManager.class);
  private final BrokerCfg configuration;
  private final BackupStore backupStore;
  private final BackupMetadataSyncer metadataSyncer;
  private final MeterRegistry meterRegistry;
  @Nullable private final ExporterPositionMapper exporterPositionMapper;
  private final ExecutorService executor;

  @VisibleForTesting
  RestoreManager(
      final BrokerCfg configuration,
      final BackupStore backupStore,
      final MeterRegistry meterRegistry) {
    this(configuration, backupStore, null, meterRegistry);
  }

  public RestoreManager(
      final BrokerCfg configuration,
      final BackupStore backupStore,
      @Nullable final ExporterPositionMapper exporterPositionMapper,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.backupStore = backupStore;
    metadataSyncer = new BackupMetadataSyncer(backupStore, meterRegistry);
    this.exporterPositionMapper = exporterPositionMapper;
    this.meterRegistry = meterRegistry;
    executor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("zeebe-restore-", 0).factory());
  }

  public void restore(
      final long backupId, final boolean validateConfig, final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    restore(new long[] {backupId}, validateConfig, ignoreFilesInTarget);
  }

  public void restore(
      @Nullable final Instant from,
      @Nullable final Instant to,
      final boolean validateConfig,
      final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    // Data folder is verified separately, so that we can fail fast rather than loading metadata
    // and then verifying the data folder is not empty.
    // Doing it as soon as possible shortens the time to find out about this, helping to achieve
    // lower RTO
    verifyDataFolderIsEmpty(Path.of(configuration.getData().getDirectory()), ignoreFilesInTarget);
    restore(resolveBackups(from, to), validateConfig, ignoreFilesInTarget);
  }

  private void restore(
      final long[] backupIds, final boolean validateConfig, final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    verifyDataFolderIsEmpty(Path.of(configuration.getData().getDirectory()), ignoreFilesInTarget);
    restore(resolveBackups(backupIds), validateConfig, ignoreFilesInTarget);
  }

  /**
   * Resolves the backups to restore each partition from, without touching any data. The partitions
   * are those of the latest backup, which can be fewer than are configured when the cluster was
   * scaled up since the backup was taken.
   *
   * @param backupIds the backup IDs to use for all partitions
   * @return a map from partition ID to backup IDs
   */
  public Map<Integer, long[]> resolveBackups(final long[] backupIds) {
    final var latestBackupId =
        Arrays.stream(backupIds)
            .max()
            .orElseThrow(() -> new IllegalArgumentException("No backup ID specified"));
    final var partitionCount = backupPartitionCount(latestBackupId);
    return IntStream.rangeClosed(1, partitionCount)
        .boxed()
        .collect(Collectors.toMap(partition -> partition, partition -> backupIds));
  }

  /**
   * Resolves the backups to restore each partition from in the given time range, without touching
   * any data. With RDBMS as secondary storage, the partitions are those the RDBMS holds exported
   * positions for, which have to be the partitions of the last backups restored.
   *
   * @return a map from partition ID to backup IDs
   */
  public Map<Integer, long[]> resolveBackups(
      @Nullable final Instant from, @Nullable final Instant to) {
    if (exporterPositionMapper == null) {
      if (from == null) {
        throw new IllegalArgumentException(
            "Expected `from` to not be null, but got <null>. When the restore is not using a RDBMS as secondary storage, `from` parameter is required");
      }
      return resolveTimeRangeBackups(from, to);
    } else {
      return resolveRdbmsBackups(from, to);
    }
  }

  private Map<Integer, long[]> resolveRdbmsBackups(
      @Nullable final Instant from, @Nullable final Instant to) {
    final var exportedPositions =
        exportedPositions(configuration.getCluster().getPartitionsCount());
    LOG.info("Exported positions for all partitions: {}", exportedPositions);

    // Load backup metadata for each partition in parallel
    final var metadataByPartition = loadMetadataForAllPartitions(exportedPositions.size()).join();

    final var restorableBackups =
        RestorePointResolver.resolve(metadataByPartition, from, to, exportedPositions);
    verifyLastBackupsHoldThePartitions(restorableBackups, exportedPositions.size());

    final var backupIdsByPartition = toBackupIdsByPartition(restorableBackups);
    LOG.info(
        "Restoring RDBMS backups in range [{},{}] to global checkpoint {}: {}",
        from,
        to,
        restorableBackups.globalCheckpointId(),
        backupIdsByPartition);
    return backupIdsByPartition;
  }

  private Map<Integer, long[]> resolveTimeRangeBackups(
      final Instant from, @Nullable final Instant to) {
    final var partitionCount = configuration.getCluster().getPartitionsCount();

    // Load backup metadata for each partition in parallel
    final var metadataByPartition = loadMetadataForAllPartitions(partitionCount).join();

    final var restorableBackups = RestorePointResolver.resolve(metadataByPartition, from, to, null);

    final var backupIdsByPartition = toBackupIdsByPartition(restorableBackups);
    LOG.info(
        "Restoring time-range backups in range [{},{}] to global checkpoint {}: {}",
        from,
        to,
        restorableBackups.globalCheckpointId(),
        backupIdsByPartition);
    return backupIdsByPartition;
  }

  /** Converts the checkpoints to restore of each partition to their backup IDs. */
  private static Map<Integer, long[]> toBackupIdsByPartition(
      final RestorableBackups restorableBackups) {
    return restorableBackups.backupsByPartitionId().entrySet().stream()
        .collect(
            Collectors.toMap(
                Entry::getKey,
                e ->
                    e.getValue().stream()
                        .mapToLong(BackupMetadata.CheckpointEntry::checkpointId)
                        .toArray()));
  }

  /**
   * The partitions to restore are those of the last backups, which have to be the ones the RDBMS
   * holds exported positions for.
   */
  private void verifyLastBackupsHoldThePartitions(
      final RestorableBackups restorableBackups, final int exportedPartitionCount) {
    restorableBackups.backupsByPartitionId().values().stream()
        .map(backups -> backups.getLast().checkpointId())
        .distinct()
        .sorted()
        .forEach(
            lastBackup -> {
              final var backupPartitionCount = backupPartitionCount(lastBackup);
              if (backupPartitionCount != exportedPartitionCount) {
                throw new IllegalStateException(
                    ("Cannot restore: backup %d was taken with %d partitions, but the RDBMS holds "
                            + "exported positions for %d")
                        .formatted(lastBackup, backupPartitionCount, exportedPartitionCount));
              }
            });
  }

  /**
   * The partition count recorded in the descriptor of the given backup, on any partition since
   * every partition's backup records the same count, rejected if more partitions are configured.
   */
  private int backupPartitionCount(final long backupId) {
    final var backupPartitionCount =
        backupStore
            .list(
                new BackupIdentifierWildcardImpl(
                    Optional.empty(), Optional.empty(), CheckpointPattern.of(backupId)))
            .thenApply(
                statuses ->
                    statuses.stream()
                        .filter(status -> status.statusCode() == BackupStatusCode.COMPLETED)
                        .findAny()
                        .flatMap(BackupStatus::descriptor)
                        .orElseThrow(() -> new BackupNotFoundException(backupId))
                        .numberOfPartitions())
            .join();
    final var configuredPartitionCount = configuration.getCluster().getPartitionsCount();
    if (backupPartitionCount > configuredPartitionCount) {
      throw new IllegalArgumentException(
          "Cannot restore backup %d: it has %d partitions, but only %d are configured"
              .formatted(backupId, backupPartitionCount, configuredPartitionCount));
    }
    return backupPartitionCount;
  }

  /**
   * Restores partitions from backups, where each partition may restore from different backup IDs.
   *
   * <p>This is useful when partitions have different safe start checkpoints based on their exported
   * positions, but all need to reach the same global checkpoint. Only the given partitions are
   * restored, placed on the brokers as a cluster with that many partitions would place them.
   *
   * @param backupIdsByPartition map from partition ID to the backup IDs to restore for that
   *     partition
   * @param validateConfig whether to validate the backup configuration
   * @param ignoreFilesInTarget files to ignore when checking if the data directory is empty
   */
  public void restore(
      final Map<Integer, long[]> backupIdsByPartition,
      final boolean validateConfig,
      final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    final var dataDirectory = Path.of(configuration.getData().getDirectory());

    verifyDataFolderIsEmpty(dataDirectory, ignoreFilesInTarget);

    final var partitionCount = backupIdsByPartition.size();
    try {
      final var partitionsToRestore = collectPartitions(partitionCount);
      final var tasks = new ArrayList<Callable<Void>>(partitionsToRestore.size());
      for (final var partition : partitionsToRestore) {
        final var partitionId = partition.partition().id().id();
        final var backupIds = backupIdsByPartition.get(partitionId);
        if (backupIds == null || backupIds.length == 0) {
          throw new IllegalArgumentException("No backup IDs provided for partition " + partitionId);
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

      if (configuration.getCluster().getNodeId() == 0) {
        restoreTopologyFile(partitionCount);
      }
    } catch (final ExecutionException | InterruptedException e) {
      LOG.error("Failed to restore broker. Deleting data directory {}", dataDirectory, e);
      FileUtil.deleteFolderContents(dataDirectory);
      throw e;
    }
  }

  private void restoreTopologyFile(final int partitionCount)
      throws ExecutionException, InterruptedException, IOException {
    final var coordinatorId = MemberId.from("0");
    LOG.info("Restoring topology file");
    final var file =
        Path.of(configuration.getData().getDirectory())
            .resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
    final var staticConfiguration =
        StaticConfigurationGenerator.getStaticConfiguration(
            configuration, partitionCount, coordinatorId);
    final var initializer = new StaticInitializer(staticConfiguration);
    // it's ok to block, it's not really async
    final var base = initializer.initialize().get();
    final var configuration =
        new ClusterConfiguration(
            base.version(),
            base.members(),
            base.lastChange(),
            Optional.of(
                ClusterChangePlan.initForRestore(
                    List.of(new UpdateRoutingState(coordinatorId, Optional.empty())))),
            base.routingState(),
            base.clusterId(),
            base.incarnationNumber());
    final var persistedConfiguration =
        PersistedClusterConfiguration.ofFile(file, new ProtoBufSerializer());
    persistedConfiguration.update(configuration);
    LOG.info("Successfully restored topology file {}", base);
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
            new ChecksumProviderRocksDBImpl(),
            partition.registry());
    try {
      restoreService.restore(backupIds, validator);
      LOG.info(
          "Successfully restored partition {} from backups {}.",
          raftPartition.id().id(),
          backupIds);
    } finally {
      MicrometerUtil.close(registry);
    }
  }

  /** The local partitions of a cluster with {@code partitionCount} partitions. */
  private Set<InstrumentedRaftPartition> collectPartitions(final int partitionCount) {
    final var localBrokerId = configuration.getCluster().getNodeId();
    final var localMember = MemberId.from(String.valueOf(localBrokerId));
    final var clusterTopology =
        new PartitionDistribution(
            StaticConfigurationGenerator.getStaticConfiguration(
                    configuration, partitionCount, localMember)
                .generatePartitionDistribution());
    final var raftPartitionFactory = new RaftPartitionFactory(configuration);

    return clusterTopology.partitions().stream()
        .filter(partitionMetadata -> partitionMetadata.members().contains(localMember))
        .map(metadata -> createRaftPartition(metadata, raftPartitionFactory))
        .collect(Collectors.toSet());
  }

  private InstrumentedRaftPartition createRaftPartition(
      final PartitionMetadata metadata, final RaftPartitionFactory factory) {
    final var partitionId = metadata.id().id();
    final var partitionRegistry =
        MicrometerUtil.wrap(meterRegistry, PartitionKeyNames.tags(partitionId));

    return new InstrumentedRaftPartition(
        factory.createRaftPartition(metadata, partitionRegistry), partitionRegistry);
  }

  private void verifyDataFolderIsEmpty(
      final Path dataDirectory, final List<String> ignoreFilesInTarget) throws IOException {
    if (!dataFolderIsEmpty(dataDirectory, ignoreFilesInTarget)) {
      LOG.error(
          "Brokers's data directory {} is not empty. Aborting restore to avoid overwriting data. Please restart with a clean directory.",
          dataDirectory);
      throw new DirectoryNotEmptyException(dataDirectory.toString());
    }
  }

  private boolean dataFolderIsEmpty(final Path dir, final List<String> ignoreFilesInTarget)
      throws IOException {
    if (!Files.exists(dir)) {
      return true;
    }

    try (final var entries = Files.list(dir)) {
      return entries
          // ignore configured files/directories that we don't care about, e.g. `lost+found`.
          .filter(path -> ignoreFilesInTarget.stream().noneMatch(path::endsWith))
          .findFirst()
          .isEmpty();
    }
  }

  /**
   * The exported positions of the partitions the RDBMS holds: partitions 1 up to the first one
   * without an exported position. Partitions without one after that are fine, e.g. those a scale-up
   * added after the database was restored, but a position after a missing one means the database is
   * inconsistent.
   */
  private Map<Integer, Long> exportedPositions(final int partitionCount) {
    final var positionByPartition =
        FuturesUtil.parTraverse(
                IntStream.rangeClosed(1, partitionCount).boxed().toList(),
                partition ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          final var positionModel = exporterPositionMapper.findOne(partition);
                          return Map.entry(
                              partition,
                              Optional.ofNullable(positionModel)
                                  .map(ExporterPositionModel::lastExportedPosition));
                        },
                        executor))
            .join()
            .stream()
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> a, TreeMap::new));

    final var positions = new TreeMap<Integer, Long>();
    var firstMissing = 0;
    for (final var entry : positionByPartition.entrySet()) {
      final var partition = entry.getKey();
      if (entry.getValue().isEmpty()) {
        if (firstMissing == 0) {
          firstMissing = partition;
        }
      } else if (firstMissing != 0) {
        throw new IllegalStateException(
            "The RDBMS holds an exported position for partition %d but none for partition %d"
                .formatted(partition, firstMissing));
      } else {
        positions.put(partition, entry.getValue().get());
      }
    }
    if (positions.isEmpty()) {
      throw new IllegalArgumentException("No exported position found for partition 1 in RDBMS");
    }
    return positions;
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
