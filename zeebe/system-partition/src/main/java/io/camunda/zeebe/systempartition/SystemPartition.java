/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.Consumer;

/**
 * Facade for the system partition: the Raft-replicated source of truth for the {@link
 * ClusterConfiguration}.
 *
 * <p>The system partition is a single Raft group whose membership is statically configured (from
 * {@code broker.yaml}) and whose state machine stores exactly one value: the latest authoritative
 * {@link ClusterConfiguration}. The leader of this Raft group is the cluster coordinator; updates
 * are accepted only on the leader and propagate to followers via Raft replication. Gossip then
 * disseminates the committed state to non-member brokers.
 *
 * <p>Updates are linearizable through Raft consensus plus a per-entry application-level CAS on
 * {@link ClusterConfiguration#version()} — see {@link SystemPartitionRecord}.
 */
public interface SystemPartition {

  /**
   * Append a new authoritative {@link ClusterConfiguration} to the system-partition log. Must be
   * called on the leader. Returns a future that completes when the entry is committed and applied
   * to the in-memory state on this replica. The future fails with:
   *
   * <ul>
   *   <li>{@link NotLeaderException} if this replica is not the leader (or steps down before
   *       commit).
   *   <li>{@link ConcurrentModificationException} if another writer's entry committed first and the
   *       application-level CAS check on {@code version} fails.
   * </ul>
   */
  ActorFuture<ClusterConfiguration> update(ClusterConfiguration newConfiguration);

  /**
   * Returns the latest in-memory {@link ClusterConfiguration} as observed by the local commit
   * listener. On the leader, this is "linearizable enough" for the read-modify-write loop the
   * coordinator runs (a stale read causes the subsequent CAS to fail and retry). On a follower,
   * this is a stale-but-monotonic snapshot — at least as fresh as the most recently applied commit
   * on this replica.
   */
  ActorFuture<ClusterConfiguration> query();

  /** True iff this broker hosts a system-partition replica that is currently the Raft leader. */
  boolean isLeader();

  /**
   * Subscribe to commit notifications. Each time a new entry is applied, the listener is invoked
   * with the freshly installed {@link ClusterConfiguration}. Invoked on the state machine's actor
   * thread.
   */
  void addCommitListener(Consumer<ClusterConfiguration> listener);

  /** Remove a listener previously registered via {@link #addCommitListener(Consumer)}. */
  void removeCommitListener(Consumer<ClusterConfiguration> listener);

  /**
   * Subscribe to leader-role transitions. The listener is invoked with {@code true} when this
   * replica becomes leader and {@code false} when it steps down.
   */
  void addLeaderListener(Consumer<Boolean> listener);

  /** Thrown by {@link #update(ClusterConfiguration)} when the local replica is not the leader. */
  final class NotLeaderException extends RuntimeException {
    public NotLeaderException(final String message) {
      super(message);
    }
  }

  /**
   * Thrown by {@link #update(ClusterConfiguration)} when the application-level CAS on {@link
   * ClusterConfiguration#version()} fails — i.e. another writer's entry was applied before ours.
   * The caller should re-read the latest state and retry.
   */
  final class ConcurrentModificationException extends RuntimeException {
    public ConcurrentModificationException(final String message) {
      super(message);
    }
  }
}
