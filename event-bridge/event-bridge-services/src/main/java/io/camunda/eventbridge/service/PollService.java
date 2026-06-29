/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import io.camunda.eventbridge.broker.request.poll.BrokerPollRequest;
import io.camunda.eventbridge.protocol.request.PollResponse;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Component;

/** Routes a consumer poll to the partition leader over the broker transport (copy-based SBE). */
@Component
public class PollService {

  private final BrokerClient brokerClient;
  private final ExecutorService executor;

  PollService(final BrokerClient brokerClient, final ServicesExecutorProvider executorProvider) {
    this.brokerClient = brokerClient;
    executor = executorProvider.getExecutor();
  }

  public CompletableFuture<PollResponse> poll(
      final int partitionId, final long fromPosition, final int maxRecords) {

    final var request = new BrokerPollRequest();
    request.setup(partitionId, fromPosition, maxRecords);

    return brokerClient.sendRequest(request).thenApplyAsync(BrokerResponse::getResponse, executor);
  }
}
