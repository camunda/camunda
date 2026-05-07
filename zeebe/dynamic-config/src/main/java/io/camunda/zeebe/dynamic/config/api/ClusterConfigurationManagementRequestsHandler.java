/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the requests for the configuration management. This is expected be running on the
 * coordinator node.
 */
public final class ClusterConfigurationManagementRequestsHandler
    implements ClusterConfigurationManagementApi {
  private static final Logger LOG =
      LoggerFactory.getLogger(ClusterConfigurationManagementRequestsHandler.class);
  private static final String SCALE_MESSAGE_NAME = "cluster_modification";
  private static final String SCALE_PROCESS_ID = "scale_operation_executor";
  private static final String EXPORTER_OPERATION_PROCESS_ID = "exporter_operation_executor";

  private final ConfigurationChangeCoordinator coordinator;
  private final ConcurrencyControl executor;
  private final MemberId localMemberId;
  private volatile CamundaClient camundaClient;

  public ClusterConfigurationManagementRequestsHandler(
      final ConfigurationChangeCoordinator coordinator,
      final MemberId localMemberId,
      final ConcurrencyControl executor) {
    this.coordinator = coordinator;
    this.executor = executor;
    this.localMemberId = localMemberId;
  }

  /** Enables BPMN-orchestrated scale operations. Must be called after the process engine starts. */
  public void setCamundaClient(final CamundaClient client) {
    camundaClient = client;
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> addMembers(
      final AddMembersRequest addMembersRequest) {
    final var transformer = new AddMembersTransformer(addMembersRequest.members());
    if (!addMembersRequest.dryRun() && camundaClient != null) {
      return handleBpmnScale(transformer);
    }
    return handleRequest(addMembersRequest.dryRun(), transformer);
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    final var transformer = new RemoveMembersTransformer(removeMembersRequest.members());
    if (!removeMembersRequest.dryRun() && camundaClient != null) {
      return handleBpmnScale(transformer);
    }
    return handleRequest(removeMembersRequest.dryRun(), transformer);
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
    final var transformer =
        new ScaleRequestTransformer(scaleRequest.members(), scaleRequest.newReplicationFactor());
    if (!scaleRequest.dryRun() && camundaClient != null) {
      return handleBpmnScale(transformer);
    }
    return handleRequest(scaleRequest.dryRun(), transformer);
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
    final var transformer =
        new ClusterPatchRequestTransformer(
            clusterPatchRequest.membersToAdd(),
            clusterPatchRequest.membersToRemove(),
            clusterPatchRequest.newPartitionCount(),
            clusterPatchRequest.newReplicationFactor());
    if (!clusterPatchRequest.dryRun() && camundaClient != null) {
      return handleBpmnScale(transformer);
    }
    return handleRequest(clusterPatchRequest.dryRun(), transformer);
  }

  @Override
  public ActorFuture<ClusterConfigurationChangeResponse> updateRoutingState(
      final UpdateRoutingStateRequest updateRoutingStateRequest) {
    return handleRequest(
        updateRoutingStateRequest.dryRun(),
        new UpdateRoutingStateTransformer(updateRoutingStateRequest.routingState()));
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
    final var transformer =
        new ExporterDisableRequestTransformer(exporterDisableRequest.exporterId());
    if (!exporterDisableRequest.dryRun() && camundaClient != null) {
      return handleBpmnExporterOperation(
          transformer, exporterDisableRequest.exporterId(), "disable", Optional.empty());
    }
    return handleRequest(exporterDisableRequest.dryRun(), transformer);
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
    final var transformer =
        new ExporterEnableRequestTransformer(
            enableRequest.exporterId(), enableRequest.initializeFrom());
    if (!enableRequest.dryRun() && camundaClient != null) {
      return handleBpmnExporterOperation(
          transformer, enableRequest.exporterId(), "enable", enableRequest.initializeFrom());
    }
    return handleRequest(enableRequest.dryRun(), transformer);
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

  /**
   * Validates the scale request via simulation, then fires a BPMN message to run the actual
   * operation. The response reflects the planned change so the caller can see what will happen.
   */
  private ActorFuture<ClusterConfigurationChangeResponse> handleBpmnScale(
      final ConfigurationChangeRequest request) {
    return coordinator
        .simulateOperations(request)
        .thenApply(
            result -> {
              final var currentMembers = result.currentConfiguration().members().keySet();
              final var finalMembers = result.finalConfiguration().members().keySet();

              final var toAdd =
                  finalMembers.stream()
                      .filter(m -> !currentMembers.contains(m))
                      .map(MemberId::nodeIdx)
                      .sorted()
                      .collect(Collectors.toList());
              final var toRemove =
                  currentMembers.stream()
                      .filter(m -> !finalMembers.contains(m))
                      .map(MemberId::nodeIdx)
                      .sorted()
                      .collect(Collectors.toList());

              final String direction = toRemove.isEmpty() ? "up" : "down";
              final String requestId = UUID.randomUUID().toString();

              final var client = camundaClient;
              if (client != null) {
                client
                    .newPublishMessageCommand()
                    .messageName(SCALE_MESSAGE_NAME)
                    .correlationKey(requestId)
                    .variables(
                        Map.of(
                            "request_id", requestId,
                            "processor_id", SCALE_PROCESS_ID,
                            "membersToAdd", toAdd,
                            "membersToRemove", toRemove,
                            "direction", direction))
                    .send()
                    .exceptionally(
                        e -> {
                          LOG.warn(
                              "Failed to publish scale BPMN message for request {}", requestId, e);
                          return null;
                        });
                LOG.info(
                    "Scale request {} dispatched to BPMN: add={} remove={}",
                    requestId,
                    toAdd,
                    toRemove);
              }

              return new ClusterConfigurationChangeResponse(
                  result.changeId(),
                  result.currentConfiguration().members(),
                  result.finalConfiguration().members(),
                  result.operations());
            },
            executor);
  }

  private ActorFuture<ClusterConfigurationChangeResponse> handleBpmnExporterOperation(
      final ConfigurationChangeRequest request,
      final String exporterId,
      final String direction,
      final Optional<String> initializeFrom) {
    return coordinator
        .simulateOperations(request)
        .thenApply(
            result -> {
              final String requestId = UUID.randomUUID().toString();

              final var client = camundaClient;
              if (client != null) {
                final var vars = new HashMap<String, Object>();
                vars.put("request_id", requestId);
                vars.put("processor_id", EXPORTER_OPERATION_PROCESS_ID);
                vars.put("exporterId", exporterId);
                vars.put("direction", direction);
                initializeFrom.ifPresent(v -> vars.put("initializeFrom", v));

                client
                    .newPublishMessageCommand()
                    .messageName(SCALE_MESSAGE_NAME)
                    .correlationKey(requestId)
                    .variables(vars)
                    .send()
                    .exceptionally(
                        e -> {
                          LOG.warn(
                              "Failed to publish exporter BPMN message for request {}",
                              requestId,
                              e);
                          return null;
                        });
                LOG.info(
                    "Exporter {} - {} request {} dispatched to BPMN",
                    exporterId,
                    direction,
                    requestId);
              }

              return new ClusterConfigurationChangeResponse(
                  result.changeId(),
                  result.currentConfiguration().members(),
                  result.finalConfiguration().members(),
                  result.operations());
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
