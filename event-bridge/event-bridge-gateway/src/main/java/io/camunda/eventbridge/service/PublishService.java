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
import java.util.function.BiFunction;
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
   * Publishes a batch of events to the given partition.
   *
   * @param body the raw client request body (full EventBridgeBatch)
   * @return a future with the decoded response
   */
  public CompletableFuture<PublishBatchResponse> publish(final byte[] body) {
    final var validation = EventBridgeBatchValidator.validate(body);
    if (!validation.valid()) {
      return CompletableFuture.failedFuture(new IllegalArgumentException(validation.error()));
    }

    return brokerClient
        .sendRequest(new BrokerPublishRequest().wrapBatch(body))
        .handleAsync(handleBrokerResponse(), executor)
        .thenApplyAsync(BrokerResponse::getResponse, executor);
  }

  private <R> BiFunction<BrokerResponse<R>, Throwable, BrokerResponse<R>> handleBrokerResponse() {
    return (response, error) -> {
      if (error != null) {
        // TODO
        System.out.println("FAILED " + error.getMessage());
      }
      return response;
    };
  }
}
