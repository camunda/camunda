/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import java.util.Optional;
import java.util.Set;

/** Defines the supported requests for the configuration management. */
public sealed interface ClusterConfigurationManagementRequest {

  /**
   * Marks a request as dry run. Changes are planned and validated but not applied so the cluster
   * configuration remains unchanged.
   */
  boolean dryRun();

  record AddMembersRequest(Set<MemberId> members, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record RemoveMembersRequest(Set<MemberId> members, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record JoinPartitionRequest(MemberId memberId, int partitionId, int priority, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record LeavePartitionRequest(MemberId memberId, int partitionId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record ReassignPartitionsRequest(Set<MemberId> members, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record PurgeRequest(boolean dryRun) implements ClusterConfigurationManagementRequest {}

  record BrokerScaleRequest(
      Set<MemberId> members, Optional<Integer> newReplicationFactor, boolean dryRun)
      implements ClusterConfigurationManagementRequest {
    public BrokerScaleRequest(final Set<MemberId> members, final boolean dryRun) {
      this(members, Optional.empty(), dryRun);
    }
  }

  record ClusterScaleRequest(
      Optional<Integer> newClusterSize,
      Optional<Integer> newPartitionCount,
      Optional<Integer> newReplicationFactor,
      boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record ClusterPatchRequest(
      Set<MemberId> membersToAdd,
      Set<MemberId> membersToRemove,
      Optional<Integer> newPartitionCount,
      Optional<Integer> newReplicationFactor,
      boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record UpdateRoutingStateRequest(Optional<RoutingState> routingState, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record ForceRemoveBrokersRequest(Set<MemberId> membersToRemove, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record ExporterDisableRequest(String exporterId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record ExporterDeleteRequest(String exporterId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record ExporterEnableRequest(String exporterId, Optional<String> initializeFrom, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record CancelChangeRequest(long changeId) implements ClusterConfigurationManagementRequest {

    @Override
    public boolean dryRun() {
      return false;
    }
  }
}
