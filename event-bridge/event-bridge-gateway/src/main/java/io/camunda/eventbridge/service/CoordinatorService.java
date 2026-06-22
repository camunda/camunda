/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import io.camunda.eventbridge.broker.request.coordination.BrokerHeartbeatRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerJoinGroupRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerLeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Component;

@Component
public class CoordinatorService {

  private final BrokerClient brokerClient;
  private final ExecutorService executor;

  public CoordinatorService(
      final BrokerClient brokerClient, final ServicesExecutorProvider executorProvider) {
    this.brokerClient = brokerClient;
    executor = executorProvider.getExecutor();
  }

  public CompletableFuture<JoinGroupResponse> joinGroup(final JoinGroupRequest request) {
    return brokerClient
        .sendRequest(new BrokerJoinGroupRequest().wrapRequest(request))
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  public CompletableFuture<HeartbeatResponse> heartbeat(final HeartbeatRequest request) {
    return brokerClient
        .sendRequest(new BrokerHeartbeatRequest().wrapRequest(request))
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  public CompletableFuture<LeaveGroupResponse> leaveGroup(final LeaveGroupRequest request) {
    return brokerClient
        .sendRequest(new BrokerLeaveGroupRequest().wrapRequest(request))
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }
}
