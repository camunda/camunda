/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import io.camunda.eventbridge.broker.request.publish.BrokerPublishRequest;
import io.camunda.eventbridge.protocol.request.PublishBatchResponse;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Component;

@Component
public class PublishService {

  private final BrokerClient brokerClient;
  private final ExecutorService executor;

  PublishService(final BrokerClient brokerClient, final ServicesExecutorProvider executorProvider) {
    this.brokerClient = brokerClient;
    executor = executorProvider.getExecutor();
  }

  /**
   * Publishes a batch to a partition of a per-topic Raft group ({@code event-bridge-topic-<name>}).
   * The BrokerClient resolves that group's partition leader from the topic's gossiped topology.
   *
   * @param partitionGroup the topic's Raft/routing group name
   * @param partitionId the partition within the topic
   * @param body the raw client request body (full EventBridgeBatch)
   * @return a future with the decoded response
   */
  public CompletableFuture<PublishBatchResponse> publish(
      final String partitionGroup, final int partitionId, final byte[] body) {
    final var validation = EventBridgeBatchValidator.validate(body);
    if (!validation.valid()) {
      return CompletableFuture.failedFuture(new IllegalArgumentException(validation.error()));
    }

    final var request = new BrokerPublishRequest();
    request.setPartitionGroup(partitionGroup);
    request.partitionId(partitionId);
    request.wrapBatch(body);

    return brokerClient.sendRequest(request).thenApplyAsync(BrokerResponse::getResponse, executor);
  }
}
