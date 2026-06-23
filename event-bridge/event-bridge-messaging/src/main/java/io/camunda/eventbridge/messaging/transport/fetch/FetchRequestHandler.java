/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.transport.fetch;

import io.atomix.cluster.messaging.ManagedPayload;
import io.camunda.eventbridge.messaging.fetch.EventStreamFetcher;
import io.camunda.eventbridge.transport.RequestHandler;
import java.util.concurrent.CompletableFuture;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Handles fetch requests for a single partition. Decodes the SBE {@code FetchRequest}, dispatches
 * it to the {@link EventStreamFetcher} (which reads on a virtual thread), and completes the future
 * with a {@link ManagedFetchResponseAdapter} — an SBE {@code FetchResponse} whose payload is
 * streamed zero-copy.
 *
 * <p>For immediate fetches (enough data available or maxWaitMs = 0), the future completes quickly.
 * For long-poll fetches, the future remains pending until new data arrives or the timeout expires.
 *
 * <p>The topic matches the group-prefixed name the gateway BrokerClient sends to (default group).
 */
public final class FetchRequestHandler implements RequestHandler {

  private static final String TOPIC_FORMAT = "default-fetch-api-%d";
  private static final String CONSUMER_ID = "gateway";

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
    final FetchRequest request;
    try {
      final var sbeRequest = new io.camunda.eventbridge.protocol.request.FetchRequest();
      sbeRequest.wrap(new UnsafeBuffer(requestBytes), 0, requestBytes.length);
      request =
          new FetchRequest(
              CONSUMER_ID,
              sbeRequest.getPartitionId(),
              sbeRequest.getFromPosition(),
              sbeRequest.getMaxBytes(),
              sbeRequest.getMinBytes(),
              sbeRequest.getMaxWaitMs());
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }

    final var responseFuture = new CompletableFuture<ManagedPayload>();
    fetchService
        .handleFetch(request)
        .whenComplete(
            (res, error) -> {
              if (error != null) {
                responseFuture.completeExceptionally(error);
              } else {
                responseFuture.complete(new ManagedFetchResponseAdapter(res));
              }
            });

    return responseFuture;
  }

  public static String topicName(final int partitionId) {
    return String.format(TOPIC_FORMAT, partitionId);
  }
}
