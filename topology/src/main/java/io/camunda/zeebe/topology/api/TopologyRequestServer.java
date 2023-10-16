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

  private final TopologyManagementApi topologyManagementAPI;
  private final ClusterCommunicationService communicationService;
  private final ConcurrencyControl executor;
  private final TopologyRequestsSerializer serializer;

  TopologyRequestServer(
      final ClusterCommunicationService communicationService,
      final TopologyRequestsSerializer serializer,
      final TopologyManagementApi topologyManagementAPI,
      final ConcurrencyControl executor) {
    this.topologyManagementAPI = topologyManagementAPI;
    this.communicationService = communicationService;
    this.serializer = serializer;
    this.executor = executor;
  }

  void start() {
    registerAddMemberRequestsHandler();
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
        request -> toCompletableFuture(topologyManagementAPI.addMembers(request)),
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
