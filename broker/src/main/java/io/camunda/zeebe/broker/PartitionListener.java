/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.util.sched.future.ActorFuture;

/**
 * Can be implemented and used to react on partition role changes, like on Leader on Actor should be
 * started and on Follower one should be removed. If this listener performs actions that are
 * critical to the progress of a partition, it is expected to complete the future exceptionally on a
 * failure. Otherwise the future should complete normally.
 */
public interface PartitionListener {

  /**
   * Is called by the {@link io.camunda.zeebe.broker.system.partitions.ZeebePartition} on becoming
   * partition follower after all partition installation/clean up related things are done.
   *
   * @param partitionId the corresponding partition id
   * @param term the current term
   * @return future that should be completed by the listener
   */
  ActorFuture<Void> onBecomingFollower(int partitionId, long term);

  /**
   * Is called by the {@link io.camunda.zeebe.broker.system.partitions.ZeebePartition} on becoming
   * partition leader after all partition installation/clean up related things are done.
   *
   * @param partitionId the corresponding partition id
   * @param term the current term
   * @param logStream the corresponding log stream
   * @param queryService the corresponding query service
   * @return future that should be completed by the listener
   */
  ActorFuture<Void> onBecomingLeader(
      int partitionId,
      long term,
      // lookup of log stream and queryService will be changed in the future
      @Deprecated LogStream logStream,
      QueryService queryService);

  /**
   * Is called by the {@link io.camunda.zeebe.broker.system.partitions.ZeebePartition} on becoming
   * inactive after a Raft role change or a failed transition.
   *
   * @param partitionId the corresponding partition id
   * @param term the current term
   * @return future that should be completed by the listener
   */
  ActorFuture<Void> onBecomingInactive(int partitionId, long term);
}
