/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableSortedSet;
import io.atomix.cluster.MemberId;
import java.util.Set;
import java.util.SortedSet;

/** Operations targeting global cluster configuration. */
public sealed interface GlobalChangeOperation extends ClusterConfigurationChangeOperation {

  /**
   * Operation to add a member to the ClusterConfiguration.
   *
   * @param memberId the member id of the member that joined the cluster
   */
  record MemberJoinOperation(MemberId memberId)
      implements io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation {}

  /**
   * Operation to remove a member from the ClusterConfiguration.
   *
   * @param memberId the member id of the member that is leaving the cluster
   */
  record MemberLeaveOperation(MemberId memberId)
      implements io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation {}

  /**
   * Operation to remove a member from the ClusterConfiguration. This operation is used to force
   * remove a (unreachable) member.
   *
   * @param memberId the id of the member that applies this operations
   * @param memberToRemove the id of the member to remove
   */
  record MemberRemoveOperation(MemberId memberId, MemberId memberToRemove)
      implements io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation {}

  /**
   * Operation to prepare a member for scaling. This operation is executed before scaling starts.
   *
   * @param memberId the member id of the member that will apply this operation
   * @param clusterMembers the list of member ids that will be part of the cluster after scaling is
   *     completed
   */
  record PreScalingOperation(MemberId memberId, SortedSet<MemberId> clusterMembers)
      implements io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation {

    public PreScalingOperation(final MemberId memberId, final Set<MemberId> clusterMembers) {
      this(memberId, ImmutableSortedSet.copyOf(clusterMembers));
    }
  }

  /**
   * Operation to finalize scaling. This operation is executed after scaling is complete.
   *
   * @param memberId the member id of the member that will apply this operation
   * @param clusterMembers the list of member ids that are part of the cluster after scaling is
   *     completed
   */
  record PostScalingOperation(MemberId memberId, SortedSet<MemberId> clusterMembers)
      implements io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation {

    public PostScalingOperation(final MemberId memberId, final Set<MemberId> clusterMembers) {
      this(memberId, ImmutableSortedSet.copyOf(clusterMembers));
    }
  }

  record UpdatePartitionDistributorConfigOperation(
      MemberId memberId, PartitionDistributorConfig config)
      implements io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation {}
}
