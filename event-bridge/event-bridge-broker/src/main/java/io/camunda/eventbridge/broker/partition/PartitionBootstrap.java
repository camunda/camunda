/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partition;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.camunda.eventbridge.broker.actor.PollActor;
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.broker.topology.TopologyBroadcaster;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps all Event Bridge RAFT partitions on startup and shuts them down on stop.
 *
 * <p>For each configured partition this class:
 *
 * <ol>
 *   <li>Creates a {@link FileBasedSnapshotStore} and submits it to the I/O actor scheduler.
 *   <li>Creates an {@link EventBridgePartition} (which wraps a {@link
 *       io.atomix.raft.partition.RaftPartition}) and calls {@code bootstrap()} on it.
 *   <li>Registers a {@link io.atomix.raft.RaftRoleChangeListener} via the partition so that {@link
 *       PublishActor#connect}/{@link PublishActor#disconnect} are called on every leader
 *       transition, and the {@link TopologyBroadcaster} keeps SWIM properties up-to-date.
 * </ol>
 *
 * <p>Bootstrap is asynchronous; RAFT leader election and initial log catch-up proceed in the
 * background. Once a partition elects a leader the role-change callback fires and the corresponding
 * {@link PublishActor} becomes operational.
 *
 * <p><strong>Thread safety:</strong> {@link #start()} and {@link #stop()} must be called from the
 * same thread (the Spring lifecycle thread). The partitions list is protected by that contract and
 * by a volatile check in {@link #start()} to guard against double-starts.
 */
public final class PartitionBootstrap {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionBootstrap.class);

  /** Timeout (seconds) to wait for each partition to close gracefully on shutdown. */
  private static final int CLOSE_TIMEOUT_SECONDS = 30;

  private final AtomixCluster cluster;
  private final ActorScheduler actorScheduler;
  private final EventBridgeProperties properties;
  private final Map<Integer, PublishActor> publishActors;
  private final Map<Integer, PollActor> pollActors;
  private final MeterRegistry meterRegistry;
  private final EventBridgePartitionFactory partitionFactory;

  /** Holds all successfully bootstrapped partitions so they can be closed on {@link #stop()}. */
  private final List<EventBridgePartition> partitions = new CopyOnWriteArrayList<>();

  private volatile boolean started = false;

  public PartitionBootstrap(
      final AtomixCluster cluster,
      final ActorScheduler actorScheduler,
      final EventBridgeProperties properties,
      final Map<Integer, PublishActor> publishActors,
      final Map<Integer, PollActor> pollActors,
      final MeterRegistry meterRegistry) {
    this.cluster = cluster;
    this.actorScheduler = actorScheduler;
    this.properties = properties;
    this.publishActors = publishActors;
    this.pollActors = pollActors;
    this.meterRegistry = meterRegistry;
    this.partitionFactory = new EventBridgePartitionFactory(properties);
  }

  /**
   * Bootstraps all partitions. Idempotent: a second call while already started is a no-op.
   *
   * <p>Each partition is bootstrapped asynchronously; this method returns before RAFT has elected a
   * leader.
   */
  public void start() {
    if (started) {
      LOG.debug("PartitionBootstrap.start() called while already started; ignoring");
      return;
    }
    started = true;

    final var membershipService = cluster.getMembershipService();
    final var managementService =
        new DefaultPartitionManagementService(membershipService, cluster.getCommunicationService());

    final var localMember = membershipService.getLocalMember();
    final var localMemberId = localMember.id();
    final var allMembers = membershipService.getMembers();
    final int replicationFactor = properties.raft().replicationFactor();
    final Set<MemberId> raftMembers = buildMemberSet(localMemberId, allMembers, replicationFactor);

    // Create the broadcaster that publishes leadership changes into SWIM member properties.
    final var topologyBroadcaster = new TopologyBroadcaster(localMember, localMemberId.id());

    // The coordinator broker advertises its identity once at startup; this is a static property
    // that does not change with partition leadership transitions.
    final boolean isCoordinator = properties.coordinator().brokerId().equals(localMemberId.id());
    if (isCoordinator) {
      topologyBroadcaster.advertiseCoordinator();
    }

    final int partitionCount = properties.broker().partitionCount();
    LOG.info(
        "Bootstrapping {} Event Bridge partition(s) with replication factor {} "
            + "(local member: {}, raft members: {}, isCoordinator: {})",
        partitionCount,
        replicationFactor,
        localMemberId,
        raftMembers,
        isCoordinator);

    for (int partitionId = 0; partitionId < partitionCount; partitionId++) {
      bootstrapPartition(
          partitionId, raftMembers, localMemberId, managementService, topologyBroadcaster);
    }
  }

  /**
   * Closes all bootstrapped partitions, waiting up to {@value #CLOSE_TIMEOUT_SECONDS} seconds per
   * partition.
   */
  public void stop() {
    if (!started) {
      return;
    }
    LOG.info("Stopping {} Event Bridge partition(s)", partitions.size());

    final var snapshot = new ArrayList<>(partitions);
    partitions.clear();

    for (final var partition : snapshot) {
      try {
        partition.close().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (final Exception e) {
        LOG.warn("Error while closing partition {}", partition.getRaftPartition().id(), e);
      }
    }
    started = false;
  }

  // -------------------------------------------------------------------------
  // Private helpers

  private void bootstrapPartition(
      final int partitionId,
      final Set<MemberId> members,
      final MemberId localMemberId,
      final DefaultPartitionManagementService managementService,
      final TopologyBroadcaster topologyBroadcaster) {

    // FileBasedSnapshotStore manages its own snapshots/ and pending-snapshots/ subdirectories
    // internally; pass the partition root directory (same convention as SnapshotStoreStep).
    final var partitionDir = partitionFactory.getPartitionDirectory(partitionId);
    try {
      FileUtil.ensureDirectoryExists(partitionDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to create data directory for partition " + partitionId, e);
    }

    final var publishActor = publishActors.get(partitionId);
    if (publishActor == null) {
      LOG.error(
          "No PublishActor registered for partition {}; skipping RAFT bootstrap for that partition",
          partitionId);
      return;
    }

    final var pollActor = pollActors.get(partitionId);
    if (pollActor == null) {
      LOG.error(
          "No PollActor registered for partition {}; skipping RAFT bootstrap for that partition",
          partitionId);
      return;
    }

    final var snapshotStore =
        new FileBasedSnapshotStore(
            0 /* brokerId – single logical broker in standalone mode */,
            partitionId,
            partitionDir,
            new EventBridgeChecksumProvider(),
            meterRegistry);

    actorScheduler
        .submitActor(snapshotStore, SchedulingHints.ioBound())
        .onComplete(
            (ignored, snapshotErr) -> {
              if (snapshotErr != null) {
                LOG.error(
                    "Failed to start snapshot store for partition {}", partitionId, snapshotErr);
                return;
              }
              final var raftPartition =
                  partitionFactory.createPartition(
                      partitionId, members, localMemberId, partitionDir, meterRegistry);

              final var partition =
                  new EventBridgePartition(
                      partitionId, raftPartition, publishActor, pollActor, topologyBroadcaster);
              partitions.add(partition);

              raftPartition
                  .bootstrap(managementService, snapshotStore)
                  .whenComplete(
                      (rp, bootstrapErr) -> {
                        if (bootstrapErr != null) {
                          LOG.error(
                              "Failed to bootstrap RAFT partition {}", partitionId, bootstrapErr);
                        } else {
                          LOG.info("RAFT partition {} bootstrap complete", partitionId);
                        }
                      });
            },
            Runnable::run);
  }

  /**
   * Builds the RAFT member set for a partition.
   *
   * <p>The local member is always included. Additional members are taken from {@code allMembers}
   * (in iteration order) until the set reaches {@code replicationFactor}. If fewer than {@code
   * replicationFactor} members are currently known, all known members are used.
   */
  private static Set<MemberId> buildMemberSet(
      final MemberId localMemberId, final Set<Member> allMembers, final int replicationFactor) {

    // LinkedHashSet preserves insertion order: local member first.
    final var result = new LinkedHashSet<MemberId>(replicationFactor);
    result.add(localMemberId);
    for (final var member : allMembers) {
      if (result.size() >= replicationFactor) {
        break;
      }
      // Skip the local member — already added above; getMembers() includes the local node.
      if (!member.id().equals(localMemberId)) {
        result.add(member.id());
      }
    }
    return Collections.unmodifiableSet(result);
  }
}
