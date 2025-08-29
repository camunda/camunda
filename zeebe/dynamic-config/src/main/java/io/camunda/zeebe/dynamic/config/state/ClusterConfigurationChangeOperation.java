/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.atomix.cluster.MemberId;
import java.util.Collection;
import java.util.Optional;

/**
 * An operation that changes the configuration. The operation could be a member join or leave a
 * cluster, or a member join or leave partition.
 */
public sealed interface ClusterConfigurationChangeOperation {

  MemberId memberId();

  /**
   * Operation to add a member to the ClusterConfiguration.
   *
   * @param memberId the member id of the member that joined the cluster
   */
  record MemberJoinOperation(MemberId memberId) implements ClusterConfigurationChangeOperation {}

  /**
   * Operation to remove a member from the ClusterConfiguration.
   *
   * @param memberId the member id of the member that is leaving the cluster
   */
  record MemberLeaveOperation(MemberId memberId) implements ClusterConfigurationChangeOperation {}

  /**
   * Operation to remove a member from the ClusterConfiguration. This operation is used to force
   * remove a (unreachable) member.
   *
   * @param memberId the id of the member that applies this operations
   * @param memberToRemove the id of the member to remove
   */
  record MemberRemoveOperation(MemberId memberId, MemberId memberToRemove)
      implements ClusterConfigurationChangeOperation {}

  sealed interface PartitionChangeOperation extends ClusterConfigurationChangeOperation {
    int partitionId();

    /**
     * Operation to add a member to a partition's replication group.
     *
     * @param memberId the member id of the member that will start replicating the partition
     * @param partitionId id of the partition to join
     * @param priority priority of the member in the partition used for Raft's priority election
     */
    record PartitionJoinOperation(MemberId memberId, int partitionId, int priority)
        implements PartitionChangeOperation {}

    /**
     * Operation to remove a member from a partition's replication group.
     *
     * @param memberId the member id of the member that will stop replicating the partition
     * @param partitionId id of the partition to leave
     */
    record PartitionLeaveOperation(MemberId memberId, int partitionId)
        implements PartitionChangeOperation {}

    /**
     * Operation to reconfigure the priority of a member used for Raft's priority election.
     *
     * @param memberId the member id of the member that will change its priority
     * @param partitionId id of the partition to reconfigure
     * @param priority new priority of the member in the partition
     */
    record PartitionReconfigurePriorityOperation(MemberId memberId, int partitionId, int priority)
        implements PartitionChangeOperation {}

    /**
     * Operation to force reconfigure the replication group of a partition.
     *
     * @param memberId the member id of the member that will apply this operation
     * @param partitionId id of the partition to reconfigure
     * @param members the members of the partition's replication group after the reconfiguration
     */
    record PartitionForceReconfigureOperation(
        MemberId memberId, int partitionId, Collection<MemberId> members)
        implements PartitionChangeOperation {}

    /**
     * Operation to disable an exporter on a partition in the given member.
     *
     * @param memberId the member id of the member that will apply this operation
     * @param partitionId id of the partition which disables the exporter
     * @param exporterId id of the exporter to disable
     */
    record PartitionDisableExporterOperation(MemberId memberId, int partitionId, String exporterId)
        implements PartitionChangeOperation {}

    /**
     * Operation to delete an exporter on a partition in the given member.
     *
     * @param memberId the member id of the member that will apply this operation
     * @param partitionId id of the partition which delete the exporter
     * @param exporterId id of the exporter to delete
     */
    record PartitionDeleteExporterOperation(MemberId memberId, int partitionId, String exporterId)
        implements PartitionChangeOperation {}

    /**
     * Operation to enable an exporter on a partition in the given member.
     *
     * @param memberId the member id of the member that will apply this operation
     * @param partitionId id of the partition which enables the exporter
     * @param exporterId id of the exporter to enable
     * @param initializeFrom id of the exporter to initialize the metadata from
     */
    record PartitionEnableExporterOperation(
        MemberId memberId, int partitionId, String exporterId, Optional<String> initializeFrom)
        implements PartitionChangeOperation {}

    /**
     * Operation to bootstrap a new partition in the given member. The operation starts the
     * partitions as a single replica. More replicas should be added in subsequent operations.
     *
     * @param memberId the member id of the member that will apply this operation
     * @param partitionId id of the partition to bootstrap
     */
    record PartitionBootstrapOperation(MemberId memberId, int partitionId, int priority)
        implements PartitionChangeOperation {}
  }
}
