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
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;
import io.camunda.zeebe.topology.serializer.TopologyRequestsSerializer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

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

  public CompletableFuture<TopologyChangeStatus> addMembers(
      final AddMembersRequest addMembersRequest) {
    return communicationService.send(
        TopologyRequestTopics.ADD_MEMBER.topic(),
        addMembersRequest,
        serializer::encodeAddMembersRequest,
        serializer::decodeTopologyChangeStatus,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<TopologyChangeStatus> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    return communicationService.send(
        TopologyRequestTopics.REMOVE_MEMBER.topic(),
        removeMembersRequest,
        serializer::encodeRemoveMembersRequest,
        serializer::decodeTopologyChangeStatus,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<TopologyChangeStatus> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    return communicationService.send(
        TopologyRequestTopics.JOIN_PARTITION.topic(),
        joinPartitionRequest,
        serializer::encodeJoinPartitionRequest,
        serializer::decodeTopologyChangeStatus,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<TopologyChangeStatus> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {
    return communicationService.send(
        TopologyRequestTopics.LEAVE_PARTITION.topic(),
        leavePartitionRequest,
        serializer::encodeLeavePartitionRequest,
        serializer::decodeTopologyChangeStatus,
        coordinator,
        TIMEOUT);
  }

  public CompletableFuture<TopologyChangeStatus> reassignPartitions(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    return communicationService.send(
        TopologyRequestTopics.REASSIGN_PARTITIONS.topic(),
        reassignPartitionsRequest,
        serializer::encodeReassignPartitionsRequest,
        serializer::decodeTopologyChangeStatus,
        coordinator,
        TIMEOUT);
  }
}
