/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.ActorControl;
import java.util.HashSet;
import java.util.Set;

public class RaftPartitionHealth implements HealthMonitorable, FailureListener {

  private final RaftPartition atomixRaftPartition;
  private final ActorControl actor;
  private final Set<FailureListener> listeners = new HashSet<>();
  private final String name;
  private volatile HealthStatus status = HealthStatus.HEALTHY;

  RaftPartitionHealth(final RaftPartition atomixRaftPartition, final ActorControl actor) {
    this.atomixRaftPartition = atomixRaftPartition;
    this.actor = actor;
    this.atomixRaftPartition.addFailureListener(this);
    name = "Raft-" + atomixRaftPartition.id().id();
  }

  @Override
  public HealthStatus getHealthStatus() {
    return status;
  }

  @Override
  public void addFailureListener(final FailureListener listener) {
    actor.run(() -> listeners.add(listener));
  }

  @Override
  public void onFailure() {
    status = HealthStatus.UNHEALTHY;
    listeners.forEach(FailureListener::onFailure);
  }

  @Override
  public void onUnrecoverableFailure() {
    status = HealthStatus.DEAD;
    listeners.forEach(FailureListener::onUnrecoverableFailure);
  }

  @Override
  public void onRecovered() {
    status = HealthStatus.HEALTHY;
    listeners.forEach(FailureListener::onRecovered);
  }

  public void close() {
    atomixRaftPartition.removeFailureListener(this);
  }

  public String getName() {
    return name;
  }
}
