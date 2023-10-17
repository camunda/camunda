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
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;
import io.camunda.zeebe.topology.serializer.TopologyRequestsSerializer;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Server that receives the topology management requests */
public final class TopologyRequestServer implements AutoCloseable {

  private final TopologyManagementApi topologyManagementApi;
  private final ClusterCommunicationService communicationService;
  private final ConcurrencyControl executor;
  private final TopologyRequestsSerializer serializer;

  TopologyRequestServer(
      final ClusterCommunicationService communicationService,
      final TopologyRequestsSerializer serializer,
      final TopologyManagementApi topologyManagementApi,
      final ConcurrencyControl executor) {
    this.topologyManagementApi = topologyManagementApi;
    this.communicationService = communicationService;
    this.serializer = serializer;
    this.executor = executor;
  }

  void start() {
    registerAddMemberRequestsHandler();
    registerJoinPartitionRequestsHandler();
    registerLeavePartitionRequestsHandler();
    registerReassignPartitionRequestHandler();
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
        request -> toCompletableFuture(topologyManagementApi.addMembers(request)),
        serializer::encode);
  }

  private void registerJoinPartitionRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.JOIN_PARTITION.topic(),
        serializer::decodeJoinPartitionRequest,
        request -> toCompletableFuture(topologyManagementApi.joinPartition(request)),
        serializer::encode);
  }

  private void registerLeavePartitionRequestsHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.LEAVE_PARTITION.topic(),
        serializer::decodeLeavePartitionRequest,
        request -> toCompletableFuture(topologyManagementApi.leavePartition(request)),
        serializer::encode);
  }

  private void registerReassignPartitionRequestHandler() {
    communicationService.replyTo(
        TopologyRequestTopics.REASSIGN_PARTITIONS.topic(),
        serializer::decodeReassignPartitionsRequest,
        request -> toCompletableFuture(topologyManagementApi.reassignPartitions(request)),
        serializer::encode);
  }

  private CompletableFuture<TopologyChangeStatus> toCompletableFuture(
      final ActorFuture<TopologyChangeStatus> resultFuture) {
    final var future = new CompletableFuture<TopologyChangeStatus>();
    executor.runOnCompletion(
        resultFuture,
        (status, error) -> {
          if (error == null) {
            future.complete(status);
          } else {
            future.completeExceptionally(error);
          }
        });
    return future;
  }
}
