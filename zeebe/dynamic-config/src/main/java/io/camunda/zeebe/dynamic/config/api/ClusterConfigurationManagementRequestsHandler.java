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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeResult;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.List;
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
  private final boolean enablePartitionScaling;

  public ClusterConfigurationManagementRequestsHandler(
      final ConfigurationChangeCoordinator coordinator,
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final boolean enablePartitionScaling) {
    this.coordinator = coordinator;
    this.executor = executor;
    this.localMemberId = localMemberId;
    this.enablePartitionScaling = enablePartitionScaling;
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
        ignore ->
            Either.right(
                List.of(
                    new PartitionJoinOperation(
                        joinPartitionRequest.memberId(),
                        joinPartitionRequest.partitionId(),
                        joinPartitionRequest.priority()))));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {

    return handleRequest(
        leavePartitionRequest.dryRun(),
        ignore ->
            Either.right(
                List.of(
                    new PartitionLeaveOperation(
                        leavePartitionRequest.memberId(),
                        leavePartitionRequest.partitionId(),
                        1))));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> reassignPartitions(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    return handleRequest(
        reassignPartitionsRequest.dryRun(),
        new PartitionReassignRequestTransformer(reassignPartitionsRequest.members()));
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
      final var failedFuture = executor.<ClusterConfigurationChangeResponse>createFuture();
      final String errorMessage =
          String.format(
              "The replication factor cannot be changed to requested value '%s' during force scale down. It will be automatically changed based on which brokers are removed. Do not provide any replication factor in the request",
              optionalNewReplicationFactor.get());
      failedFuture.completeExceptionally(new InvalidRequest(errorMessage));
      return failedFuture;
    }

    return handleRequest(
        forceScaleDownRequest.dryRun(),
        new ForceScaleDownRequestTransformer(forceScaleDownRequest.members(), localMemberId));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> scaleCluster(
      final ClusterScaleRequest clusterScaleRequest) {

    if (!enablePartitionScaling && clusterScaleRequest.newPartitionCount().isPresent()) {
      final var failedFuture = executor.<ClusterConfigurationChangeResponse>createFuture();
      failedFuture.completeExceptionally(
          new UnsupportedOperationException("Partition scaling is not enabled."));
      return failedFuture;
    }

    return handleRequest(
        clusterScaleRequest.dryRun(),
        new ClusterScaleRequestTransformer(
            clusterScaleRequest.newClusterSize(),
            clusterScaleRequest.newPartitionCount(),
            clusterScaleRequest.newReplicationFactor()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> patchCluster(
      final ClusterPatchRequest clusterPatchRequest) {

    if (!enablePartitionScaling && clusterPatchRequest.newPartitionCount().isPresent()) {
      final var failedFuture = executor.<ClusterConfigurationChangeResponse>createFuture();
      failedFuture.completeExceptionally(
          new UnsupportedOperationException("Partition scaling is not enabled."));
      return failedFuture;
    }

    return handleRequest(
        clusterPatchRequest.dryRun(),
        new ClusterPatchRequestTransformer(
            clusterPatchRequest.membersToAdd(),
            clusterPatchRequest.membersToRemove(),
            clusterPatchRequest.newPartitionCount(),
            clusterPatchRequest.newReplicationFactor()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> updateRoutingState(
      final UpdateRoutingStateRequest updateRoutingStateRequest) {
    return handleRequest(
        updateRoutingStateRequest.dryRun(),
        new UpdateRoutingStateTransformer(
            enablePartitionScaling, updateRoutingStateRequest.routingState()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> purge(final PurgeRequest purgeRequest) {

    return handleRequest(purgeRequest.dryRun(), new PurgeRequestTransformer());
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
  public ActorFuture<ClusterConfigurationChangeResponse> disableExporter(
      final ExporterDisableRequest exporterDisableRequest) {
    return handleRequest(
        exporterDisableRequest.dryRun(),
        new ExporterDisableRequestTransformer(exporterDisableRequest.exporterId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> deleteExporter(
      final ExporterDeleteRequest exporterDeleteRequest) {
    return handleRequest(
        exporterDeleteRequest.dryRun(),
        new ExporterDeleteRequestTransformer(exporterDeleteRequest.exporterId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> enableExporter(
      final ExporterEnableRequest enableRequest) {
    return handleRequest(
        enableRequest.dryRun(),
        new ExporterEnableRequestTransformer(
            enableRequest.exporterId(), enableRequest.initializeFrom()));
  }

  @Override
  public ActorFuture<ClusterConfiguration> cancelTopologyChange(
      final CancelChangeRequest changeRequest) {
    return coordinator.cancelChange(changeRequest.changeId());
  }

  @Override
  public ActorFuture<ClusterConfiguration> getTopology() {
    return coordinator.getClusterConfiguration();
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
                    result.currentConfiguration().members(),
                    result.finalConfiguration().members(),
                    result.operations()),
            executor);
  }
}
