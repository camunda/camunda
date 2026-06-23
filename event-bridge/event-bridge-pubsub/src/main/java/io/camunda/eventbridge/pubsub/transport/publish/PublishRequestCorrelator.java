/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.transport.publish;

import io.camunda.eventbridge.protocol.RejectionReason;
import io.camunda.eventbridge.pubsub.publish.PublishResponse;
import io.camunda.eventbridge.pubsub.stream.EventStreamListener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.concurrent.IdGenerator;

/**
 * Correlates publish requests to responses. Generates request IDs, stores pending futures, and
 * completes them when the event stream commits or fails.
 *
 * <p>Thread-safe — used from Netty threads ({@link #register}, {@link #cancel}) and actor threads
 * ({@link #onCommitted}, {@link #onFailed}).
 *
 * <p>Implements {@link EventStreamListener} so it can be wired directly as the listener on the
 * event stream.
 */
public final class PublishRequestCorrelator implements EventStreamListener {

  private final IdGenerator requestIdGenerator;
  private final ConcurrentHashMap<Long, CompletableFuture<byte[]>> pendingResponses =
      new ConcurrentHashMap<>();

  public PublishRequestCorrelator(final IdGenerator requestIdGenerator) {
    this.requestIdGenerator = requestIdGenerator;
  }

  /**
   * Registers a new request. Generates a unique request ID and stores the response future. Called
   * on Netty threads.
   */
  public Registration register() {
    final var requestId = requestIdGenerator.nextId();
    final var future = new CompletableFuture<byte[]>();
    pendingResponses.put(requestId, future);
    return new Registration(requestId, future);
  }

  /**
   * Removes a pending request without completing it. Used when the write to the ring buffer fails.
   * Called on Netty threads.
   */
  public void cancel(final long requestId) {
    pendingResponses.remove(requestId);
  }

  @Override
  public void onCommitted(final long requestId, final long firstPosition, final long lastPosition) {
    final var future = pendingResponses.remove(requestId);
    if (future != null) {
      future.complete(PublishResponse.success(firstPosition, lastPosition));
    }
  }

  @Override
  public void onFailed(final long requestId, final Throwable error) {
    final var future = pendingResponses.remove(requestId);
    if (future != null) {
      final var reason = RejectionReason.WRITE_FAILURE;
      future.complete(PublishResponse.error(reason, error));
    }
  }

  public record Registration(long requestId, CompletableFuture<byte[]> future) {}
}
