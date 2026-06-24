/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Collection;
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
  ActorFuture<Void> join(
      int partitionId,
      Map<MemberId, Integer> membersWithPriority,
      DynamicPartitionConfig partitionConfig);

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

  /**
   * The implementation of this method must bootstrap the partition with a single replica. The
   * implementation must be idempotent. If the node restarts after this method was called, but
   * before marking the operation as completed, it will be retried after the restart.
   *
   * @param partitionId id of the partition
   * @param priority priority of the member in the partition used for Raft's priority election
   * @param partitionConfig the configuration of the partition
   * @return a future that completes when the partition is bootstrapped
   */
  ActorFuture<Void> bootstrap(
      int partitionId,
      int priority,
      DynamicPartitionConfig partitionConfig,
      boolean initializeFromConfig);

  /**
   * Updates the priority of the member used for raft priority election for the given partition.
   *
   * @param partitionId id of the partition
   * @param newPriority new priority value
   * @return a future that completes when the priority is updated
   */
  ActorFuture<Void> reconfigurePriority(int partitionId, int newPriority);

  /**
   * Force reconfigure a partition to include only the given members in the replication group.
   *
   * @param partitionId id of the partition
   * @param members members that will be part of the replication group after reconfiguring
   * @return a future that completes when the partition is reconfigured
   */
  ActorFuture<Void> forceReconfigure(final int partitionId, final Collection<MemberId> members);

  /**
   * Disables the exporter for the given partition.
   *
   * @param partitionId id of the partition
   * @param exporterId id of the exporter to disable
   * @return a future that completes when the exporter is disabled
   */
  ActorFuture<Void> disableExporter(final int partitionId, final String exporterId);

  /**
   * Delete the exporter for the given partition.
   *
   * @param partitionId id of the partition
   * @param exporterId id of the exporter to delete
   * @return a future that completes when the exporter is deleted
   */
  ActorFuture<Void> deleteExporter(final int partitionId, final String exporterId);

  /**
   * Enables the exporter for the given partition.
   *
   * @param partitionId id of the partition
   * @param exporterId id of the exporter to enable
   * @param metadataVersion the version of the metadata to set in the exporter state
   * @param initializeFrom the id of another exporter to initialize metadata from. Can be null.
   * @return a future that completes when the exporter is enabled
   */
  ActorFuture<Void> enableExporter(
      int partitionId, String exporterId, long metadataVersion, String initializeFrom);
}
