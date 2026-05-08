/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.SystemPartitionCfg;
import io.camunda.zeebe.db.impl.rocksdb.ChecksumProviderRocksDBImpl;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.systempartition.SystemPartitionFactory;
import io.camunda.zeebe.systempartition.SystemPartitionStateMachine;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;

/**
 * Bootstraps the system partition's Raft replica and state machine.
 *
 * <p>Skipped when {@code experimental.systemPartition.enabled = false} (the default). When the flag
 * is on:
 *
 * <ol>
 *   <li>Resolve the static membership: lowest-{@code nodeId} brokers, count = {@code
 *       systemPartition.replicationFactor} (or {@code cluster.replicationFactor} if 0).
 *   <li>Create a {@link FileBasedSnapshotStore} for the partition directory under {@code
 *       {dataDir}/system/partitions/1/} and submit it to the scheduler.
 *   <li>Build the {@link RaftPartition} and bootstrap it (only on members; non-members get a no-op
 *       state machine that always reports "not leader").
 *   <li>Submit the {@link SystemPartitionStateMachine} actor and store the {@code SystemPartition}
 *       facade on the {@link BrokerStartupContext} for downstream steps.
 * </ol>
 */
public final class SystemPartitionStep implements StartupStep<BrokerStartupContext> {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  public String getName() {
    return "System Partition";
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(final BrokerStartupContext context) {
    final BrokerCfg cfg = context.getBrokerConfiguration();
    final SystemPartitionCfg sysCfg = cfg.getExperimental().getSystemPartition();

    if (!sysCfg.isEnabled()) {
      LOG.debug("System partition is disabled; skipping system partition bootstrap.");
      return CompletableActorFuture.completed(context);
    }

    final CompletableActorFuture<BrokerStartupContext> result = new CompletableActorFuture<>();

    try {
      final Set<MemberId> clusterMembers = staticClusterMembers(cfg);
      final int requestedRf = sysCfg.getReplicationFactor();
      final int rf =
          requestedRf == SystemPartitionCfg.REPLICATION_FACTOR_MATCH_DATA
              ? cfg.getCluster().getReplicationFactor()
              : requestedRf;
      final Set<MemberId> systemMembers =
          SystemPartitionFactory.resolveSystemPartitionMembers(clusterMembers, rf);
      final PartitionMetadata metadata =
          SystemPartitionFactory.buildPartitionMetadata(systemMembers);

      final Path dir = SystemPartitionFactory.getPartitionDirectory(cfg.getData().getDirectory());
      FileUtil.ensureDirectoryExists(dir);

      LOG.info(
          "Bootstrapping system partition with members {} (replicationFactor={}); data dir={}",
          systemMembers,
          rf,
          dir);

      // Scope a partition-tagged registry for the system partition so that meters created by
      // FileBasedSnapshotStore + RaftPartition (e.g. zeebe_snapshot_duration_seconds) share the
      // same tag-key set ([bootstrap, partition]) as data partitions — Prometheus requires that.
      // We use a non-numeric value ("system-1") so it doesn't collide with data partition values
      // (which are integer partition ids).
      final MeterRegistry partitionRegistry =
          MicrometerUtil.wrap(
              context.getMeterRegistry(),
              Tags.of(
                  "partition",
                  SystemPartitionFactory.GROUP_NAME
                      + "-"
                      + SystemPartitionFactory.SYSTEM_PARTITION_ID));

      final FileBasedSnapshotStore snapshotStore =
          new FileBasedSnapshotStore(
              cfg.getCluster().getNodeId(),
              SystemPartitionFactory.SYSTEM_PARTITION_ID,
              dir,
              new ChecksumProviderRocksDBImpl(),
              partitionRegistry);

      context
          .getActorSchedulingService()
          .submitActor(snapshotStore, SchedulingHints.ioBound())
          .onComplete(
              (snapshotStarted, snapshotErr) -> {
                if (snapshotErr != null) {
                  result.completeExceptionally(snapshotErr);
                  return;
                }
                bootstrapRaft(context, metadata, dir, snapshotStore, partitionRegistry, result);
              });
    } catch (final IOException e) {
      result.completeExceptionally(new UncheckedIOException(e));
    }
    return result;
  }

  private void bootstrapRaft(
      final BrokerStartupContext context,
      final PartitionMetadata metadata,
      final Path dir,
      final FileBasedSnapshotStore snapshotStore,
      final MeterRegistry partitionRegistry,
      final CompletableActorFuture<BrokerStartupContext> result) {
    try {
      final RaftPartition partition =
          SystemPartitionFactory.createRaftPartition(
              metadata, dir, 32L * 1024 * 1024, 1024L * 1024 * 1024, partitionRegistry);

      final var managementService =
          new DefaultPartitionManagementService(
              context.getClusterServices().getMembershipService(),
              context.getClusterServices().getCommunicationService());

      partition
          .bootstrap(managementService, snapshotStore)
          .whenComplete(
              (raft, raftErr) -> {
                if (raftErr != null) {
                  result.completeExceptionally(raftErr);
                  return;
                }
                final SystemPartitionStateMachine stateMachine =
                    new SystemPartitionStateMachine(partition);
                context
                    .getActorSchedulingService()
                    .submitActor(stateMachine)
                    .onComplete(
                        (stateMachineStarted, stateMachineErr) -> {
                          if (stateMachineErr != null) {
                            result.completeExceptionally(stateMachineErr);
                            return;
                          }
                          context.setSystemPartition(stateMachine);
                          result.complete(context);
                        });
              });
    } catch (final Exception e) {
      result.completeExceptionally(e);
    }
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(final BrokerStartupContext context) {
    // For hackday scope: rely on broker actor scheduler shutdown to clean up. A future revision
    // should explicitly close the state machine, RaftPartition, and snapshot store in reverse
    // order, mirroring SnapshotStoreStep.
    context.setSystemPartition(null);
    return CompletableActorFuture.completed(context);
  }

  private static Set<MemberId> staticClusterMembers(final BrokerCfg cfg) {
    final int clusterSize = cfg.getCluster().getClusterSize();
    return IntStream.range(0, clusterSize)
        .mapToObj(id -> MemberId.from(Integer.toString(id)))
        .collect(Collectors.toSet());
  }
}
