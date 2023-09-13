/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

/**
 * Can be implemented and used to react on partition role changes, like on Leader on Actor should be
 * started and on Follower one should be removed. If this listener performs actions that are
 * critical to the progress of a partition, it is expected to complete the future exceptionally on a
 * failure. Otherwise the future should complete normally.
 */
public interface PartitionRaftListener {
  /**
   * Is called by the {@link io.camunda.zeebe.broker.system.partitions.ZeebePartition} on starting
   * to become partition follower. This is called before the installation starts, but after the node
   * became already follower on raft level.
   *
   * @param partitionId the corresponding partition id
   * @param term the current term
   * @return future that should be completed by the listener
   */
  default ActorFuture<Void> onBecameRaftFollower(final int partitionId, final long term) {
    return CompletableActorFuture.completed(null);
  }

  /**
   * Is called by the {@link io.camunda.zeebe.broker.system.partitions.ZeebePartition} on starting
   * to become partition leader. This is called before the installation starts, but after the node
   * became already leader on raft level.
   *
   * @param partitionId the corresponding partition id
   * @param term the current term
   * @param logStream the corresponding log stream
   * @param queryService the corresponding query service
   * @return future that should be completed by the listener
   */
  default ActorFuture<Void> onBecameRaftLeader(final int partitionId, final long term) {
    return CompletableActorFuture.completed(null);
  }
}
