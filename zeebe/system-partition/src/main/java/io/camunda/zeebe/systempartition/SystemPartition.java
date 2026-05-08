/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.dynamic.config.ClusterConfigCommandSubmitter;
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
 * <p>Extends {@link ClusterConfigCommandSubmitter} so the dynamic-config layer can accept this
 * interface directly without a circular module dependency.
 */
public interface SystemPartition extends ClusterConfigCommandSubmitter {

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
   * <p>On the leader, the command is appended directly to the stream. On a follower, the future
   * fails with {@link NotLeaderException}; callers must route through the broker client to the
   * current leader. The future completes with the corresponding event (e.g. {@code
   * CHANGE_PLAN_STAMPED} for a {@code STAMP_CHANGE_PLAN} command) once it is committed and applied
   * on this replica.
   */
  @Override
  ActorFuture<ClusterConfigurationRecord> submitCommand(
      ClusterConfigurationIntent intent, ClusterConfigurationRecord record);

  /** True iff this broker hosts a system-partition replica that is currently the Raft leader. */
  boolean isLeader();

  /**
   * Subscribe to leader-role transitions. The listener is invoked with {@code true} when this
   * replica becomes leader and {@code false} when it steps down.
   */
  void addLeaderListener(Consumer<Boolean> listener);

  /** Thrown when a write is attempted on a non-leader replica. */
  final class NotLeaderException extends RuntimeException {
    public NotLeaderException(final String message) {
      super(message);
    }
  }
}
