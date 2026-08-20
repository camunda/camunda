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
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Defines the supported requests for the configuration management. */
@NullMarked
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

  /**
   * Adds a member to one partition's replication group, or changes the priority it already
   * replicates that partition with.
   *
   * @param partitionId the partition within {@code physicalTenantId}'s partition group. Partition
   *     ids restart at 1 in every physical tenant, so this identifies a partition only together
   *     with the tenant.
   * @param physicalTenantId the physical tenant whose partition to join, or empty for the default
   *     physical tenant. A single partition belongs to exactly one tenant, so an absent id cannot
   *     mean "every tenant" here, unlike the requests that address a whole cluster.
   */
  record JoinPartitionRequest(
      MemberId memberId,
      int partitionId,
      int priority,
      Optional<String> physicalTenantId,
      boolean dryRun)
      implements ClusterConfigurationManagementRequest {

    public JoinPartitionRequest {
      physicalTenantId.ifPresent(assertNonEmpty("physicalTenantId"));
    }
  }

  /**
   * Removes a member from one partition's replication group.
   *
   * @param partitionId the partition within {@code physicalTenantId}'s partition group. Partition
   *     ids restart at 1 in every physical tenant, so this identifies a partition only together
   *     with the tenant.
   * @param physicalTenantId the physical tenant whose partition to leave, or empty for the default
   *     physical tenant. A single partition belongs to exactly one tenant, so an absent id cannot
   *     mean "every tenant" here, unlike the requests that address a whole cluster.
   */
  record LeavePartitionRequest(
      MemberId memberId, int partitionId, Optional<String> physicalTenantId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {

    public LeavePartitionRequest {
      physicalTenantId.ifPresent(assertNonEmpty("physicalTenantId"));
    }
  }

  /**
   * Purge the partitions and the exported history of the given physical tenant. If no
   * physicalTenantId is provided, it applies to all tenants.
   */
  record PurgeRequest(Optional<String> physicalTenantId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

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
   *     Targets cluster membership, which has no tenant dimension, so it must not be combined with
   *     {@code physicalTenantId}.
   * @param newPartitionCount the target number of partitions of a single physical tenant — the one
   *     named by {@code physicalTenantId}, or the default tenant when none is named — or empty to
   *     leave it unchanged. Partitions can only be scaled up.
   * @param newReplicationFactor the target replication factor, or empty to leave it unchanged. When
   *     {@code zone} is set it must not be set. To change replication factor in zone aware clusters
   *     use {@link UpdatePartitionDistributorConfigRequest}. A cluster-wide setting, so it has no
   *     tenant to scope it to and must not be combined with {@code physicalTenantId}.
   * @param zone the zone to scale, or empty to scale a plain cluster. Required when scaling a
   *     zone-aware cluster and rejected on a plain one.
   * @param physicalTenantId the physical tenant whose partition group {@code newPartitionCount}
   *     applies to, or empty for the default physical tenant. Must not be combined with {@code
   *     brokerCount} or {@code newReplicationFactor}.
   * @param dryRun when true, the resulting plan is computed and returned without being applied.
   */
  record ClusterScaleRequest(
      Optional<Integer> brokerCount,
      Optional<Integer> newPartitionCount,
      Optional<Integer> newReplicationFactor,
      Optional<String> zone,
      Optional<String> physicalTenantId,
      boolean dryRun)
      implements ClusterConfigurationManagementRequest {
    public ClusterScaleRequest(
        final Optional<Integer> brokerCount,
        final Optional<Integer> newPartitionCount,
        final Optional<Integer> newReplicationFactor,
        final Optional<String> zone,
        final boolean dryRun) {
      this(brokerCount, newPartitionCount, newReplicationFactor, zone, Optional.empty(), dryRun);
    }

    public ClusterScaleRequest {
      zone.ifPresent(assertNonEmpty("zone"));
      physicalTenantId.ifPresent(assertNonEmpty("physicalTenantId"));
      brokerCount.ifPresent(assertPositive("brokerCount"));
      newPartitionCount.ifPresent(assertPositive("newPartitionCount"));
      newReplicationFactor.ifPresent(assertPositive("newReplicationFactor"));
    }
  }

  /**
   * @param physicalTenantId the physical tenant whose partition group {@code newPartitionCount}
   *     applies to, or empty for the default physical tenant. Must not be combined with a non-empty
   *     {@code membersToAdd}/{@code membersToRemove}, which change cluster membership and have no
   *     tenant dimension, nor with {@code newReplicationFactor}, which is a cluster-wide setting.
   */
  record ClusterPatchRequest(
      Set<MemberId> membersToAdd,
      Set<MemberId> membersToRemove,
      Optional<Integer> newPartitionCount,
      Optional<Integer> newReplicationFactor,
      Optional<String> physicalTenantId,
      boolean dryRun)
      implements ClusterConfigurationManagementRequest {

    public ClusterPatchRequest(
        final Set<MemberId> membersToAdd,
        final Set<MemberId> membersToRemove,
        final Optional<Integer> newPartitionCount,
        final Optional<Integer> newReplicationFactor,
        final boolean dryRun) {
      this(
          membersToAdd,
          membersToRemove,
          newPartitionCount,
          newReplicationFactor,
          Optional.empty(),
          dryRun);
    }

    public ClusterPatchRequest {
      physicalTenantId.ifPresent(assertNonEmpty("physicalTenantId"));
      newPartitionCount.ifPresent(assertPositive("newPartitionCount"));
      newReplicationFactor.ifPresent(assertPositive("newReplicationFactor"));
    }
  }

  /**
   * Writes the routing state. If physicalTenantId is provided, only that physical tenant's routing
   * state is updated; otherwise, the default physical tenant's routing state is updated, unlike the
   * other per-tenant requests where an absent physicalTenantId means "every tenant".
   */
  record UpdateRoutingStateRequest(
      Optional<RoutingState> routingState, Optional<String> physicalTenantId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record UpdatePartitionDistributorConfigRequest(PartitionDistributorConfig config, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  /**
   * Migrates one persisted zone stage from a bare or partially zoned cluster. The request names the
   * zone to migrate; the persisted {@code ZoneAwareConfig} remains the single source of truth for
   * zone order, priorities, and replica counts.
   */
  record ClusterZoneMigrationRequest(String zone, boolean dryRun)
      implements ClusterConfigurationManagementRequest {
    public ClusterZoneMigrationRequest {
      assertNonEmpty("zone").accept(zone);
    }
  }

  record ForceRemoveBrokersRequest(Set<MemberId> membersToRemove, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  /**
   * Discards a disabled physical tenant, so that a broker still holding its partitions may leave
   * the cluster. Until this is called, a disabled tenant blocks a broker removal exactly as an
   * enabled one does — disabling is a safety barrier against losing a tenant's data by accident,
   * not a statement that the data is expendable.
   *
   * <p>The tenant must already be disabled, i.e. absent from the brokers' static configuration.
   *
   * @param force when true, the request is executed by whichever broker receives it instead of
   *     being routed to the elected coordinator — the operator's bail-out for when the coordinator
   *     is unreachable. Normally left false, so the request runs on the coordinator like any other
   *     configuration change.
   */
  record RemovePhysicalTenantRequest(String physicalTenantId, boolean dryRun, boolean force)
      implements ClusterConfigurationManagementRequest {
    public RemovePhysicalTenantRequest {
      assertNonEmpty("physicalTenantId").accept(physicalTenantId);
    }
  }

  /**
   * Force-evicts a failed zone's brokers from the member set and drops the zone from the persisted
   * {@code ZoneAwareConfig}, in one atomic change.
   */
  record ForceZoneRemoveRequest(String zoneId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  /**
   * Restores a previously failed-over zone: re-adds the operator-supplied brokers and re-includes
   * the zone in the persisted {@code ZoneAwareConfig}, in one atomic change.
   */
  record AddZoneRequest(
      String zoneId, int numberOfReplicas, int priority, Set<MemberId> brokers, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  /**
   * Re-orders the Raft leader-election priorities of the persisted {@code ZoneAwareConfig}: the
   * existing priority values are kept and re-assigned to zones by {@code zoneOrder} (highest
   * first).
   */
  record UpdateZonePrioritiesRequest(List<String> zoneOrder, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  /**
   * Disable an exporter on all partitions of the given physical tenant. If no physicalTenantId is
   * provided, it applies to all tenants.
   */
  record ExporterDisableRequest(
      String exporterId, Optional<String> physicalTenantId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  /**
   * Delete an exporter on all partitions of the given physical tenant. If no physicalTenantId is
   * provided, it applies to all tenants.
   */
  record ExporterDeleteRequest(String exporterId, Optional<String> physicalTenantId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  /**
   * Enable an exporter on all partitions of the given physical tenant. If no physicalTenantId is
   * provided, it applies to all tenants.
   */
  record ExporterEnableRequest(
      String exporterId,
      Optional<String> initializeFrom,
      Optional<String> physicalTenantId,
      boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  /**
   * @param physicalTenantId the physical tenant to change, or empty for every physical tenant of
   *     the cluster
   */
  record ExportingStateChangeRequest(
      ExportingState state, Optional<String> physicalTenantId, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record CancelChangeRequest(long changeId) implements ClusterConfigurationManagementRequest {

    @Override
    public boolean dryRun() {
      return false;
    }
  }

  /**
   * @param physicalTenantId the physical tenant to transition, or empty for every physical tenant
   *     of the cluster
   */
  record ModeChangeRequest(Optional<String> physicalTenantId, Mode mode, boolean dryRun)
      implements ClusterConfigurationManagementRequest {

    public static ModeChangeRequest recovering(
        final Optional<String> physicalTenantId, final boolean dryRun) {
      return new ModeChangeRequest(physicalTenantId, Mode.RECOVERING, dryRun);
    }

    public static ModeChangeRequest processing(
        final Optional<String> physicalTenantId, final boolean dryRun) {
      return new ModeChangeRequest(physicalTenantId, Mode.PROCESSING, dryRun);
    }
  }

  record RestoreParameters(List<Long> backupIds, @Nullable String from, @Nullable String to) {}

  record TenantRestoreArguments(
      RestoreParameters parameters, String databaseType, boolean continuousBackups) {}

  record RestoreRequest(String physicalTenantId, TenantRestoreArguments arguments, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record RestoreResolvedRequest(Map<Integer, long[]> backups, boolean dryRun)
      implements ClusterConfigurationManagementRequest {}

  record ClusterRestoreRequest(Map<String, TenantRestoreArguments> tenantArguments, boolean dryRun)
      implements ClusterConfigurationManagementRequest {

    public TenantRestoreArguments argumentsFor(final String physicalTenantId) {
      final var arguments = tenantArguments.get(physicalTenantId);
      if (arguments == null) {
        throw new NoSuchElementException(
            "No restore arguments for physical tenant '%s'".formatted(physicalTenantId));
      }
      return arguments;
    }

    public RestoreRequest toRestoreRequest(final String physicalTenantId) {
      return new RestoreRequest(physicalTenantId, argumentsFor(physicalTenantId), dryRun);
    }
  }
}
