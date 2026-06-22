/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import io.camunda.eventbridge.broker.request.fetch.BrokerFetchRequest;
import io.camunda.eventbridge.protocol.request.FetchResponse;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Component;

/**
 * Fetches batches from a partition's leader through the {@link BrokerClient} — same routing, leader
 * resolution, and {@code NOT_LEADER} retry as publish/poll. The broker streams the response payload
 * zero-copy; the gateway receives a decoded {@link FetchResponse}.
 */
@Component
public class FetchService {

  private final BrokerClient brokerClient;
  private final ExecutorService executor;

  FetchService(final BrokerClient brokerClient, final ServicesExecutorProvider executorProvider) {
    this.brokerClient = brokerClient;
    executor = executorProvider.getExecutor();
  }

  public CompletableFuture<FetchResponse> fetch(
      final int partitionId,
      final long offset,
      final int maxBytes,
      final int minBytes,
      final long maxWaitMs) {

    // "From start" sentinel (<= 0): positions begin at 1 and scanning from 0 is out-of-range, so
    // clamp to the first position. (POC: assumes the earliest entries are not retention-trimmed.)
    final long readOffset = offset <= 0 ? 1 : offset;

    final var request = new BrokerFetchRequest();
    request.setup(partitionId, readOffset, maxBytes, minBytes, maxWaitMs);

    return brokerClient.sendRequest(request).thenApplyAsync(BrokerResponse::getResponse, executor);
  }
}
