/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamBlockedException;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.api.NoSuchStreamException;
import io.camunda.zeebe.transport.stream.api.StreamExhaustedException;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles forwarding pushed payloads to aggregated client streams. It will try each underlying
 * stream once until either one succeeds or it exhausts all of them.
 */
final class ClientStreamPusher {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClientStreamPusher.class);
  private static final Logger PUSH_ERROR_LOGGER =
      new ThrottledLogger(LOGGER, Duration.ofSeconds(1));

  private final ClientStreamMetrics metrics;

  ClientStreamPusher(final ClientStreamMetrics metrics) {
    this.metrics = metrics;
  }

  /**
   * Pushes the given payload downstream to any of the stream's clients. On success, will complete
   * the given future with null.
   *
   * <p>May complete exceptionally with:
   *
   * <ul>
   *   <li>{@link StreamExhaustedException} if all clients failed
   *   <li>{@link NoSuchStreamException} if all clients were removed since the push was received but
   *       before it was forwarded
   * </ul>
   *
   * @param stream the stream to push to
   * @param payload the payload to push
   * @param future the future to complete on success or failure
   */
  void push(
      final AggregatedClientStream<?> stream,
      final DirectBuffer payload,
      final ActorFuture<Void> future) {
    final var streams = stream.clientStreams().values();
    if (streams.isEmpty()) {
      future.completeExceptionally(
          new NoSuchStreamException(
              "Cannot forward remote payload as there is no known client streams for aggregated stream %s"
                  .formatted(stream.logicalId())));
      return;
    }

    final LinkedList<ClientStreamImpl<?>> targets = new LinkedList<>(streams);
    Collections.shuffle(targets);

    tryPush(stream.streamId(), targets, payload, future, new ArrayList<>());
  }

  private void tryPush(
      final UUID streamId,
      final Queue<ClientStreamImpl<?>> targets,
      final DirectBuffer buffer,
      final ActorFuture<Void> future,
      final List<Throwable> errors) {
    final var clientStream = targets.poll();
    if (clientStream == null) {
      failOnStreamExhausted(future, errors);
      return;
    }

    LOGGER.trace("Pushing data from stream [{}] to client [{}]", streamId, clientStream.streamId());
    push(clientStream, buffer)
        .onComplete(
            (ok, pushFailed) -> {
              if (pushFailed == null) {
                future.complete(null);
                return;
              }

              errors.add(pushFailed);
              logFailedPush(pushFailed, clientStream);
              metrics.pushTryFailed(ErrorResponse.mapErrorToCode(pushFailed));
              tryPush(streamId, targets, buffer, future, errors);
            });
  }

  private ActorFuture<Void> push(final ClientStreamImpl<?> stream, final DirectBuffer payload) {
    try {
      return stream.clientStreamConsumer().push(payload);
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  private void failOnStreamExhausted(final ActorFuture<Void> future, final List<Throwable> errors) {
    final StreamExhaustedException error =
        new StreamExhaustedException(
            "Failed to push data to all available clients. No more clients left to retry.");
    errors.forEach(error::addSuppressed);
    future.completeExceptionally(error);
  }

  private void logFailedPush(final Throwable pushFailed, final ClientStreamImpl<?> clientStream) {
    if (pushFailed instanceof ClientStreamBlockedException) {
      LOGGER.trace(
          "Failed to push data to client [{}], stream is blocked", clientStream.streamId());
    } else {
      PUSH_ERROR_LOGGER.warn(
          "Failed to push data to client [{}], retrying with next client.",
          clientStream.streamId(),
          pushFailed);
    }
  }
}
