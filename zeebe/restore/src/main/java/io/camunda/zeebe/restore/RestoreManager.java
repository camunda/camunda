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
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.BackupRanges;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.Interval;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.collections.MutableBoolean;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public class RestoreManager implements CloseableSilently {
  private static final Logger LOG = LoggerFactory.getLogger(RestoreManager.class);
  private final BrokerCfg configuration;
  private final BackupStore backupStore;
  private final BackupRangeResolver rangeResolver;
  private final MeterRegistry meterRegistry;
  private final CheckpointIdGenerator checkpointIdGenerator;
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
    checkpointIdGenerator =
        new CheckpointIdGenerator(configuration.getData().getBackup().getOffset());
    this.configuration = configuration;
    this.backupStore = backupStore;
    rangeResolver = new BackupRangeResolver(backupStore);
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
      final Instant from,
      @Nullable final Instant to,
      final boolean validateConfig,
      final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    if (exporterPositionMapper == null) {
      restoreTimeRange(from, to != null ? to : Instant.now(), validateConfig, ignoreFilesInTarget);
    } else {
      restoreRdbms(from, to, validateConfig, ignoreFilesInTarget);
    }
  }

  private void restoreRdbms(
      final Instant from,
      @Nullable final Instant to,
      final boolean validateConfig,
      final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    final var partitionCount = configuration.getCluster().getPartitionsCount();

    final var exportedPositions = exportedPositions(partitionCount).join();
    LOG.info("Exported positions for all partitions: {}", exportedPositions);
    final var interval = Interval.closed(from, to);
    final var restoreInfos =
        rangeResolver
            .getRestoreInfoForAllPartitions(
                from, to, partitionCount, exportedPositions, checkpointIdGenerator, executor)
            .join();

    LOG.info(
        "Restoring RDBMS backups in range [{},{}] to global checkpoint {}: {}",
        from,
        to,
        restoreInfos.globalCheckpointId(),
        restoreInfos.backupsByPartitionId());

    restore(restoreInfos.backupsByPartitionId(), validateConfig, ignoreFilesInTarget);
  }

  public void restoreTimeRange(
      final Instant from,
      final Instant to,
      final boolean validateConfig,
      final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    final var dataDirectory = Path.of(configuration.getData().getDirectory());

    // Data folder is verified separately, so that we can fail fast rather than downloading
    // backups and then verifying the data folder is not empty.
    // Doing it as soon as possible shortens the time to find out about this, helping to achieve
    // lower RTO
    verifyDataFolderIsEmpty(dataDirectory, ignoreFilesInTarget);

    final var wildCard =
        BackupIdentifierWildcard.ofPattern(
            CheckpointPattern.ofTimeRange(from, to, checkpointIdGenerator));
    final var backups =
        backupStore
            .list(wildCard)
            .thenApply(
                b ->
                    b.stream().filter(bs -> bs.statusCode() == BackupStatusCode.COMPLETED).toList())
            .join();
    final var backupIds =
        backups.stream()
            .map(BackupStatus::id)
            .mapToLong(BackupIdentifier::checkpointId)
            .distinct()
            .sorted()
            .toArray();
    LOG.info("Completed backups in range [{},{}] are {}", from, to, backupIds);

    if (backupIds.length == 0) {
      throw new IllegalArgumentException("No backups found in range [" + from + "," + to + "]");
    }

    restore(backupIds, validateConfig, ignoreFilesInTarget);
  }

  public void restore(
      final long[] backupIds, final boolean validateConfig, final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    restore(toBackupIdsByPartition(backupIds), validateConfig, ignoreFilesInTarget);
  }

  /**
   * Converts a common array of backup IDs to a map where each partition uses the same backup IDs.
   *
   * @param backupIds the backup IDs to use for all partitions
   * @return a map from partition ID to backup IDs
   */
  private Map<Integer, long[]> toBackupIdsByPartition(final long[] backupIds) {
    final var partitionCount = configuration.getCluster().getPartitionsCount();
    return IntStream.rangeClosed(1, partitionCount)
        .boxed()
        .collect(
            Collectors.toMap(
                partition -> partition, partition -> Arrays.copyOf(backupIds, backupIds.length)));
  }

  /**
   * Restores partitions from backups, where each partition may restore from different backup IDs.
   *
   * <p>This is useful when partitions have different safe start checkpoints based on their exported
   * positions, but all need to reach the same global checkpoint.
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

    verifyBackupIdsAreContinuous(backupIdsByPartition);

    try {
      final var partitionsToRestore = collectPartitions();
      final var tasks = new ArrayList<Callable<Void>>(partitionsToRestore.size());
      for (final var partition : partitionsToRestore) {
        final var partitionId = partition.partition().id().id();
        final var backupIds = backupIdsByPartition.get(partitionId);
        if (backupIds == null || backupIds.length == 0) {
          throw new IllegalArgumentException("No backup IDs provided for partition " + partitionId);
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

      if (configuration.getCluster().getNodeId() == 0) {
        restoreTopologyFile();
      }
    } catch (final ExecutionException | InterruptedException e) {
      LOG.error("Failed to restore broker. Deleting data directory {}", dataDirectory, e);
      FileUtil.deleteFolderContents(dataDirectory);
      throw e;
    }
  }

  private void restoreTopologyFile() throws ExecutionException, InterruptedException, IOException {
    final var coordinatorId = MemberId.from("0");
    LOG.info("Restoring topology file");
    final var file =
        Path.of(configuration.getData().getDirectory())
            .resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
    final var staticConfiguration =
        StaticConfigurationGenerator.getStaticConfiguration(configuration, coordinatorId);
    final var initializer = new StaticInitializer(staticConfiguration);
    // it's ok to block, it's not really async
    final var base = initializer.initialize().get();
    final var configuration =
        new ClusterConfiguration(
            base.version(),
            base.members(),
            base.lastChange(),
            Optional.of(
                ClusterChangePlan.init(
                    1L, List.of(new UpdateRoutingState(coordinatorId, Optional.empty())))),
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
      final boolean validateConfig)
      throws IOException, FlushException {
    final BackupValidator validator;
    final RaftPartition raftPartition = partition.partition();

    if (validateConfig) {
      validator = new ValidatePartitionCount(configuration.getCluster().getPartitionsCount());
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

  private Set<InstrumentedRaftPartition> collectPartitions() {
    final var localBrokerId = configuration.getCluster().getNodeId();
    final var localMember = MemberId.from(String.valueOf(localBrokerId));
    final var clusterTopology =
        new PartitionDistribution(
            StaticConfigurationGenerator.getStaticConfiguration(configuration, localMember)
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

  private void verifyBackupIdsAreContinuous(final Map<Integer, long[]> backupIdsByPartition) {
    final MutableBoolean validBackupRange = new MutableBoolean(true);

    for (final var entry : backupIdsByPartition.entrySet()) {
      final var partition = entry.getKey();
      final var backupIds = entry.getValue();

      if (backupIds.length > 1) {
        final var minBackup = Arrays.stream(backupIds).min().orElseThrow();
        final var maxBackup = Arrays.stream(backupIds).max().orElseThrow();

        final var ranges = BackupRanges.fromMarkers(backupStore.rangeMarkers(partition).join());
        final var validRange =
            ranges.stream()
                .filter(r -> r.contains(new Interval<>(minBackup, maxBackup)))
                .findFirst();

        if (validRange.isEmpty()) {
          final var completeRanges =
              ranges.stream().filter(BackupRange.Complete.class::isInstance).toList();
          LOG.error(
              "Expected to find a continuous range of backups between {} and {} for partition {}. Complete ranges are the following: {}",
              minBackup,
              maxBackup,
              partition,
              completeRanges);
          validBackupRange.set(false);
        }
      }
    }

    if (!validBackupRange.get()) {
      LOG.error("Found one or more invalid backup ranges. Aborting restore.");
      throw new IllegalStateException("Invalid backup ranges");
    }
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

  private CompletableFuture<Map<Integer, Long>> exportedPositions(final int partitionCount) {
    return FuturesUtil.parTraverse(
            IntStream.rangeClosed(1, partitionCount).boxed().toList(),
            partition ->
                CompletableFuture.supplyAsync(
                    () -> {
                      final var positionModel = exporterPositionMapper.findOne(partition);
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

  @Override
  public void close() {
    try {
      executor.shutdown();
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (final InterruptedException ignored) {
      // Not much we can do here, just report in case it's a bug in the shutdown process.
      LOG.warn("Interrupted while waiting for executor to shutdown", ignored);
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
