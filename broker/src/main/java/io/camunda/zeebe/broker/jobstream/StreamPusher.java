/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.jobstream.ImmutableStreamRegistry.StreamId;
import io.camunda.zeebe.protocol.impl.stream.PushStreamRequest;
import io.camunda.zeebe.stream.api.GatewayStreamer.ErrorHandler;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A naive implementation to push jobs out, which performs no retries of any kind, but reports
 * errors on failure.
 *
 * @param <P> the payload type to be pushed out
 */
final class StreamPusher<P extends BufferWriter> {
  private static final Logger LOG = LoggerFactory.getLogger(StreamPusher.class);
  private final StreamMetrics metrics = new StreamMetrics();

  private final StreamId streamId;
  private final Transport transport;
  private final Executor executor;

  StreamPusher(final StreamId streamId, final Transport transport, final Executor executor) {
    this.streamId = Objects.requireNonNull(streamId, "must specify a target stream ID");
    this.transport = Objects.requireNonNull(transport, "must provide a network transport");
    this.executor = Objects.requireNonNull(executor, "must provide an asynchronous executor");
  }

  public void pushAsync(final P payload, final ErrorHandler<P> errorHandler) {
    Objects.requireNonNull(payload, "must specify a payload");
    Objects.requireNonNull(errorHandler, "must specify a error handler");

    executor.execute(() -> push(payload, instrumentingErrorHandler(errorHandler)));
  }

  private ErrorHandler<P> instrumentingErrorHandler(final ErrorHandler<P> errorHandler) {
    return (error, payload) -> {
      if (error != null) {
        metrics.pushFailed();
        LOG.debug("Failed to push {} to stream {}", payload, streamId, error);
        errorHandler.handleError(error, payload);
      }
    };
  }

  private void push(final P payload, final ErrorHandler<P> errorHandler) {
    final var request = new PushStreamRequest().streamId(streamId.streamId()).payload(payload);
    try {
      transport
          .send(request, streamId.receiver())
          .whenCompleteAsync((ok, error) -> onPush(payload, errorHandler, error), executor);
      LOG.trace("Pushed {} to stream {}", payload, streamId);
    } catch (final Exception e) {
      errorHandler.handleError(e, payload);
    }
  }

  private void onPush(final P payload, final ErrorHandler<P> errorHandler, final Throwable error) {
    if (error != null) {
      errorHandler.handleError(error, payload);
    } else {
      metrics.pushSucceeded();
    }
  }

  /**
   * A small abstraction over the network transport. This allows for better testability, and also
   * removes the need for this class to know how communication occurs (e.g. which topic the message
   * is sent over)
   */
  interface Transport {

    /**
     * Sends the given request out to the given receiver. May throw errors, e.g. serialization
     * errors.
     *
     * @param request the request to send
     * @param receiver the expected target
     * @return a future which is completed when the request has been acknowledged by the receiver,
     *     or an error occurred
     * @throws Exception if an error occurs before the request is sent out, i.e. serialization error
     */
    CompletableFuture<Void> send(final PushStreamRequest request, final MemberId receiver)
        throws Exception;
  }
}
