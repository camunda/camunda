/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.Consumer;

/**
 * Facade for the system partition: the Raft-replicated source of truth for the {@link
 * ClusterConfiguration}.
 *
 * <p>The system partition is a single Raft group whose membership is statically configured (from
 * {@code broker.yaml}). The leader of this Raft group is the cluster coordinator. Updates are
 * accepted only on the leader by submitting {@link ClusterConfigurationIntent} commands to the
 * system partition's stream processor; the resulting events propagate to followers via Raft
 * replication and are applied deterministically on every replica.
 *
 * <p>This interface intentionally exposes both the legacy direct-update API (deprecated; used by
 * {@link SystemPartitionStateMachine} and {@link SystemPartitionMirror}) and the stream-processor
 * facade API. The legacy methods will be removed once the bootstrap has switched to the stream
 * processor implementation in a follow-up phase.
 */
public interface SystemPartition {

  // -------------------------------------------------------------------------
  // Stream-processor facade API.
  // -------------------------------------------------------------------------

  /**
   * Returns the latest committed {@link ClusterConfiguration} as observed by this replica's commit
   * listener. After the first commit, this is guaranteed non-null and at least as fresh as the most
   * recently applied commit on this replica.
   */
  ClusterConfiguration query();

  /**
   * Subscribe to commit notifications for cluster-configuration events.
   *
   * <p>Each time a {@link ClusterConfigurationIntent} event is applied on this replica, the
   * listener is invoked with the resulting {@link ClusterConfiguration}.
   */
  void addClusterConfigListener(Consumer<ClusterConfiguration> listener);

  /**
   * Submit a cluster-configuration command to the system partition.
   *
   * <p>On the leader, the command is appended directly to the stream. On a follower, callers must
   * route through the broker client to the current leader; this facade does not perform forwarding.
   * The future completes with the corresponding event (e.g. {@code CHANGE_PLAN_STAMPED} for a
   * {@code STAMP_CHANGE_PLAN} command) once it is committed and applied on this replica.
   */
  ActorFuture<ClusterConfigurationRecord> submitCommand(
      ClusterConfigurationIntent intent, ClusterConfigurationRecord record);

  /** True iff this broker hosts a system-partition replica that is currently the Raft leader. */
  boolean isLeader();

  /**
   * Subscribe to leader-role transitions. The listener is invoked with {@code true} when this
   * replica becomes leader and {@code false} when it steps down.
   */
  void addLeaderListener(Consumer<Boolean> listener);

  // -------------------------------------------------------------------------
  // Legacy direct-update API (kept for backward compatibility with
  // SystemPartitionStateMachine + SystemPartitionMirror until Phase 3).
  // -------------------------------------------------------------------------

  /**
   * Append a new authoritative {@link ClusterConfiguration} to the system-partition log. Must be
   * called on the leader. Returns a future that completes when the entry is committed and applied
   * to the in-memory state on this replica.
   *
   * @deprecated Use {@link #submitCommand(ClusterConfigurationIntent, ClusterConfigurationRecord)}
   *     instead. Removed once the broker bootstrap switches to the stream processor.
   */
  @Deprecated
  ActorFuture<ClusterConfiguration> update(ClusterConfiguration newConfiguration);

  /**
   * Asynchronous variant of {@link #query()} that returns an {@link ActorFuture}. Useful for
   * callers running on actor threads.
   *
   * @deprecated Prefer the synchronous {@link #query()}.
   */
  @Deprecated
  ActorFuture<ClusterConfiguration> queryAsync();

  /**
   * Subscribe to commit notifications.
   *
   * @deprecated Use {@link #addClusterConfigListener(Consumer)}.
   */
  @Deprecated
  void addCommitListener(Consumer<ClusterConfiguration> listener);

  /**
   * Remove a listener previously registered via {@link #addCommitListener(Consumer)}.
   *
   * @deprecated Listeners registered via {@link #addClusterConfigListener(Consumer)} cannot be
   *     individually removed in the new facade.
   */
  @Deprecated
  void removeCommitListener(Consumer<ClusterConfiguration> listener);

  /** Thrown when a write is attempted on a non-leader replica. */
  final class NotLeaderException extends RuntimeException {
    public NotLeaderException(final String message) {
      super(message);
    }
  }

  /**
   * Thrown when an application-level CAS on {@link ClusterConfiguration#version()} fails — i.e.
   * another writer's entry was applied before ours. The caller should re-read the latest state and
   * retry.
   */
  final class ConcurrentModificationException extends RuntimeException {
    public ConcurrentModificationException(final String message) {
      super(message);
    }
  }
}
