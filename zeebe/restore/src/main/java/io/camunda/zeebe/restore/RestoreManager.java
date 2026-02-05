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
import io.camunda.zeebe.util.FileUtil;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.agrona.collections.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreManager {
  private static final Logger LOG = LoggerFactory.getLogger(RestoreManager.class);
  private final BrokerCfg configuration;
  private final BackupStore backupStore;
  private final MeterRegistry meterRegistry;
  private final CheckpointIdGenerator checkpointIdGenerator;

  public RestoreManager(
      final BrokerCfg configuration,
      final BackupStore backupStore,
      final MeterRegistry meterRegistry) {
    checkpointIdGenerator =
        new CheckpointIdGenerator(configuration.getData().getBackup().getOffset());
    this.configuration = configuration;
    this.backupStore = backupStore;
    this.meterRegistry = meterRegistry;
  }

  public void restore(
      final long backupId, final boolean validateConfig, final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    restore(new long[] {backupId}, validateConfig, ignoreFilesInTarget);
  }

  public void restore(
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

    restore(backupIds, false, validateConfig, ignoreFilesInTarget);
  }

  /**
   * Restores from the given time, restoring as much as possible from the complete backup range
   * that contains the from timestamp. This is used when the --to parameter is not provided, which
   * means the cluster is shutdown and we should find and restore from the complete range containing
   * the from timestamp.
   */
  public void restoreFromCompleteRange(
      final Instant from, final boolean validateConfig, final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    final var dataDirectory = Path.of(configuration.getData().getDirectory());
    verifyDataFolderIsEmpty(dataDirectory, ignoreFilesInTarget);

    final var partitionCount = configuration.getCluster().getPartitionsCount();
    final var fromCheckpointId = checkpointIdGenerator.fromTimestamp(from.toEpochMilli());

    // Find the complete range that contains 'from' for each partition
    Long rangeStart = null;
    Long rangeEnd = null;

    for (int partition = 1; partition <= partitionCount; partition++) {
      final var ranges = BackupRanges.fromMarkers(backupStore.rangeMarkers(partition).join());
      final var completeRanges =
          ranges.stream().filter(BackupRange.Complete.class::isInstance).toList();

      // Find the complete range that contains fromCheckpointId
      final var matchingRange =
          completeRanges.stream()
              .filter(
                  r ->
                      r.firstCheckpointId() <= fromCheckpointId
                          && r.lastCheckpointId() >= fromCheckpointId)
              .findFirst();

      if (matchingRange.isEmpty()) {
        throw new IllegalArgumentException(
            "No complete backup range found containing checkpoint %d (from timestamp %s) for partition %d. Available complete ranges: %s"
                .formatted(fromCheckpointId, from, partition, completeRanges));
      }

      final var range = matchingRange.get();
      if (rangeStart == null) {
        rangeStart = range.firstCheckpointId();
        rangeEnd = range.lastCheckpointId();
      } else {
        // Verify all partitions have the same range
        if (rangeStart != range.firstCheckpointId() || rangeEnd != range.lastCheckpointId()) {
          throw new IllegalStateException(
              "Partitions have different complete ranges. Expected range [%d, %d] but partition %d has range [%d, %d]"
                  .formatted(
                      rangeStart,
                      rangeEnd,
                      partition,
                      range.firstCheckpointId(),
                      range.lastCheckpointId()));
        }
      }
    }

    if (rangeStart == null || rangeEnd == null) {
      throw new IllegalArgumentException(
          "No complete backup range found containing checkpoint %d (from timestamp %s)"
              .formatted(fromCheckpointId, from));
    }

    LOG.info(
        "Found complete backup range [{}, {}] containing from checkpoint {} (timestamp {})",
        rangeStart,
        rangeEnd,
        fromCheckpointId,
        from);

    // Create a checkpoint pattern for the interval [fromCheckpointId, rangeEnd]
    // This optimizes the query by using a common prefix instead of querying all backups
    final var checkpointPattern =
        BackupIdentifierWildcard.CheckpointPattern.longestCommonPrefix(
            String.valueOf(fromCheckpointId), String.valueOf(rangeEnd));

    // Query each partition separately with the interval pattern to minimize object storage queries
    final var backupIds = new ArrayList<Long>();
    for (int partition = 1; partition <= partitionCount; partition++) {
      final var wildCard =
          BackupIdentifierWildcard.forPartition(partition, checkpointPattern);
      final var partitionBackups =
          backupStore
              .list(wildCard)
              .thenApply(
                  b ->
                      b.stream()
                          .filter(bs -> bs.statusCode() == BackupStatusCode.COMPLETED)
                          .filter(
                              bs ->
                                  bs.id().checkpointId() >= fromCheckpointId
                                      && bs.id().checkpointId() <= rangeEnd)
                          .map(bs -> bs.id().checkpointId())
                          .toList())
              .join();
      backupIds.addAll(partitionBackups);
    }

    final var backupIdsArray =
        backupIds.stream().distinct().sorted().mapToLong(Long::longValue).toArray();

    LOG.info(
        "Restoring {} backups from checkpoint {} to {} (range [{}, {}])",
        backupIdsArray.length,
        fromCheckpointId,
        rangeEnd,
        rangeStart,
        rangeEnd);

    if (backupIdsArray.length == 0) {
      throw new IllegalArgumentException(
          "No completed backups found in range [%d, %d]".formatted(fromCheckpointId, rangeEnd));
    }

    restore(backupIdsArray, false, validateConfig, ignoreFilesInTarget);
  }

  public void restore(
      final long[] backupIds, final boolean validateConfig, final List<String> ignoreFilesInTarget)
      throws IOException, ExecutionException, InterruptedException {
    final var dataDirectory = Path.of(configuration.getData().getDirectory());

    verifyDataFolderIsEmpty(dataDirectory, ignoreFilesInTarget);

    verifyBackupIdsAreContinuous(backupIds);

    final var partitionsToRestore = collectPartitions();
    try (final var executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("zeebe-restore-", 0).factory())) {
      final var tasks = new ArrayList<Callable<Void>>(partitionsToRestore.size());
      for (final var partition : partitionsToRestore) {
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

  private void verifyBackupIdsAreContinuous(final long[] backupIds) {
    final var partitionCount = configuration.getCluster().getPartitionsCount();

    final MutableBoolean validBackupRange = new MutableBoolean(true);
    if (backupIds.length > 1) {
      final var minBackup = Arrays.stream(backupIds).min().orElseThrow();
      final var maxBackup = Arrays.stream(backupIds).max().orElseThrow();

      for (int partition = 1; partition <= partitionCount; partition++) {
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
