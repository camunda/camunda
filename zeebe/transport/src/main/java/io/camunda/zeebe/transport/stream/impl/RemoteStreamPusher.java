/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.api.StreamResponseException;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamId;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamResponse;
import io.camunda.zeebe.transport.stream.impl.messages.StreamResponseDecoder;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
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
final class RemoteStreamPusher<P extends BufferWriter> {
  private static final Logger LOG = LoggerFactory.getLogger(RemoteStreamPusher.class);

  private final StreamResponseDecoder responseDecoder = new StreamResponseDecoder();
  private final ThrottledLogger pushErrorLogger = new ThrottledLogger(LOG, Duration.ofSeconds(5));
  private final ThrottledLogger pushWarnLogger = new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private final RemoteStreamMetrics metrics;
  private final Transport transport;
  private final Executor executor;

  RemoteStreamPusher(
      final Transport transport, final Executor executor, final RemoteStreamMetrics metrics) {
    this.metrics = Objects.requireNonNull(metrics, "must specify remote stream metrics");
    this.transport = Objects.requireNonNull(transport, "must provide a network transport");
    this.executor = Objects.requireNonNull(executor, "must provide an asynchronous executor");
  }

  public void pushAsync(
      final P payload, final RemoteStreamErrorHandler<P> errorHandler, final StreamId streamId) {
    Objects.requireNonNull(errorHandler, "must specify a error handler");

    try {
      Objects.requireNonNull(payload, "must specify a payload");
      executor.execute(
          () -> push(payload, instrumentingErrorHandler(errorHandler, streamId), streamId));
    } catch (final Exception e) {
      errorHandler.handleError(e, payload);
    }
  }

  private RemoteStreamErrorHandler<P> instrumentingErrorHandler(
      final RemoteStreamErrorHandler<P> errorHandler, final StreamId streamId) {
    return (error, payload) -> {
      if (error == null) {
        return;
      }

      if (error instanceof final StreamResponseException e) {
        logResponseError(streamId, payload, e);
        e.details().forEach(d -> metrics.pushTryFailed(d.code()));
      } else {
        pushWarnLogger.warn(
            "Failed to push (size = {}) to stream {}", payload.getLength(), streamId, error);
      }

      metrics.pushFailed();
      errorHandler.handleError(error, payload);
    };
  }

  private void logResponseError(
      final StreamId streamId, final P payload, final StreamResponseException e) {
    switch (e.code()) {
      case INVALID, MALFORMED ->
          pushErrorLogger.error(
              "Failed to push (size = {}) to stream {}, request could not be parsed",
              payload.getLength(),
              streamId,
              e);
      case EXHAUSTED ->
          LOG.trace(
              "Failed to push (size = {}) to stream {} after trying all clients",
              payload.getLength(),
              streamId,
              e);
      default ->
          pushWarnLogger.warn(
              "Failed to push (size = {}) to stream {}", payload.getLength(), streamId, e);
    }
  }

  private void push(
      final P payload, final RemoteStreamErrorHandler<P> errorHandler, final StreamId streamId) {
    final var request = new PushStreamRequest().streamId(streamId.streamId()).payload(payload);
    try {
      transport
          .send(request, streamId.receiver())
          .whenCompleteAsync(
              (response, error) -> onPush(payload, errorHandler, response, error), executor);
      LOG.trace("Pushed {} to stream {}", payload, streamId);
    } catch (final Exception e) {
      errorHandler.handleError(e, payload);
    }
  }

  private void onPush(
      final P payload,
      final RemoteStreamErrorHandler<P> errorHandler,
      final byte[] responseBuffer,
      final Throwable error) {
    if (error != null) {
      errorHandler.handleError(error, payload);
      return;
    }

    responseDecoder
        .decode(responseBuffer, new PushStreamResponse())
        .mapLeft(ErrorResponse::asException)
        .ifRightOrLeft(
            ok -> metrics.pushSucceeded(), failure -> errorHandler.handleError(failure, payload));
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
    CompletableFuture<byte[]> send(final PushStreamRequest request, final MemberId receiver)
        throws Exception;
  }
}
