/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.LogArchiveStoreFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreResult;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreTarget;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores the local partitions of the default physical tenant from the log archive (PoC scope: no
 * other tenants). Node 0 writes the restored topology file, as in a classic whole-cluster restore.
 */
@NullMarked
public final class LogArchiveClusterRestore {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveClusterRestore.class);

  private final BrokerCfg configuration;
  private final MeterRegistry meterRegistry;

  public LogArchiveClusterRestore(
      final BrokerCfg configuration, final MeterRegistry meterRegistry) {
    this.configuration = configuration;
    this.meterRegistry = meterRegistry;
  }

  /**
   * @param timelineId the timeline to restore from
   * @param checkpointId the checkpoint to restore to, or empty for the latest common checkpoint
   * @param ignoreFilesInTarget entries of the data directory that do not count as data
   */
  public LogArchiveRestoreResult restore(
      final String timelineId,
      final Optional<Long> checkpointId,
      final List<String> ignoreFilesInTarget)
      throws IOException {
    final var dataDirectory = Path.of(configuration.getData().getDirectory());
    ClusterRestore.verifyDirectoryIsEmpty(dataDirectory, ignoreFilesInTarget);

    final var partitionCount = configuration.getCluster().getPartitionsCount();
    final var brokerId = configuration.getCluster().getNodeId();
    final var raftPartitionFactory = new RaftPartitionFactory(configuration);
    final var partitions =
        ClusterRestore.localPartitionsOf(
            configuration,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, configuration),
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    final var registries =
        partitions.stream()
            .filter(metadata -> metadata.id().number() <= partitionCount)
            .map(
                metadata ->
                    Map.entry(
                        metadata,
                        MicrometerUtil.wrap(meterRegistry, PartitionKeyNames.tags(metadata.id()))))
            .toList();

    final LogArchiveStore store =
        LogArchiveStoreFactory.createStore(configuration.getData().getBackup());
    try {
      final var targets =
          registries.stream()
              .map(
                  entry ->
                      LogArchiveRestoreTarget.of(
                          raftPartitionFactory.createRaftPartition(
                              entry.getKey(), entry.getValue()),
                          brokerId))
              .toList();
      final var result =
          new LogArchiveRestore(store, new RocksDBSnapshotFileInfoProvider(), meterRegistry)
              .restorePartitions(timelineId, partitionCount, checkpointId, targets);
      // Like a classic whole-cluster restore: node 0 writes the topology the restored brokers boot
      // from; the others leave it to node 0.
      if (brokerId == 0) {
        ClusterRestore.writeRestoredTopologyFile(
            configuration,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, configuration),
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionCount));
      }
      LOG.info(
          "Restored partitions {} from log archive timeline '{}' to checkpoint {}; new timeline is '{}'",
          result.partitions().keySet(),
          timelineId,
          result.restorePoint().checkpointId(),
          result.timelineId());
      return result;
    } catch (final RuntimeException e) {
      LOG.error("Failed to restore from the log archive. Deleting the restored data", e);
      FileUtil.deleteFolderContents(dataDirectory);
      throw e;
    } finally {
      registries.forEach(entry -> MicrometerUtil.close(entry.getValue()));
      store.closeAsync().join();
    }
  }
}
