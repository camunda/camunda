/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.restore.PartitionRestoreService;
import io.camunda.zeebe.restore.ValidatePartitionCount;
import io.camunda.zeebe.restore.validation.RestoreValidator;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A limited {@link RecoveryPartitionManager} used when the cluster is in recovery mode. Only a
 * restricted set of resources is available.
 *
 * <p>Each local partition is started through a sequence of {@link
 * io.camunda.zeebe.scheduler.startup.StartupStep}s encapsulated in {@link RecoveryPartition},
 * mirroring the normal {@link Partition} bootstrapping approach.
 */
public final class RecoveryPartitionManager
    implements PartitionManager,
        PartitionChangeExecutor,
        PartitionScalingChangeExecutor,
        RestoreChangeExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(RecoveryPartitionManager.class);

  // Dedicated to the ephemeral open-and-close sanity check after restore: the restored partition
  // is not yet running as a ZeebePartition, so there is no shared factory/resources to reuse here.
  private static final ZeebeRocksDbFactory<?> SANITY_CHECK_DB_FACTORY =
      new ZeebeRocksDbFactory<>(
          new RocksDbConfiguration(),
          new ConsistencyChecksSettings(true, true),
          new AccessMetricsConfiguration(AccessMetricsConfiguration.Kind.NONE),
          SimpleMeterRegistry::new);

  private final List<RecoveryPartition> recoveryPartitions = new ArrayList<>();
  private final List<Integer> failedPartitionIds = new ArrayList<>();
  private final String partitionGroup;
  private final ConcurrencyControl concurrencyControl;
  private final ActorSchedulingService actorSchedulingService;
  private final ClusterConfigurationService clusterConfigurationService;
  private final ClusterMembershipService membershipService;
  private final TopologyManagerImpl topologyManager;
  private final MeterRegistry meterRegistry;
  private final BrokerCfg brokerCfg;
  private final BrokerInfo brokerInfo;
  private final AtomixServerTransport gatewayBrokerTransport;
  private final @Nullable IntFunction<Long> exportedPositionSupplier;
  private @Nullable BackupStore backupStore;
  private @Nullable ExecutorService restoreExecutor;

  public RecoveryPartitionManager(
      final String partitionGroup,
      final BrokerCfg brokerCfg,
      final BrokerInfo brokerInfo,
      final ConcurrencyControl concurrencyControl,
      final ClusterConfigurationService clusterConfigurationService,
      final ClusterMembershipService membershipService,
      final ActorSchedulingService schedulingService,
      final MeterRegistry meterRegistry,
      final AtomixServerTransport gatewayBrokerTransport,
      final @Nullable IntFunction<Long> exportedPositionSupplier,
      final TopologyManagerImpl topologyManager) {
    this.partitionGroup = partitionGroup;
    this.concurrencyControl = concurrencyControl;
    actorSchedulingService = schedulingService;
    this.clusterConfigurationService = clusterConfigurationService;
    this.topologyManager = topologyManager;
    this.meterRegistry = meterRegistry;
    this.membershipService = membershipService;
    this.brokerCfg = brokerCfg;
    this.brokerInfo = brokerInfo;
    this.gatewayBrokerTransport = gatewayBrokerTransport;
    this.exportedPositionSupplier = exportedPositionSupplier;
  }

  @Override
  public @Nullable RaftPartition getRaftPartition(final int partitionId) {
    return null;
  }

  @Override
  public Collection<RaftPartition> getRaftPartitions() {
    return List.of();
  }

  @Override
  public Collection<ZeebePartition> getZeebePartitions() {
    return Collections.emptyList();
  }

  @Override
  public ActorFuture<Void> start() {
    LOG.info("Recovering partitions for partition group {}", partitionGroup);
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (DEFAULT_GROUP_NAME.equals(partitionGroup)) {
            clusterConfigurationService.registerPartitionChangeExecutors(this, this, this);
          }
          startInternal(result);
        });
    return result;
  }

  @Override
  public ActorFuture<Void> stop() {
    LOG.info("Stopping RecoveryPartitionManager");
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (DEFAULT_GROUP_NAME.equals(partitionGroup)) {
            clusterConfigurationService.removePartitionChangeExecutor();
          }
          clusterConfigurationService.removeRequestValidator(partitionGroup, RestoreRequest.class);
          stopInternal(result);
        });
    return result;
  }

  private void startInternal(final ActorFuture<Void> result) {
    restoreExecutor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("zeebe-restore-", 0).factory());
    final var localPartitions = localPartitions();
    if (localPartitions.isEmpty()) {
      LOG.info("No local partitions to recover for partition group {}", partitionGroup);
      result.complete(null);
      return;
    }

    final var backupCfg = brokerCfg.getData().getBackup();
    try {
      backupStore = BackupCfg.BackupStoreFactory.createStore(backupCfg);
    } catch (final Exception e) {
      LOG.error("Failed to create backup store for partition group {}", partitionGroup, e);
      result.completeExceptionally(e);
      return;
    }

    final var partitionCount =
        clusterConfigurationService.getCurrentClusterConfiguration().partitionCount();
    clusterConfigurationService.registerRequestValidator(
        partitionGroup,
        new RestoreValidator(partitionCount, backupStore, exportedPositionSupplier));

    final var startFutures = new ArrayList<ActorFuture<Void>>();
    for (final var partitionMetadata : localPartitions) {
      LOG.info("Recovering partition {}", partitionMetadata.id());
      final var partition = RecoveryPartition.recovering(startupContext(partitionMetadata));
      startFutures.add(startPartition(partition));
    }

    final var startAll =
        startFutures.stream().collect(new ActorFutureCollector<>(concurrencyControl));
    concurrencyControl.runOnCompletion(
        startAll,
        (ignored, startError) -> {
          if (startError != null) {
            LOG.warn(
                "Recovered {}/{} partitions for partition group {}",
                recoveryPartitions.size(),
                localPartitions.size(),
                partitionGroup);
          }
          final var deactivateFutures =
              localPartitions.stream()
                  .map(p -> topologyManager.setInactive(p.id().number()))
                  .collect(new ActorFutureCollector<>(concurrencyControl));
          concurrencyControl.runOnCompletion(
              deactivateFutures,
              (ignoredDeactivate, deactivateError) -> {
                // reported once the partition is registered as INACTIVE above, since
                // onHealthChanged is a no-op for a partition the topology doesn't know about yet
                recoveryPartitions.forEach(
                    p ->
                        topologyManager.onHealthChanged(
                            p.partitionId().number(), HealthStatus.HEALTHY));
                failedPartitionIds.forEach(
                    id -> topologyManager.onHealthChanged(id, HealthStatus.DEAD));
                if (recoveryPartitions.isEmpty()) {
                  if (deactivateError != null) {
                    LOG.error(
                        "Failed to deactivate local partitions for partition group {} after all"
                            + " partitions failed to recover",
                        partitionGroup,
                        deactivateError);
                  }
                  concurrencyControl.runOnCompletion(
                      closeBackupStore(),
                      (ignoredClose, ignoredCloseError) ->
                          result.completeExceptionally(
                              new IllegalStateException("No partitions recovered", startError)));
                } else if (deactivateError != null) {
                  result.completeExceptionally(deactivateError);
                } else {
                  result.complete(null);
                }
              });
        });
  }

  private RecoveryPartitionStartupContext startupContext(
      final PartitionMetadata partitionMetadata) {
    final var partitionId = partitionMetadata.id();
    final var partitionDir = partitionDirectory(partitionId);
    return new RecoveryPartitionStartupContext(
        partitionId,
        partitionDir,
        actorSchedulingService,
        topologyManager,
        meterRegistry,
        concurrencyControl,
        brokerCfg,
        brokerInfo,
        gatewayBrokerTransport,
        backupStore);
  }

  private void stopInternal(final ActorFuture<Void> result) {
    final var stopFutures =
        recoveryPartitions.stream()
            .map(RecoveryPartition::stop)
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        stopFutures,
        (ignored, stopError) -> {
          recoveryPartitions.clear();
          if (restoreExecutor != null) {
            restoreExecutor.shutdownNow();
            restoreExecutor = null;
          }
          if (stopError != null) {
            LOG.error("Failed to stop recovery partitions", stopError);
          }
          concurrencyControl.runOnCompletion(
              closeBackupStore(),
              (ignoredClose, ignoredCloseError) -> {
                if (stopError != null) {
                  result.completeExceptionally(stopError);
                } else {
                  result.complete(null);
                }
              });
        });
  }

  private Path partitionDirectory(final PartitionId partitionId) {
    final var dataDirectory = brokerCfg.getData().getDirectory();
    return RaftPartitionFactory.getPartitionDirectory(partitionId, dataDirectory);
  }

  @Override
  public ActorFuture<Void> preRestore(final int partitionId) {
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          final var executor = restoreExecutor;
          if (executor == null) {
            result.completeExceptionally(
                new IllegalStateException("RecoveryPartitionManager is not started"));
            return;
          }
          final var partitionDir = partitionDirectory(new PartitionId(partitionGroup, partitionId));
          CompletableFuture.runAsync(() -> deleteDirectory(partitionDir), executor)
              .whenCompleteAsync(
                  (ok, error) -> {
                    if (error != null) {
                      result.completeExceptionally(unwrapCompletionException(error));
                    } else {
                      LOG.info("Dropped local data of partition {} for restore", partitionId);
                      result.complete(null);
                    }
                  },
                  concurrencyControl);
        });
    return result;
  }

  private static void deleteDirectory(final Path directory) {
    try {
      if (Files.exists(directory)) {
        FileUtil.deleteFolderContents(directory);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to delete directory " + directory, e);
    }
  }

  @Override
  public ActorFuture<Void> restore(final int partitionId, final SortedSet<Long> backupIds) {
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          final var executor = restoreExecutor;
          if (executor == null) {
            result.completeExceptionally(
                new IllegalStateException("RecoveryPartitionManager is not started"));
            return;
          }
          final var store = backupStore;
          if (store == null) {
            result.completeExceptionally(
                new IllegalStateException(
                    "No backup store available to restore partition " + partitionId));
            return;
          }
          final var metadata =
              localPartitions().stream()
                  .filter(p -> p.id().number() == partitionId)
                  .findFirst()
                  .orElse(null);
          if (metadata == null) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Cannot restore partition %d, it is not a local partition of group %s"
                        .formatted(partitionId, partitionGroup)));
            return;
          }
          final var ids = backupIds.stream().mapToLong(Long::longValue).toArray();
          final var partitionDir = partitionDirectory(metadata.id());

          CompletableFuture.runAsync(() -> restorePartition(metadata, store, ids), executor)
              .thenRunAsync(() -> verifyRestoredPartition(metadata), executor)
              .whenCompleteAsync(
                  (ok, error) -> {
                    if (error != null) {
                      LOG.error(
                          "Failed to restore partition {}, dropping partial data so the"
                              + " operation can be retried",
                          partitionId,
                          error);
                      try {
                        deleteDirectory(partitionDir);
                      } catch (final Exception cleanupError) {
                        error.addSuppressed(cleanupError);
                      }
                      result.completeExceptionally(unwrapCompletionException(error));
                    } else {
                      LOG.info("Restored partition {} from backups {}", partitionId, backupIds);
                      result.complete(null);
                    }
                  },
                  concurrencyControl);
        });
    return result;
  }

  private void restorePartition(
      final PartitionMetadata metadata, final BackupStore store, final long[] backupIds) {
    final var registry = MicrometerUtil.wrap(meterRegistry, PartitionKeyNames.tags(metadata.id()));
    final var factory = new RaftPartitionFactory(brokerCfg);
    try {
      final var restoreService =
          new PartitionRestoreService(
              store,
              factory.createRaftPartition(metadata, registry),
              brokerInfo.getNodeId(),
              new RocksDBSnapshotFileInfoProvider(),
              registry);
      restoreService.restore(
          backupIds, new ValidatePartitionCount(brokerCfg.getCluster().getPartitionsCount()));
    } catch (final Exception e) {
      throw new CompletionException("Failed to restore partition %s".formatted(metadata.id()), e);
    } finally {
      MicrometerUtil.close(registry);
    }
  }

  /** Blocks the calling thread; must only run on {@link #restoreExecutor}. */
  private void verifyRestoredPartition(final PartitionMetadata metadata) {
    final var partitionDir = partitionDirectory(metadata.id());
    if (!directoryHasEntries(partitionDir)) {
      throw new IllegalStateException(
          "Expected restored partition %s to have data, but directory %s is empty or missing"
              .formatted(metadata.id(), partitionDir));
    }
    verifyRestoredRocksDb(metadata);
  }

  private static boolean directoryHasEntries(final Path directory) {
    final var files = directory.toFile().listFiles();
    return directory.toFile().isDirectory() && files != null && files.length > 0;
  }

  /**
   * Opens the restored partition's latest snapshot, if any, through {@link
   * ZeebeRocksDbFactory#openSnapshotOnlyDb(java.io.File)} and immediately closes it. RocksDB
   * validates the on-disk SST files as part of opening (paranoid checks), so this surfaces
   * corruption that {@link #directoryHasEntries} cannot detect.
   */
  private void verifyRestoredRocksDb(final PartitionMetadata metadata) {
    final var partitionDir = partitionDirectory(metadata.id());
    final var snapshotStore =
        new FileBasedSnapshotStore(
            brokerInfo.getNodeId(),
            metadata.id(),
            partitionDir,
            new RocksDBSnapshotFileInfoProvider(),
            meterRegistry);
    try {
      actorSchedulingService.submitActor(snapshotStore, SchedulingHints.ioBound()).join();
      // getLatestSnapshot() is a plain getter, not dispatched onto the store's actor; without
      // this barrier it can race ahead of the store's own startup job, which populates it from
      // disk. getAvailableSnapshots() runs via actor.call(), so joining it first forces correct
      // ordering.
      snapshotStore.getAvailableSnapshots().join();
      verifyRocksDbOpens(metadata.id(), snapshotStore, partitionDir);
    } finally {
      snapshotStore.closeAsync();
    }
  }

  private void verifyRocksDbOpens(
      final PartitionId partitionId,
      final FileBasedSnapshotStore snapshotStore,
      final Path partitionDir) {
    final var snapshot = snapshotStore.getLatestSnapshot();
    if (snapshot.isEmpty()) {
      if (hasSnapshotDirectoryEntries(partitionDir)) {
        // The store itself already checksums every candidate on load (see
        // FileBasedSnapshotStoreImpl#collectSnapshot) and silently excludes anything that
        // doesn't match from getLatestSnapshot(). An empty result despite files on disk means
        // every candidate failed that check, i.e. the restored snapshot is corrupted.
        throw new IllegalStateException(
            "Restored partition %s has snapshot files on disk that failed checksum validation; "
                    .formatted(partitionId)
                + "the restored data is likely corrupted");
      }
      // Nothing was ever flushed to a snapshot (e.g. restored purely from the log); nothing to
      // verify here.
      return;
    }
    final Path scratchRoot;
    try {
      scratchRoot = Files.createTempDirectory("restore-sanity-check-");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    final var runtimeDir = scratchRoot.resolve("runtime");
    try {
      try (final var snapshotOnlyDb =
          SANITY_CHECK_DB_FACTORY.openSnapshotOnlyDb(snapshot.get().getPath().toFile())) {
        snapshotOnlyDb.createSnapshot(runtimeDir.toFile());
      }
      // The snapshot-only open above only parses the manifest; a full read-write open of the
      // copy is what actually validates every on-disk SST file, surfacing corruption the
      // earlier open does not (mirrors StateControllerImpl.recoverFromSnapshot() + openDb()).
      SANITY_CHECK_DB_FACTORY.createDb(runtimeDir.toFile()).close();
    } catch (final Exception e) {
      throw new IllegalStateException(
          "RocksDB sanity check failed for restored partition %s".formatted(partitionId), e);
    } finally {
      try {
        FileUtil.deleteFolderIfExists(scratchRoot);
      } catch (final IOException e) {
        LOG.warn("Failed to delete RocksDB sanity check scratch directory {}", scratchRoot, e);
      }
    }
  }

  private static boolean hasSnapshotDirectoryEntries(final Path partitionDir) {
    final var snapshotsDir = partitionDir.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY);
    if (!Files.isDirectory(snapshotsDir)) {
      return false;
    }
    try (final var entries = Files.list(snapshotsDir)) {
      return entries.findAny().isPresent();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Throwable unwrapCompletionException(final Throwable error) {
    return error instanceof CompletionException && error.getCause() != null
        ? error.getCause()
        : error;
  }

  private MemberId localMemberId() {
    return membershipService.getLocalMember().id();
  }

  /**
   * Resolves the partitions of this partition group that the local broker is a member of, according
   * to the current partition distribution.
   */
  private List<PartitionMetadata> localPartitions() {
    final var localMemberId = localMemberId();
    // The default physical tenant's partition distribution is the only one stored in dynamic
    // config; other physical tenants derive their distribution by rewriting the group on every
    // PartitionId.
    return clusterConfigurationService
        .getPartitionDistribution()
        .withGroupName(partitionGroup)
        .partitions()
        .stream()
        .filter(p -> p.members().contains(localMemberId))
        .toList();
  }

  private ActorFuture<Void> closeBackupStore() {
    final var closed = concurrencyControl.<Void>createFuture();
    if (backupStore == null) {
      closed.complete(null);
      return closed;
    }
    final var store = backupStore;
    backupStore = null;
    store
        .closeAsync()
        .whenCompleteAsync(
            (ignore, closeError) -> {
              if (closeError != null) {
                LOG.error("Failed to close backup store", closeError);
              }
              closed.complete(null);
            },
            concurrencyControl);
    return closed;
  }

  private ActorFuture<Void> startPartition(final RecoveryPartition partition) {
    return partition
        .start()
        .andThen(
            (started, startError) -> {
              if (startError == null) {
                recoveryPartitions.add(started);
                return CompletableActorFuture.completed();
              }
              LOG.error(
                  "Failed to start recovery partition for {}, stopping it",
                  partition.partitionId(),
                  startError);
              failedPartitionIds.add(partition.partitionId().number());
              return partition
                  .stop()
                  .andThen(
                      (ignored, stopError) -> {
                        if (stopError != null) {
                          LOG.error(
                              "Failed to stop partially started recovery partition for {}",
                              partition.partitionId(),
                              stopError);
                        }
                        return CompletableActorFuture.<Void>completedExceptionally(startError);
                      },
                      concurrencyControl);
            },
            concurrencyControl);
  }

  @Override
  public ActorFuture<Void> join(
      final int partitionId,
      final Map<MemberId, Integer> membersWithPriority,
      final DynamicPartitionConfig partitionConfig) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform join on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> leave(final int partitionId) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform leave on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> bootstrap(
      final int partitionId,
      final int priority,
      final DynamicPartitionConfig partitionConfig,
      final boolean initializeFromConfig) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform bootstrap on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> reconfigurePriority(final int partitionId, final int newPriority) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform reconfigurePriority on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> forceReconfigure(
      final int partitionId, final Collection<MemberId> members) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform forceReconfigure on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> disableExporter(final int partitionId, final String exporterId) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform disableExporter on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> deleteExporter(final int partitionId, final String exporterId) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform deleteExporter on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> enableExporter(
      final int partitionId,
      final String exporterId,
      final long metadataVersion,
      final String initializeFrom) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform enableExporter on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> setExportingState(final ExportingState exportingState) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform setExportingState on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> initiateScaleUp(final int desiredPartitionCount) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform scaleUp on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> awaitRedistributionCompletion(
      final int desiredPartitionCount,
      final Set<Integer> redistributedPartitions,
      final Duration timeout) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException(
            "Cannot perform awaitRedistributionCompletion on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> notifyPartitionBootstrapped(final int partitionId) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException(
            "Cannot perform notifyPartitionBootstrapped on a recovering partition"));
  }

  @Override
  public ActorFuture<RoutingState> getRoutingState() {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform getRoutingState on a recovering partition"));
  }
}
