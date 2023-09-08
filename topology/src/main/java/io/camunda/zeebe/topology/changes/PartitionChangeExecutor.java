/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Map;

/**
 * Represents the executor that executes the actual process to start or start. The concrete
 * implementation of this interface is expected to be a call back to the system component that can
 * start or stop partition. This is typically the PartitionManager in the Broker.
 */
public interface PartitionChangeExecutor {

  /**
   * The implementation of this method must start the partition on this member. The partition must
   * join the replication group formed by the members given in the {@code membersWithPriority}. The
   * implementation must be idempotent. If the node restarts after this method was called, but
   * before marking the operation as completed, it will be retried after the restart.
   *
   * @param partitionId id of the partition
   * @param membersWithPriority priority of each replicas used of leader election
   * @return a future that completes when the partition is started and joined the replication group
   */
  ActorFuture<Void> join(int partitionId, Map<MemberId, Integer> membersWithPriority);

  /**
   * The implementation of this method must remove the member from the replication group of the
   * given partition and stops the partition on this member. The implementation must be idempotent.
   * If the node restarts after this method was called, but before marking the operation as
   * completed, it will be retried after the restart.
   *
   * @param partitionId id of the partition
   * @return a future that completes when the partition is stopped and removed from the replication.
   */
  ActorFuture<Void> leave(int partitionId);
}
