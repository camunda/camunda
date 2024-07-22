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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ScaleRequest;
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

  public ClusterConfigurationManagementRequestsHandler(
      final ConfigurationChangeCoordinator coordinator,
      final MemberId localMemberId,
      final ConcurrencyControl executor) {
    this.coordinator = coordinator;
    this.executor = executor;
    this.localMemberId = localMemberId;
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
                        leavePartitionRequest.memberId(), leavePartitionRequest.partitionId()))));
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
      final ScaleRequest scaleRequest) {
    return handleRequest(
        scaleRequest.dryRun(),
        new ScaleRequestTransformer(scaleRequest.members(), scaleRequest.newReplicationFactor()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> forceScaleDown(
      final ScaleRequest forceScaleDownRequest) {
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
  public ActorFuture<ClusterConfigurationChangeResponse> disableExporter(
      final ExporterDisableRequest exporterDisableRequest) {
    return handleRequest(
        exporterDisableRequest.dryRun(),
        new ExporterDisableRequestTransformer(exporterDisableRequest.exporterId()));
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
