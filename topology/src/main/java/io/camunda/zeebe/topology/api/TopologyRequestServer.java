/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.topology.serializer.TopologyRequestsSerializer;
import java.util.function.Function;
import java.util.stream.Stream;

/** Server that receives the topology management requests */
public final class TopologyRequestServer implements AutoCloseable {

  private final TopologyManagementApi topologyManagementApi;
  private final ClusterCommunicationService communicationService;
  private final ConcurrencyControl executor;
  private final TopologyRequestsSerializer serializer;

  public TopologyRequestServer(
      final ClusterCommunicationService communicationService,
      final TopologyRequestsSerializer serializer,
      final TopologyManagementApi topologyManagementApi,
      final ConcurrencyControl executor) {
    this.topologyManagementApi = topologyManagementApi;
    this.communicationService = communicationService;
    this.serializer = serializer;
    this.executor = executor;
  }

  public void start() {
    registerAddMemberRequestsHandler();
    registerRemoveMemberRequestsHandler();
    registerJoinPartitionRequestsHandler();
    registerLeavePartitionRequestsHandler();
    registerReassignPartitionRequestHandler();
    registerScaleRequestHandler();
    registerGetTopologyQueryHandler();
  }

  @Override
  public void close() {
    Stream.of(TopologyRequestTopics.values())
        .toList()
        .forEach(topic -> communicationService.unsubscribe(topic.topic()));
  }

  private void registerAddMemberRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.ADD_MEMBER.topic(),
        serializer::decodeAddMembersRequest,
        request -> topologyManagementApi.addMembers(request).toCompletableFuture(),
        serializer::encodeTopologyChangeResponse);
  }

  private void registerRemoveMemberRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.REMOVE_MEMBER.topic(),
        serializer::decodeRemoveMembersRequest,
        request -> topologyManagementApi.removeMembers(request).toCompletableFuture(),
        serializer::encodeTopologyChangeResponse);
  }

  private void registerJoinPartitionRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.JOIN_PARTITION.topic(),
        serializer::decodeJoinPartitionRequest,
        request -> topologyManagementApi.joinPartition(request).toCompletableFuture(),
        serializer::encodeTopologyChangeResponse);
  }

  private void registerLeavePartitionRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.LEAVE_PARTITION.topic(),
        serializer::decodeLeavePartitionRequest,
        request -> topologyManagementApi.leavePartition(request).toCompletableFuture(),
        serializer::encodeTopologyChangeResponse);
  }

  private void registerReassignPartitionRequestHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.REASSIGN_PARTITIONS.topic(),
        serializer::decodeReassignPartitionsRequest,
        request -> topologyManagementApi.reassignPartitions(request).toCompletableFuture(),
        serializer::encodeTopologyChangeResponse);
  }

  private void registerScaleRequestHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.SCALE_MEMBERS.topic(),
        serializer::decodeScaleRequest,
        request -> topologyManagementApi.scaleMembers(request).toCompletableFuture(),
        serializer::encodeTopologyChangeResponse);
  }

  private void registerGetTopologyQueryHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.QUERY_TOPOLOGY.topic(),
        Function.identity(),
        request -> topologyManagementApi.getTopology().toCompletableFuture(),
        serializer::encode);
  }
}
