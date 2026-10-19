/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.LogArchiveStoreFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.BridgedFromArchive;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.FromArchive;
import io.camunda.zeebe.restore.logarchive.HandoverRestorePlan.OldBackups;
import io.camunda.zeebe.restore.logarchive.HandoverRestoreRequest;
import io.camunda.zeebe.restore.logarchive.HandoverRestoreResolver;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreTarget;
import io.camunda.zeebe.restore.logarchive.PartitionRestoreSources;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores the default physical tenant across the handover from the old backups to the log archive
 * (spec section 18.5, PoC scope: no other tenants). Each local partition is restored from where the
 * target checkpoint exists, old backups or the archive, or bridged from both when the RDBMS needs
 * log from before the archive; see {@link HandoverRestoreResolver}. All paths write the timeline
 * file as they do on their own, and node 0 writes the restored topology file, as in a classic
 * whole-cluster restore.
 */
@NullMarked
public final class HandoverClusterRestore implements CloseableSilently {

  private static final Logger LOG = LoggerFactory.getLogger(HandoverClusterRestore.class);

  private final BrokerCfg configuration;
  private final BackupStore backupStore;
  private final LogArchiveStore logArchiveStore;
  private final MeterRegistry meterRegistry;

  /**
   * @param configuration the default physical tenant's configuration
   * @param backupStore the store of the old backups; not closed by this
   */
  public HandoverClusterRestore(
      final BrokerCfg configuration,
      final BackupStore backupStore,
      final MeterRegistry meterRegistry) {
    this(
        configuration,
        backupStore,
        LogArchiveStoreFactory.createStore(configuration.getData().getBackup()),
        meterRegistry);
  }

  HandoverClusterRestore(
      final BrokerCfg configuration,
      final BackupStore backupStore,
      final LogArchiveStore logArchiveStore,
      final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.backupStore = backupStore;
    this.logArchiveStore = logArchiveStore;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Resolves where each partition is restored from.
   *
   * @param explicitTimeline the log archive timeline named by the operator, the only one searched
   */
  public HandoverRestorePlan resolve(
      final HandoverRestoreRequest request, final Optional<String> explicitTimeline) {
    final var sources =
        PartitionRestoreSources.load(
            backupStore,
            logArchiveStore,
            configuration.getCluster().getPartitionsCount(),
            configuration.getData().getBackup().getLogArchive().getTimeline(),
            explicitTimeline);
    return HandoverRestoreResolver.resolve(sources, request);
  }

  /**
   * Restores the local partitions by the plan into the empty data directory; deletes what it
   * restored if any partition fails.
   */
  public void restore(
      final HandoverRestorePlan plan,
      final boolean validateConfig,
      final List<String> ignoreFilesInTarget)
      throws IOException {
    final var dataDirectory = Path.of(configuration.getData().getDirectory());
    ClusterRestore.verifyDirectoryIsEmpty(dataDirectory, ignoreFilesInTarget);
    final var partitionCount = plan.partitions().size();
    final var brokerId = configuration.getCluster().getNodeId();
    final var raftPartitionFactory = new RaftPartitionFactory(configuration);
    final var archiveRestore =
        new LogArchiveRestore(
            logArchiveStore, new RocksDBSnapshotFileInfoProvider(), meterRegistry);
    final BackupValidator validator =
        validateConfig ? new ValidatePartitionCount(partitionCount) : BackupValidator.none();
    final var localPartitions =
        ClusterRestore.localPartitionsOf(
                configuration,
                Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, configuration),
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
            .stream()
            .filter(metadata -> metadata.id().number() <= partitionCount)
            .toList();
    try {
      for (final var metadata : localPartitions) {
        final var partitionId = metadata.id().number();
        final var registry =
            MicrometerUtil.wrap(meterRegistry, PartitionKeyNames.tags(metadata.id()));
        try {
          final var raftPartition = raftPartitionFactory.createRaftPartition(metadata, registry);
          switch (Objects.requireNonNull(plan.partitions().get(partitionId))) {
            case final OldBackups old -> {
              final var backupIds = old.backupIds().stream().mapToLong(Long::longValue).toArray();
              new PartitionRestoreService(
                      backupStore,
                      raftPartition,
                      brokerId,
                      new RocksDBSnapshotFileInfoProvider(),
                      registry)
                  .restore(backupIds, validator);
              RestoreManager.writeLogArchiveTimeline(
                  configuration, raftPartition.dataDirectory().toPath(), backupIds);
              LOG.info("Restored partition {} from old backups {}", partitionId, old.backupIds());
            }
            case final FromArchive archived -> {
              final var result =
                  archiveRestore.restorePartition(
                      archived.view(),
                      archived.checkpoint().checkpointId(),
                      LogArchiveRestoreTarget.of(raftPartition, brokerId),
                      archived.exportedPosition());
              LOG.info(
                  "Restored partition {} from log archive timeline '{}'; new timeline is '{}'",
                  partitionId,
                  archived.timelineId(),
                  result.timelineId());
            }
            case final BridgedFromArchive bridged -> {
              LOG.info(
                  "Restoring partition {} bridged: old backups {} up to index {}, log archive"
                      + " timeline '{}' from switch index {}, because the exported position {} lies"
                      + " before the start of the archive",
                  partitionId,
                  bridged.oldBackupIds(),
                  bridged.switchIndex() - 1,
                  bridged.timelineId(),
                  bridged.switchIndex(),
                  bridged.exportedPosition());
              new PartitionRestoreService(
                      backupStore,
                      raftPartition,
                      brokerId,
                      new RocksDBSnapshotFileInfoProvider(),
                      registry)
                  .restoreLog(
                      bridged.oldBackupIds().stream().mapToLong(Long::longValue).toArray(),
                      validator);
              final var result =
                  archiveRestore.restoreBridgedPartition(
                      bridged, LogArchiveRestoreTarget.of(raftPartition, brokerId));
              LOG.info(
                  "Restored partition {} bridged at switch index {}; new timeline is '{}'",
                  partitionId,
                  bridged.switchIndex(),
                  result.timelineId());
            }
          }
        } finally {
          MicrometerUtil.close(registry);
        }
      }
      if (brokerId == 0) {
        ClusterRestore.writeRestoredTopologyFile(
            configuration,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, configuration),
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionCount));
      }
      LOG.info(
          "Restored partitions {} to checkpoint {}",
          localPartitions.stream().map(metadata -> metadata.id().number()).sorted().toList(),
          plan.checkpointId());
    } catch (final IOException | FlushException | RuntimeException e) {
      LOG.error("Failed to restore across the handover. Deleting the restored data", e);
      FileUtil.deleteFolderContents(dataDirectory);
      if (e instanceof final IOException ioException) {
        throw ioException;
      }
      if (e instanceof final RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() {
    logArchiveStore.closeAsync().join();
  }
}
