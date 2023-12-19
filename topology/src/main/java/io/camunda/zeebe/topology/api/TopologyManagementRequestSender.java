/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ScaleRequest;
import io.camunda.zeebe.topology.serializer.TopologyRequestsSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Forwards all requests to the coordinator. */
public final class TopologyManagementRequestSender {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private final ClusterCommunicationService communicationService;
  private final MemberId coordinator;
  private final TopologyRequestsSerializer serializer;

  public TopologyManagementRequestSender(
      final ClusterCommunicationService communicationService,
      final MemberId coordinator,
      final TopologyRequestsSerializer serializer) {
    this.communicationService = communicationService;
    this.coordinator = coordinator;
    this.serializer = serializer;
  }

  public CompletableFuture<Either<ErrorResponse, TopologyChangeResponse>> addMembers(
      final AddMembersRequest addMembersRequest) {
    return communicationService.send(
        TopologyRequestTopics.ADD_MEMBER.topic(),
        addMembersRequest,
        serializer::encodeAddMembersRequest,
        serializer::decodeTopologyChangeResponse,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, TopologyChangeResponse>> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    return communicationService.send(
        TopologyRequestTopics.REMOVE_MEMBER.topic(),
        removeMembersRequest,
        serializer::encodeRemoveMembersRequest,
        serializer::decodeTopologyChangeResponse,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, TopologyChangeResponse>> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    return communicationService.send(
        TopologyRequestTopics.JOIN_PARTITION.topic(),
        joinPartitionRequest,
        serializer::encodeJoinPartitionRequest,
        serializer::decodeTopologyChangeResponse,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, TopologyChangeResponse>> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {
    return communicationService.send(
        TopologyRequestTopics.LEAVE_PARTITION.topic(),
        leavePartitionRequest,
        serializer::encodeLeavePartitionRequest,
        serializer::decodeTopologyChangeResponse,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, TopologyChangeResponse>> reassignPartitions(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    return communicationService.send(
        TopologyRequestTopics.REASSIGN_PARTITIONS.topic(),
        reassignPartitionsRequest,
        serializer::encodeReassignPartitionsRequest,
        serializer::decodeTopologyChangeResponse,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, TopologyChangeResponse>> scaleMembers(
      final ScaleRequest scaleRequest) {
    return communicationService.send(
        TopologyRequestTopics.SCALE_MEMBERS.topic(),
        scaleRequest,
        serializer::encodeScaleRequest,
        serializer::decodeTopologyChangeResponse,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterTopology>> getTopology() {
    return communicationService.send(
        TopologyRequestTopics.QUERY_TOPOLOGY.topic(),
        new byte[0],
        Function.identity(),
        serializer::decodeClusterTopologyResponse,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<Either<ErrorResponse, ClusterTopology>> cancelTopologyChange(
      final TopologyManagementRequest.CancelChangeRequest request) {
    return communicationService.send(
        TopologyRequestTopics.CANCEL_CHANGE.topic(),
        request,
        serializer::encodeCancelChangeRequest,
        serializer::decodeClusterTopologyResponse,
        coordinator,
        TIMEOUT);
  }
}
