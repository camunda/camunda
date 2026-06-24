/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Correlates a request-command to the reply the processor produces. The request side {@link
 * #register}s (allocating a request id and a future, stamped onto the command's metadata); the
 * processing side {@link #complete}s that future once the platform flushes the staged response
 * through {@link BridgingCommandResponseWriter} — i.e. only after the command commits. Requests are
 * registered on messaging threads and completed on the stream actor, so the registry is
 * thread-safe.
 */
public final class RequestResponseBridge {

  private final AtomicLong requestIds = new AtomicLong();
  private final ConcurrentHashMap<Long, CompletableFuture<byte[]>> pending =
      new ConcurrentHashMap<>();

  /** Allocates a request id + reply future to stamp on a command before it is written. */
  public Registration register() {
    final long requestId = requestIds.incrementAndGet();
    final var response = new CompletableFuture<byte[]>();
    pending.put(requestId, response);
    return new Registration(requestId, response);
  }

  /** Completes the waiting reply with the encoded response bytes; no-op if already resolved. */
  public void complete(final long requestId, final byte[] response) {
    final var future = pending.remove(requestId);
    if (future != null) {
      future.complete(response);
    }
  }

  /** Fails the waiting reply (e.g. the command could not be written); no-op if already resolved. */
  public void fail(final long requestId, final Throwable error) {
    final var future = pending.remove(requestId);
    if (future != null) {
      future.completeExceptionally(error);
    }
  }

  /** A pending request: the id stamped on the command and the future its reply will complete. */
  public record Registration(long requestId, CompletableFuture<byte[]> response) {}
}
