/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddZoneRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterRestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterZoneMigrationRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceZoneRemoveRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemovePhysicalTenantRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateZonePrioritiesRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeResult;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Optional;
import java.util.function.Function;

/**
 * Handles the requests for the configuration management. This is expected be running on the
 * coordinator node.
 */
public final class ClusterConfigurationManagementRequestsHandler
    implements ClusterConfigurationManagementApi {
  private final ConfigurationChangeCoordinator coordinator;
  private final ConcurrencyControl executor;
  private final MemberId localMemberId;
  private final RequestValidatorRegistry validatorRegistry;

  public ClusterConfigurationManagementRequestsHandler(
      final ConfigurationChangeCoordinator coordinator,
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final RequestValidatorRegistry validatorRegistry) {
    this.coordinator = coordinator;
    this.executor = executor;
    this.localMemberId = localMemberId;
    this.validatorRegistry = validatorRegistry;
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> addMembers(
      final AddMembersRequest addMembersRequest) {
    return handleRequest(
        addMembersRequest.dryRun(), new AddMembersTransformer(addMembersRequest.members()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    return handleRequest(
        removeMembersRequest.dryRun(),
        new RemoveMembersTransformer(removeMembersRequest.members()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    return handleRequest(
        joinPartitionRequest.dryRun(),
        new JoinPartitionRequestTransformer(
            joinPartitionRequest.memberId(),
            joinPartitionRequest.partitionId(),
            joinPartitionRequest.priority(),
            joinPartitionRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {
    return handleRequest(
        leavePartitionRequest.dryRun(),
        new LeavePartitionRequestTransformer(
            leavePartitionRequest.memberId(),
            leavePartitionRequest.partitionId(),
            leavePartitionRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> scaleMembers(
      final BrokerScaleRequest scaleRequest) {
    return handleRequest(
        scaleRequest.dryRun(),
        new ScaleRequestTransformer(scaleRequest.members(), scaleRequest.newReplicationFactor()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> forceScaleDown(
      final BrokerScaleRequest forceScaleDownRequest) {
    final Optional<Integer> optionalNewReplicationFactor =
        forceScaleDownRequest.newReplicationFactor();
    if (optionalNewReplicationFactor.isPresent()) {
      final String errorMessage =
          String.format(
              "The replication factor cannot be changed to requested value '%s' during force scale down. It will be automatically changed based on which brokers are removed. Do not provide any replication factor in the request",
              optionalNewReplicationFactor.get());
      return CompletableActorFuture.completedExceptionally(new InvalidRequest(errorMessage));
    }

    return handleRequest(
        forceScaleDownRequest.dryRun(),
        new ForceScaleDownRequestTransformer(forceScaleDownRequest.members(), localMemberId));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> scaleCluster(
      final ClusterScaleRequest clusterScaleRequest) {
    return handleRequest(
        clusterScaleRequest.dryRun(),
        new ClusterScaleRequestTransformer(
            clusterScaleRequest.brokerCount(),
            clusterScaleRequest.newPartitionCount(),
            clusterScaleRequest.newReplicationFactor(),
            clusterScaleRequest.zone(),
            clusterScaleRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> patchCluster(
      final ClusterPatchRequest clusterPatchRequest) {
    return handleRequest(
        clusterPatchRequest.dryRun(),
        new ClusterPatchRequestTransformer(
            clusterPatchRequest.membersToAdd(),
            clusterPatchRequest.membersToRemove(),
            clusterPatchRequest.newPartitionCount(),
            clusterPatchRequest.newReplicationFactor(),
            clusterPatchRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> updateRoutingState(
      final UpdateRoutingStateRequest updateRoutingStateRequest) {
    return handleRequest(
        updateRoutingStateRequest.dryRun(),
        new UpdateRoutingStateTransformer(
            updateRoutingStateRequest.routingState(),
            updateRoutingStateRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> updatePartitionDistribution(
      final UpdatePartitionDistributorConfigRequest request) {
    return handleRequest(
        request.dryRun(), new UpdatePartitionDistributionTransformer(request.config()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> migrateZone(
      final ClusterZoneMigrationRequest zoneMigrationRequest) {
    return handleRequest(
        zoneMigrationRequest.dryRun(),
        new ZoneMigrationRequestTransformer(zoneMigrationRequest.zone()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> purge(final PurgeRequest purgeRequest) {
    return handleRequest(
        purgeRequest.dryRun(), new PurgeRequestTransformer(purgeRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> forceRemoveBrokers(
      final ForceRemoveBrokersRequest forceRemoveBrokersRequest) {
    return handleRequest(
        forceRemoveBrokersRequest.dryRun(),
        new ForceRemoveBrokersRequestTransformer(
            forceRemoveBrokersRequest.membersToRemove(), localMemberId));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> removePhysicalTenant(
      final RemovePhysicalTenantRequest removePhysicalTenantRequest) {
    return handleRequest(
        removePhysicalTenantRequest.dryRun(),
        new RemovePhysicalTenantRequestTransformer(
            removePhysicalTenantRequest.physicalTenantId(),
            localMemberId,
            removePhysicalTenantRequest.force()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> forceRemoveZone(
      final ForceZoneRemoveRequest forceZoneRemoveRequest) {
    return handleRequest(
        forceZoneRemoveRequest.dryRun(),
        new ForceRemoveZoneTransformer(forceZoneRemoveRequest.zoneId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> addZone(
      final AddZoneRequest addZoneRequest) {
    return handleRequest(
        addZoneRequest.dryRun(),
        new AddZoneTransformer(
            addZoneRequest.zoneId(),
            addZoneRequest.numberOfReplicas(),
            addZoneRequest.priority(),
            addZoneRequest.brokers()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> updateZonePriorities(
      final UpdateZonePrioritiesRequest updateZonePrioritiesRequest) {
    return handleRequest(
        updateZonePrioritiesRequest.dryRun(),
        new UpdateZonePrioritiesTransformer(updateZonePrioritiesRequest.zoneOrder()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> disableExporter(
      final ExporterDisableRequest exporterDisableRequest) {
    return handleRequest(
        exporterDisableRequest.dryRun(),
        new ExporterDisableRequestTransformer(
            exporterDisableRequest.exporterId(), exporterDisableRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> deleteExporter(
      final ExporterDeleteRequest exporterDeleteRequest) {
    return handleRequest(
        exporterDeleteRequest.dryRun(),
        new ExporterDeleteRequestTransformer(
            exporterDeleteRequest.exporterId(), exporterDeleteRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> enableExporter(
      final ExporterEnableRequest enableRequest) {
    return handleRequest(
        enableRequest.dryRun(),
        new ExporterEnableRequestTransformer(
            enableRequest.exporterId(),
            enableRequest.initializeFrom(),
            enableRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> modeChange(
      final ModeChangeRequest modeChangeRequest) {
    return handleRequest(
        modeChangeRequest.dryRun(), new ModeChangeRequestTransformer(modeChangeRequest));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> changeExportingState(
      final ExportingStateChangeRequest exportingStateChangeRequest) {
    return handleRequest(
        exportingStateChangeRequest.dryRun(),
        new ExportingStateChangeRequestTransformer(
            exportingStateChangeRequest.state(), exportingStateChangeRequest.physicalTenantId()));
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> cancelTopologyChange(
      final CancelChangeRequest changeRequest) {
    return coordinator.cancelChange(changeRequest.changeId());
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> getTopology() {
    return coordinator.getClusterConfiguration();
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> restore(final RestoreRequest request) {
    return handleRequest(
        request.dryRun(), new RestoreRequestTransformer(request, validatorRegistry));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> clusterRestore(
      final ClusterRestoreRequest request) {
    return handleRequest(
        request.dryRun(), new ClusterRestoreRequestTransformer(request, validatorRegistry));
  }

  private ActorFuture<ClusterConfigurationChangeResponse> handleRequest(
      final boolean dryRun, final ConfigurationChangeRequest request) {
    final Function<ConfigurationChangeRequest, ActorFuture<ConfigurationChangeResult>> handler;
    if (dryRun) {
      handler = coordinator::simulateOperations;
    } else {
      handler = coordinator::applyOperations;
    }

    return handler
        .apply(request)
        .thenApply(
            result ->
                new ClusterConfigurationChangeResponse(
                    result.changeId(),
                    new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                        result.currentConfiguration().members(),
                        result.finalConfiguration().members(),
                        result.legacyOperations()),
                    new ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse(
                        result.currentMultiConfiguration(),
                        result.finalMultiConfiguration(),
                        result.phases())),
            executor);
  }
}
