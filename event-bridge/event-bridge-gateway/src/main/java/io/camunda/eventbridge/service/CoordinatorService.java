/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import io.camunda.eventbridge.broker.request.coordination.BrokerCommitRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerCreateTopicRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerDeleteTopicRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerHeartbeatRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerJoinGroupRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerLeaveGroupRequest;
import io.camunda.eventbridge.broker.request.coordination.BrokerListTopicsRequest;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.core.coordinator.CoordinatorRouting;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetResponse;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicResponse;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicResponse;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsResponse;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Component;

@Component
public class CoordinatorService {

  /**
   * The global topic registry is owned by a single coordinator shard (partition 1), so all topic
   * management routes there — unlike consumer groups, which shard by group id.
   */
  private static final int TOPIC_REGISTRY_SHARD = 1;

  private final BrokerClient brokerClient;
  private final ExecutorService executor;
  private final int coordinatorPartitionCount;

  public CoordinatorService(
      final BrokerClient brokerClient,
      final ServicesExecutorProvider executorProvider,
      final EventBridgeProperties properties) {
    this.brokerClient = brokerClient;
    executor = executorProvider.getExecutor();
    coordinatorPartitionCount = Math.max(1, properties.coordinator().partitionCount());
  }

  public CompletableFuture<JoinGroupResponse> joinGroup(final JoinGroupRequest request) {
    final var brokerRequest = new BrokerJoinGroupRequest().wrapRequest(request);
    brokerRequest.setPartitionId(shardFor(request.getGroupId()));
    return brokerClient
        .sendRequest(brokerRequest)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  public CompletableFuture<HeartbeatResponse> heartbeat(final HeartbeatRequest request) {
    final var brokerRequest = new BrokerHeartbeatRequest().wrapRequest(request);
    brokerRequest.setPartitionId(shardFor(request.getGroupId()));
    return brokerClient
        .sendRequest(brokerRequest)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  public CompletableFuture<LeaveGroupResponse> leaveGroup(final LeaveGroupRequest request) {
    final var brokerRequest = new BrokerLeaveGroupRequest().wrapRequest(request);
    brokerRequest.setPartitionId(shardFor(request.getGroupId()));
    return brokerClient
        .sendRequest(brokerRequest)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  public CompletableFuture<CommitOffsetResponse> commit(final CommitOffsetRequest request) {
    final var brokerRequest = new BrokerCommitRequest().wrapRequest(request);
    brokerRequest.setPartitionId(shardFor(request.getGroupId()));
    return brokerClient
        .sendRequest(brokerRequest)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  public CompletableFuture<CreateTopicResponse> createTopic(final CreateTopicRequest request) {
    final var brokerRequest = new BrokerCreateTopicRequest().wrapRequest(request);
    brokerRequest.setPartitionId(TOPIC_REGISTRY_SHARD);
    return brokerClient
        .sendRequest(brokerRequest)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  public CompletableFuture<DeleteTopicResponse> deleteTopic(final DeleteTopicRequest request) {
    final var brokerRequest = new BrokerDeleteTopicRequest().wrapRequest(request);
    brokerRequest.setPartitionId(TOPIC_REGISTRY_SHARD);
    return brokerClient
        .sendRequest(brokerRequest)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  public CompletableFuture<ListTopicsResponse> listTopics() {
    final var brokerRequest = new BrokerListTopicsRequest();
    brokerRequest.setPartitionId(TOPIC_REGISTRY_SHARD);
    return brokerClient
        .sendRequest(brokerRequest)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  /** Routes a group to its owning coordinator shard (consistent with the brokers' bootstrap). */
  private int shardFor(final String groupId) {
    return CoordinatorRouting.partitionForGroup(groupId, coordinatorPartitionCount);
  }
}
