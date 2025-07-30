/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
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
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationRequestsSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Forwards all requests to the coordinator. */
public final class ClusterConfigurationManagementRequestSender {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private final ClusterCommunicationService communicationService;
  private final ClusterConfigurationCoordinatorSupplier coordinatorSupplier;
  private final ClusterConfigurationRequestsSerializer serializer;

  public ClusterConfigurationManagementRequestSender(
      final ClusterCommunicationService communicationService,
      final ClusterConfigurationCoordinatorSupplier coordinatorSupplier,
      final ClusterConfigurationRequestsSerializer serializer) {
    this.communicationService = communicationService;
    this.coordinatorSupplier = coordinatorSupplier;
    this.serializer = serializer;
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> addMembers(
      final AddMembersRequest addMembersRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.ADD_MEMBER.topic(),
        addMembersRequest,
        serializer::encodeAddMembersRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.REMOVE_MEMBER.topic(),
        removeMembersRequest,
        serializer::encodeRemoveMembersRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> purge(
      final PurgeRequest purgeRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.PURGE.topic(),
        purgeRequest,
        serializer::encodePurgeRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.JOIN_PARTITION.topic(),
        joinPartitionRequest,
        serializer::encodeJoinPartitionRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      leavePartition(final LeavePartitionRequest leavePartitionRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.LEAVE_PARTITION.topic(),
        leavePartitionRequest,
        serializer::encodeLeavePartitionRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      reassignPartitions(final ReassignPartitionsRequest reassignPartitionsRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.REASSIGN_PARTITIONS.topic(),
        reassignPartitionsRequest,
        serializer::encodeReassignPartitionsRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> scaleMembers(
      final BrokerScaleRequest scaleRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.SCALE_MEMBERS.topic(),
        scaleRequest,
        serializer::encodeScaleRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      forceScaleDown(final BrokerScaleRequest forceScaleDownRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.FORCE_SCALE_DOWN.topic(),
        forceScaleDownRequest,
        serializer::encodeScaleRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getNextCoordinator(forceScaleDownRequest.members()),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> scaleCluster(
      final ClusterScaleRequest clusterScaleRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.SCALE_CLUSTER.topic(),
        clusterScaleRequest,
        serializer::encodeClusterScaleRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> patchCluster(
      final ClusterPatchRequest clusterPatchRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.PATCH_CLUSTER.topic(),
        clusterPatchRequest,
        serializer::encodeClusterPatchRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      forceRemoveBrokers(final ForceRemoveBrokersRequest forceRemoveBrokersRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.FORCE_REMOVE_BROKERS.topic(),
        forceRemoveBrokersRequest,
        serializer::encodeForceRemoveBrokersRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getNextCoordinatorExcluding(
            forceRemoveBrokersRequest.membersToRemove()),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      disableExporter(final ExporterDisableRequest exporterDisableRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.DISABLE_EXPORTER.topic(),
        exporterDisableRequest,
        serializer::encodeExporterDisableRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      deleteExporter(final ExporterDeleteRequest exporterDeleteRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.DELETE_EXPORTER.topic(),
        exporterDeleteRequest,
        serializer::encodeExporterDeleteRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      enableExporter(final ExporterEnableRequest enableRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.ENABLE_EXPORTER.topic(),
        enableRequest,
        serializer::encodeExporterEnableRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfiguration>> getTopology() {
    return communicationService.send(
        ClusterConfigurationRequestTopics.QUERY_TOPOLOGY.topic(),
        new byte[0],
        Function.identity(),
        serializer::decodeClusterTopologyResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfiguration>> cancelTopologyChange(
      final ClusterConfigurationManagementRequest.CancelChangeRequest request) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.CANCEL_CHANGE.topic(),
        request,
        serializer::encodeCancelChangeRequest,
        serializer::decodeClusterTopologyResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      updateRoutingState(final UpdateRoutingStateRequest updateRoutingStateRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.UPDATE_ROUTING_STATE.topic(),
        updateRoutingStateRequest,
        serializer::encodeUpdateRoutingStateRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }
}
