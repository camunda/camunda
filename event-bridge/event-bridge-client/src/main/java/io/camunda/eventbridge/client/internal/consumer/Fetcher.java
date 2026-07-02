/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.client.FetchResult;
import java.util.concurrent.CompletableFuture;

/**
 * The narrow fetch capability the consumer collaborators need, decoupling them from the full client
 * facade. Implemented by the client so both the immediate-read and long-poll variants route through
 * the single HTTP transport.
 */
@FunctionalInterface
public interface Fetcher {

  /**
   * Fetches batches from a partition of a topic, optionally long-polling.
   *
   * @param minBytes minimum committed bytes the broker waits to accumulate before responding
   * @param maxWaitMs maximum time the broker parks the request before returning what it has
   */
  CompletableFuture<FetchResult> fetchFromTopic(
      String topic, int partitionId, long offset, int maxBytes, int minBytes, long maxWaitMs);

  /** Immediate read that returns whatever is available without waiting. */
  default CompletableFuture<FetchResult> fetchFromTopic(
      final String topic, final int partitionId, final long offset, final int maxBytes) {
    return fetchFromTopic(topic, partitionId, offset, maxBytes, 0, 0L);
  }
}
