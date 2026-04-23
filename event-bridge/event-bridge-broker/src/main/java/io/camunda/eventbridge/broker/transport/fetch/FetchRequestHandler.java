/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport.fetch;

import io.atomix.cluster.messaging.ManagedPayload;
import io.camunda.eventbridge.broker.fetch.EventStreamFetcher;
import io.camunda.eventbridge.broker.transport.RequestHandler;
import java.util.concurrent.CompletableFuture;

/**
 * Handles fetch requests for a single partition. Decodes the request, dispatches it to the {@link
 * EventStreamFetcher} which reads on a virtual thread, and completes the future with the serialized
 * response.
 *
 * <p>For immediate fetches (enough data available or maxWaitMs = 0), the future completes quickly.
 * For long-poll fetches, the future remains pending until new data arrives or the timeout expires.
 */
public final class FetchRequestHandler implements RequestHandler {

  private static final String TOPIC_FORMAT = "fetch-api-%d";

  private final int partitionId;
  private final EventStreamFetcher fetchService;

  public FetchRequestHandler(final int partitionId, final EventStreamFetcher fetchService) {
    this.partitionId = partitionId;
    this.fetchService = fetchService;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    return null;
  }

  @Override
  public CompletableFuture<ManagedPayload> handleWithManagedPayload(final byte[] requestBytes) {
    final var request = new FetchRequest("foo", partitionId, 999, 1024, 1, 1000 * 60 * 5);

    final var responseFuture = new CompletableFuture<ManagedPayload>();
    fetchService
        .handleFetch(request)
        .whenComplete(
            (res, error) -> {
              responseFuture.complete(new ManagedFetchResponseAdapter(res));
            });

    return responseFuture;
  }

  public static String topicName(final int partitionId) {
    return String.format(TOPIC_FORMAT, partitionId);
  }
}
