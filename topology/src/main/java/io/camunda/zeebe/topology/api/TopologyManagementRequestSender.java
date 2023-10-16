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
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;
import io.camunda.zeebe.topology.serializer.TopologyRequestsSerializer;
import java.time.Duration;

/** Forwards all requests to the coordinator. */
final class TopologyManagementRequestSender implements TopologyManagementApi {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private final ClusterCommunicationService communicationService;
  private final MemberId coordinator;
  private final ConcurrencyControl executor;
  private final TopologyRequestsSerializer serializer;

  public TopologyManagementRequestSender(
      final ClusterCommunicationService communicationService,
      final MemberId coordinator,
      final TopologyRequestsSerializer serializer,
      final ConcurrencyControl executor) {
    this.communicationService = communicationService;
    this.coordinator = coordinator;
    this.executor = executor;
    this.serializer = serializer;
  }

  @Override
  public ActorFuture<TopologyChangeStatus> addMembers(final AddMembersRequest addMembersRequest) {
    final ActorFuture<TopologyChangeStatus> resultFuture = executor.createFuture();
    final var responseFuture =
        communicationService.send(
            TopologyRequestTopics.ADD_MEMBER.topic(),
            addMembersRequest,
            serializer::encode,
            serializer::decodeTopologyChangeStatus,
            coordinator,
            TIMEOUT);
    responseFuture
        .thenAccept(resultFuture::complete)
        .exceptionally(
            error -> {
              resultFuture.completeExceptionally(error);
              return null;
            });
    return resultFuture;
  }
}
