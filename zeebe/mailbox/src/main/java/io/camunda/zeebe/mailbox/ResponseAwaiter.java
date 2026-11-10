/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the responses of the requests that one gateway sent through the mailbox. One call of
 * {@link #pollOnce()} asks the store for all pending requests at once.
 *
 * <p>A request that gets no response in time fails with a {@link TimeoutException}. It is never
 * sent again, because the first attempt may have been processed.
 *
 * <p>Thread-safe. The caller decides when to poll.
 */
public final class ResponseAwaiter {

  private static final Logger LOG = LoggerFactory.getLogger(ResponseAwaiter.class);

  private final MailboxStore store;
  private final GatewayId gatewayId;
  private final InstantSource clock;
  private final Map<Long, Pending> pending = new ConcurrentHashMap<>();

  public ResponseAwaiter(
      final MailboxStore store, final GatewayId gatewayId, final InstantSource clock) {
    this.store = store;
    this.gatewayId = gatewayId;
    this.clock = clock;
  }

  /**
   * Starts to wait for the response to a request.
   *
   * @param requestId the request id that was used in the inbox message
   * @param timeout how long to wait
   * @return a future with the serialized response, or a {@link TimeoutException}
   * @throws IllegalStateException if the gateway already waits for this request id
   */
  public CompletableFuture<byte[]> await(final long requestId, final Duration timeout) {
    final var future = new CompletableFuture<byte[]>();
    final var entry = new Pending(future, clock.instant().plus(timeout));
    if (pending.putIfAbsent(requestId, entry) != null) {
      throw new IllegalStateException("Already waiting for request " + requestId);
    }
    future.whenComplete((response, error) -> pending.remove(requestId, entry));
    return future;
  }

  /**
   * Collects the responses that are ready and fails the requests whose time is up.
   *
   * @return how many responses were handed to their requests
   */
  public int pollOnce() {
    if (pending.isEmpty()) {
      return 0;
    }

    var delivered = 0;
    try {
      final Set<Long> requestIds = Set.copyOf(pending.keySet());
      for (final OutboxResponse response : store.takeResponses(gatewayId, requestIds)) {
        final var entry = pending.remove(response.requestId());
        if (entry != null && entry.future.complete(response.payload())) {
          delivered++;
        }
      }
    } catch (final MailboxStoreException e) {
      LOG.warn("Could not collect the responses of gateway {}", gatewayId.value(), e);
    }

    failExpired();
    return delivered;
  }

  /** The number of requests that wait for a response. */
  public int pendingCount() {
    return pending.size();
  }

  private void failExpired() {
    final var now = clock.instant();
    pending.forEach(
        (requestId, entry) -> {
          if (!entry.deadline.isAfter(now) && pending.remove(requestId, entry)) {
            entry.future.completeExceptionally(
                new TimeoutException("No response to request " + requestId + " in time"));
          }
        });
  }

  private record Pending(CompletableFuture<byte[]> future, Instant deadline) {}
}
