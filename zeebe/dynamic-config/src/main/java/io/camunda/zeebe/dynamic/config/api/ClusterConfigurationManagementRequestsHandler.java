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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterZoneMigrationRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeResult;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry.RequestValidator;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
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
  private final RequestValidator requestValidator;

  public ClusterConfigurationManagementRequestsHandler(
      final ConfigurationChangeCoordinator coordinator,
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final RequestValidator requestValidator) {
    this.coordinator = coordinator;
    this.executor = executor;
    this.localMemberId = localMemberId;
    this.requestValidator = requestValidator;
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> addMembers(
      final AddMembersRequest addMembersRequest) {
    return handleRequest(addMembersRequest, req -> new AddMembersTransformer(req.members()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    return handleRequest(removeMembersRequest, req -> new RemoveMembersTransformer(req.members()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    return handleRequest(
        joinPartitionRequest,
        req ->
            configuration ->
                Either.right(
                    List.of(
                        new PartitionJoinOperation(
                            req.memberId(), req.partitionId(), req.priority()))));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {
    return handleRequest(
        leavePartitionRequest,
        req ->
            configuration ->
                Either.right(
                    List.of(new PartitionLeaveOperation(req.memberId(), req.partitionId(), 1))));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> reassignPartitions(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    return handleRequest(
        reassignPartitionsRequest, req -> new PartitionReassignRequestTransformer(req.members()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> scaleMembers(
      final BrokerScaleRequest scaleRequest) {
    return handleRequest(
        scaleRequest,
        req -> new ScaleRequestTransformer(req.members(), req.newReplicationFactor()));
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
        forceScaleDownRequest,
        req -> new ForceScaleDownRequestTransformer(req.members(), localMemberId));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> scaleCluster(
      final ClusterScaleRequest clusterScaleRequest) {
    return handleRequest(
        clusterScaleRequest,
        req ->
            new ClusterScaleRequestTransformer(
                req.brokerCount(),
                req.newPartitionCount(),
                req.newReplicationFactor(),
                req.zone()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> patchCluster(
      final ClusterPatchRequest clusterPatchRequest) {
    return handleRequest(
        clusterPatchRequest,
        req ->
            new ClusterPatchRequestTransformer(
                req.membersToAdd(),
                req.membersToRemove(),
                req.newPartitionCount(),
                req.newReplicationFactor()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> updateRoutingState(
      final UpdateRoutingStateRequest updateRoutingStateRequest) {
    return handleRequest(
        updateRoutingStateRequest, req -> new UpdateRoutingStateTransformer(req.routingState()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> updatePartitionDistribution(
      final UpdatePartitionDistributorConfigRequest request) {
    return handleRequest(request, req -> new UpdatePartitionDistributionTransformer(req.config()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> migrateZone(
      final ClusterZoneMigrationRequest zoneMigrationRequest) {
    return handleRequest(
        zoneMigrationRequest, req -> new ZoneMigrationRequestTransformer(req.zone()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> purge(final PurgeRequest purgeRequest) {
    return handleRequest(purgeRequest, req -> new PurgeRequestTransformer());
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> forceRemoveBrokers(
      final ForceRemoveBrokersRequest forceRemoveBrokersRequest) {
    return handleRequest(
        forceRemoveBrokersRequest,
        req -> new ForceRemoveBrokersRequestTransformer(req.membersToRemove(), localMemberId));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> disableExporter(
      final ExporterDisableRequest exporterDisableRequest) {
    return handleRequest(
        exporterDisableRequest, req -> new ExporterDisableRequestTransformer(req.exporterId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> deleteExporter(
      final ExporterDeleteRequest exporterDeleteRequest) {
    return handleRequest(
        exporterDeleteRequest, req -> new ExporterDeleteRequestTransformer(req.exporterId()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> enableExporter(
      final ExporterEnableRequest enableRequest) {
    return handleRequest(
        enableRequest,
        req -> new ExporterEnableRequestTransformer(req.exporterId(), req.initializeFrom()));
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> modeChange(
      final ModeChangeRequest modeChangeRequest) {
    return handleRequest(modeChangeRequest, req -> new ModeChangeRequestTransformer(req.mode()));
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

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> restore(final RestoreRequest request) {
    return handleRequest(request, RestoreRequestTransformer::new);
  }

  /**
   * Runs the (optional, blocking) validator registered for the request type, on the same actor
   * thread that {@link RequestValidatorRegistry} is registered/deregistered on, before delegating
   * to the coordinator with the {@link ConfigurationChangeRequest} produced by {@code transform}
   * from the (possibly rewritten) request.
   */
  @SuppressWarnings("unchecked")
  private <T extends ClusterConfigurationManagementRequest>
      ActorFuture<ClusterConfigurationChangeResponse> handleRequest(
          final T request, final Function<T, ConfigurationChangeRequest> transform) {
    return executor
        .call(() -> requestValidator.validate(request))
        .andThen(
            (result, error) -> {
              if (error != null) {
                return CompletableActorFuture.completedExceptionally(error);
              }
              if (result.isLeft()) {
                return CompletableActorFuture.completedExceptionally(
                    (RuntimeException) result.getLeft());
              }
              final var validatedRequest = (T) result.get();
              return handleRequest(request.dryRun(), transform.apply(validatedRequest));
            },
            executor);
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
