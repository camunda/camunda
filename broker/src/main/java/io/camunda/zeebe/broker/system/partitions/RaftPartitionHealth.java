/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions;

import io.atomix.raft.RaftFailureListener;
import io.atomix.raft.partition.RaftPartition;
import io.zeebe.util.health.FailureListener;
import io.zeebe.util.health.HealthMonitorable;
import io.zeebe.util.health.HealthStatus;
import io.zeebe.util.sched.ActorControl;
import java.util.concurrent.CompletableFuture;

public class RaftPartitionHealth implements HealthMonitorable, RaftFailureListener {

  private final RaftPartition atomixRaftPartition;
  private FailureListener healthMonitor;
  private final ActorControl actor;
  private final RaftFailureListener raftFailureListener;
  private final String name;

  RaftPartitionHealth(
      final RaftPartition atomixRaftPartition,
      final ActorControl actor,
      final RaftFailureListener listener) {
    this.atomixRaftPartition = atomixRaftPartition;
    this.actor = actor;
    raftFailureListener = listener;
    this.atomixRaftPartition.addFailureListener(this);
    name = "Raft-" + atomixRaftPartition.id().id();
  }

  @Override
  public HealthStatus getHealthStatus() {
    final boolean isHealthy = atomixRaftPartition.getServer().isRunning();
    return isHealthy ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> healthMonitor = failureListener);
  }

  @Override
  public CompletableFuture<Void> onRaftFailed() {
    if (healthMonitor != null) {
      healthMonitor.onFailure();
    }
    return raftFailureListener.onRaftFailed();
  }

  public void close() {
    atomixRaftPartition.removeFailureListener(this);
  }

  public String getName() {
    return name;
  }
}
