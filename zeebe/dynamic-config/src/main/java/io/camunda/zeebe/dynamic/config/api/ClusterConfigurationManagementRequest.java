/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.util.Preconditions.assertNonEmpty;
import static io.camunda.zeebe.util.Preconditions.assertPositive;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.Mode;
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

  /**
   * Request to scale a cluster by target counts rather than by explicit broker ids.
   *
   * @param brokerCount the target number of brokers. On a plain (non-zone-aware) cluster this is
   *     the total cluster size; when {@code zone} is set it is the target broker count <em>within
   *     that zone</em>, leaving the other zones untouched. Empty leaves the broker count unchanged.
   * @param newPartitionCount the target number of partitions, or empty to leave it unchanged.
   *     Partitions can only be scaled up.
   * @param newReplicationFactor the target replication factor, or empty to leave it unchanged. When
   *     {@code zone} is set it must not be set. To change replication factor in zone aware clusters
   *     use {@link UpdatePartitionDistributorConfigRequest}
   * @param zone the zone to scale, or empty to scale a plain cluster. Required when scaling a
   *     zone-aware cluster and rejected on a plain one.
   * @param dryRun when true, the resulting plan is computed and returned without being applied.
   */
  record ClusterScaleRequest(
      Optional<Integer> brokerCount,
      Optional<Integer> newPartitionCount,
      Optional<Integer> newReplicationFactor,
      Optional<String> zone,
      boolean dryRun)
      implements ClusterConfigurationManagementRequest {
    public ClusterScaleRequest {
      zone.ifPresent(assertNonEmpty("zone"));
      brokerCount.ifPresent(assertPositive("brokerCount"));
      newPartitionCount.ifPresent(assertPositive("newPartitionCount"));
      newReplicationFactor.ifPresent(assertPositive("newReplicationFactor"));
    }
  }

  record ClusterPatchRequest(
      Set<MemberId> membersToAdd,
      Set<MemberId> membersToRemove,
      Optional<Integer> newPartitionCount,
      Optional<Integer> newReplicationFactor,
      boolean dryRun)
      implements ClusterConfigurationManagementRequest {

    public ClusterPatchRequest {
      newPartitionCount.ifPresent(assertPositive("newPartitionCount"));
      newReplicationFactor.ifPresent(assertPositive("newReplicationFactor"));
    }
  }

  record UpdateRoutingStateRequest(Optional<RoutingState> routingState, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record UpdatePartitionDistributorConfigRequest(PartitionDistributorConfig config, boolean dryRun)
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

  record ModeChangeRequest(String physicalTenantId, Mode mode, boolean dryRun)
      implements ClusterConfigurationManagementRequest {

    public static ModeChangeRequest recovering(
        final String physicalTenantId, final boolean dryRun) {
      return new ModeChangeRequest(physicalTenantId, Mode.RECOVERING, dryRun);
    }

    public static ModeChangeRequest processing(
        final String physicalTenantId, final boolean dryRun) {
      return new ModeChangeRequest(physicalTenantId, Mode.PROCESSING, dryRun);
    }
  }
}
