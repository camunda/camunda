/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

/**
 * Can be implemented and used to react on partition role changes, like on Leader on Actor should be
 * started and on Follower one should be removed.
 */
public interface PartitionRaftListener {
  /**
   * Is called by the {@link io.camunda.zeebe.broker.system.partitions.ZeebePartition} on starting
   * to become partition follower. This is called before the installation starts, but after the node
   * became already follower on raft level.
   *
   * @param partitionId the corresponding partition id
   * @param term the current term
   */
  void onBecameRaftFollower(final int partitionId, final long term);

  /**
   * Is called by the {@link io.camunda.zeebe.broker.system.partitions.ZeebePartition} on starting
   * to become partition leader. This is called before the installation starts, but after the node
   * became already leader on raft level.
   *
   * @param partitionId the corresponding partition id
   * @param term the current term
   */
  void onBecameRaftLeader(final int partitionId, final long term);
}
