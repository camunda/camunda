/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.transport.stream.api.StreamExhaustedException;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamConsumer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RemoteStreamImpl<M, P extends BufferWriter> implements RemoteStream<M, P> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteStreamImpl.class);
  private final AggregatedRemoteStream<M> stream;
  private final RemoteStreamPusher<P> streamer;
  private final RemoteStreamErrorHandler<P> errorHandler;

  public RemoteStreamImpl(
      final AggregatedRemoteStream<M> stream,
      final RemoteStreamPusher<P> streamer,
      final RemoteStreamErrorHandler<P> errorHandler) {
    this.stream = stream;
    this.streamer = streamer;
    this.errorHandler = errorHandler;
  }

  @Override
  public M metadata() {
    return stream.logicalId().metadata();
  }

  @Override
  public void push(final P payload) {
    final var initialConsumer = pickInitialConsumer();
    if (initialConsumer == null) {
      errorHandler.handleError(
          new StreamExhaustedException(
              "Failed to push to stream %s, all consumers were removed since it was picked"
                  .formatted(stream.logicalId())),
          payload);
      return;
    }

    final var retryHandler = new RetryHandler(errorHandler, initialConsumer);
    streamer.pushAsync(payload, retryHandler, initialConsumer.id());
  }

  private StreamConsumer<M> pickInitialConsumer() {
    final var consumers = stream.streamConsumers();
    var size = consumers.size();

    // since we can get concurrent modifications of the stream consumers list, we have to handle the
    // case where the size changes while we're picking a consumer, so we loop as long as we fail to
    // pick a consumer or the list is empty
    while (size > 0) {
      final var index = ThreadLocalRandom.current().nextInt(size);
      try {
        return consumers.get(index);
      } catch (final IndexOutOfBoundsException e) {
        LOGGER.trace(
            "Stream consumer list concurrently modified while picking consumer; retrying", e);
        size = consumers.size();
      }
    }

    return null;
  }

  private final class RetryHandler implements RemoteStreamErrorHandler<P> {
    private final RemoteStreamErrorHandler<P> errorHandler;
    private final StreamConsumer<M> initialConsumer;

    private RetryHandler(
        final RemoteStreamErrorHandler<P> errorHandler, final StreamConsumer<M> initialConsumer) {
      this.errorHandler = errorHandler;
      this.initialConsumer = initialConsumer;
    }

    /** Called the first time a push is retried */
    @Override
    public void handleError(final Throwable error, final P data) {
      final var consumers = new ArrayList<>(stream.streamConsumers());
      if (consumers.isEmpty()) {
        onConsumersExhausted(error, data);
        return;
      }

      consumers.remove(initialConsumer);
      Collections.shuffle(consumers);
      final var iterator = consumers.iterator();
      retry(error, data, iterator);
    }

    /** Called during future retries */
    private void retry(
        final Throwable throwable, final P payload, final Iterator<StreamConsumer<M>> iterator) {
      if (!iterator.hasNext()) {
        onConsumersExhausted(throwable, payload);
        return;
      }

      final var client = iterator.next();
      LOGGER.trace(
          "Failed to push payload (size = {}), retrying with next stream", payload.getLength());
      streamer.pushAsync(payload, (error, data) -> retry(error, data, iterator), client.id());
    }

    private void onConsumersExhausted(final Throwable throwable, final P payload) {
      LOGGER.trace(
          "Failed to push payload (size = {}), no more streams to retry", payload.getLength());
      errorHandler.handleError(throwable, payload);
    }
  }
}
