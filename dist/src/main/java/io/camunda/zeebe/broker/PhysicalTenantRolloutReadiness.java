/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Decides when the rollout of a tenant's configuration may go on from this member, so that a
 * rolling restart never takes a quorum of a partition's replicas down at once.
 *
 * <ul>
 *   <li>Before this member restarts the tenant, every partition of the tenant it replicates must
 *       keep a quorum of healthy replicas on the other members, as the cluster's topology shows
 *       them.
 *   <li>After the restart, every local partition of the tenant must be installed again, be leader
 *       or follower of a partition with a known leader, and report itself healthy. A partition
 *       paused because the tenant is out of disk space counts as ready: it only resumes once space
 *       is freed or the tenant's limit is raised, which may be the next change to roll out.
 * </ul>
 *
 * <p>Both waits are bounded; a wait that runs out fails the member's step of the rollout, which
 * dynamic-config retries.
 */
@NullMarked
final class PhysicalTenantRolloutReadiness {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

  private final Broker broker;
  private final PhysicalTenantLifecycleService lifecycle;
  private final Supplier<@Nullable BrokerTopologyManager> topology;
  private final MemberId localMemberId;
  private final Mode mode;
  private final Duration timeout;

  PhysicalTenantRolloutReadiness(
      final Broker broker,
      final PhysicalTenantLifecycleService lifecycle,
      final Supplier<@Nullable BrokerTopologyManager> topology,
      final MemberId localMemberId,
      final Mode mode,
      final Duration timeout) {
    this.broker = broker;
    this.lifecycle = lifecycle;
    this.topology = topology;
    this.localMemberId = localMemberId;
    this.mode = mode;
    this.timeout = timeout;
  }

  Mode mode() {
    return mode;
  }

  Duration timeout() {
    return timeout;
  }

  /**
   * Waits until this member may restart the tenant without leaving one of its partitions without a
   * quorum of healthy replicas.
   *
   * @param waiting told why the rollout waits, while it does
   * @throws NotReadyException if that is not the case within the timeout
   */
  void awaitQuorumElsewhere(final String physicalTenantId, final Consumer<String> waiting) {
    if (mode != Mode.SERVING) {
      return;
    }
    poll(() -> quorumMissingElsewhere(physicalTenantId), waiting);
  }

  /**
   * Waits until the restarted tenant serves on this member again.
   *
   * @param waiting told why the rollout waits, while it does
   * @throws NotReadyException if it does not within the timeout
   */
  void awaitReady(final String physicalTenantId, final Consumer<String> waiting) {
    if (mode == Mode.NONE) {
      return;
    }
    final var deadline = System.nanoTime() + timeout.toNanos();
    waiting.accept("its partitions are not installed yet");
    try {
      lifecycle.awaitReady(physicalTenantId).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (final ExecutionException | TimeoutException e) {
      throw new NotReadyException("its partitions were not installed within " + timeout, e);
    }
    if (mode == Mode.SERVING) {
      pollUntil(deadline, () -> notServing(physicalTenantId), waiting);
    }
  }

  /** Why the tenant does not serve on this member, or empty if it does. */
  Optional<String> notServing(final String physicalTenantId) {
    final var context = broker.getBrokerContext();
    final var partitions =
        context == null ? null : context.getPartitionManagers().get(physicalTenantId);
    if (partitions == null) {
      return Optional.of("its partition manager does not run");
    }
    for (final var raft : partitions.getRaftPartitions()) {
      final var role = raft.getRole();
      if (role != Role.LEADER && role != Role.FOLLOWER) {
        return Optional.of("partition %d is %s".formatted(raft.id().number(), role));
      }
      if (role == Role.FOLLOWER && raft.getServer().getServer().getContext().getLeader() == null) {
        return Optional.of("partition %d knows no leader".formatted(raft.id().number()));
      }
    }
    for (final var partition : partitions.getZeebePartitions()) {
      final var health = partition.getHealthReport();
      if (health.getStatus() != HealthStatus.HEALTHY && partition.isDiskSpaceAvailable()) {
        return Optional.of(
            "partition %d is %s: %s"
                .formatted(partition.getPartitionId(), health.getStatus(), health.getIssue()));
      }
    }
    return Optional.empty();
  }

  /**
   * Which local partition of the tenant would lose its quorum if this member restarted it now, or
   * empty if none would. A replica counts as healthy when the topology shows it as leader or
   * follower and its broker reports it healthy.
   */
  Optional<String> quorumMissingElsewhere(final String physicalTenantId) {
    final var manager = topology.get();
    if (manager == null) {
      return Optional.empty();
    }
    final @Nullable PartitionGroupConfiguration group =
        manager.getClusterConfiguration().partitionGroup(physicalTenantId);
    if (group == null) {
      return Optional.empty();
    }
    final var local = group.getMember(localMemberId);
    if (local == null) {
      return Optional.empty();
    }
    final var state = manager.getTopology(physicalTenantId);
    for (final var partitionId : local.partitions().keySet()) {
      final var replicas =
          group.members().values().stream()
              .filter(member -> member.hasPartition(partitionId))
              .count();
      final var needed = Math.min(replicas / 2 + 1, replicas - 1);
      final var leader = state.getLeaderForPartition(partitionId);
      var healthy = 0;
      if (leader != null && isHealthyElsewhere(state, leader, partitionId)) {
        healthy++;
      }
      for (final var follower : state.getFollowersForPartition(partitionId)) {
        if (isHealthyElsewhere(state, follower, partitionId)) {
          healthy++;
        }
      }
      if (healthy < needed) {
        return Optional.of(
            "partition %d has %d of the %d healthy replicas on other members it needs to keep a"
                    .formatted(partitionId, healthy, needed)
                + " quorum");
      }
    }
    return Optional.empty();
  }

  private boolean isHealthyElsewhere(
      final BrokerClusterState state, final BrokerMemberId member, final int partitionId) {
    return !member.memberId().equals(localMemberId)
        && state.getPartitionHealth(member, partitionId) == PartitionHealthStatus.HEALTHY;
  }

  private void poll(final Supplier<Optional<String>> reason, final Consumer<String> waiting) {
    pollUntil(System.nanoTime() + timeout.toNanos(), reason, waiting);
  }

  private void pollUntil(
      final long deadline,
      final Supplier<Optional<String>> reason,
      final Consumer<String> waiting) {
    var why = reason.get();
    while (why.isPresent()) {
      waiting.accept(why.get());
      if (System.nanoTime() >= deadline) {
        throw new NotReadyException(why.get() + " after " + timeout, null);
      }
      try {
        Thread.sleep(POLL_INTERVAL);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      why = reason.get();
    }
  }

  /** What the rollout waits for on a member before it goes on to the next member. */
  enum Mode {
    /** Nothing: the next member restarts as soon as this one started the tenant's module. */
    NONE,
    /** Every local partition of the tenant is installed again. */
    INSTALLED,
    /**
     * A quorum of each partition stays healthy elsewhere before the restart, and the tenant serves
     * again after it.
     */
    SERVING
  }

  /** The tenant did not become ready, or its partitions had no quorum elsewhere, in time. */
  static final class NotReadyException extends RuntimeException {
    NotReadyException(final String message, final @Nullable Throwable cause) {
      super(message, cause);
    }
  }
}
