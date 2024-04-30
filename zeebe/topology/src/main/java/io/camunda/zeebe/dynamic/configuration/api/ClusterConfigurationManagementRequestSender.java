/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.configuration.api;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.configuration.api.ClusterConfigurationManagementRequest.ScaleRequest;
import io.camunda.zeebe.dynamic.configuration.serializer.ClusterConfigurationRequestsSerializer;
import io.camunda.zeebe.dynamic.configuration.state.ClusterConfiguration;
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
      final ScaleRequest scaleRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.SCALE_MEMBERS.topic(),
        scaleRequest,
        serializer::encodeScaleRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
      forceScaleDown(final ScaleRequest forceScaleDownRequest) {
    return communicationService.send(
        ClusterConfigurationRequestTopics.FORCE_SCALE_DOWN.topic(),
        forceScaleDownRequest,
        serializer::encodeScaleRequest,
        serializer::decodeTopologyChangeResponse,
        coordinatorSupplier.getNextCoordinator(forceScaleDownRequest.members()),
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
}
