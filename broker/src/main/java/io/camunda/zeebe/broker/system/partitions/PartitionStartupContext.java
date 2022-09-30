/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.logstreams.LogDeletionService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;

public interface PartitionStartupContext {

  // provided by application-wide dependencies
  int getNodeId();

  RaftPartition getRaftPartition();

  int getPartitionId();

  ActorSchedulingService getActorSchedulingService();

  PersistedSnapshotStore getPersistedSnapshotStore();

  // injected before bootstrap
  /**
   * Returns the {@link ActorControl} of {@link ZeebePartition}
   *
   * @return {@link ActorControl} of {@link ZeebePartition}
   */
  ActorControl getActorControl();

  void setActorControl(ActorControl actorControl);

  LogDeletionService getLogDeletionService();

  void setLogDeletionService(final LogDeletionService deletionService);

  ScheduledTimer getMetricsTimer();

  void setMetricsTimer(final ScheduledTimer metricsTimer);

  ScheduledTimer getStatisticsTimer();

  void setStatisticsTimer(final ScheduledTimer statisticsTimer);

  ZeebeDb<ZbColumnFamilies> getZeebeDb();

  // can be called any time after bootstrap has completed
  PartitionTransitionContext createTransitionContext();
}
