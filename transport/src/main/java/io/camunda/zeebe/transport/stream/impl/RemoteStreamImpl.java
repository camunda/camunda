/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamConsumer;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteStreamImpl<M extends BufferReader, P extends BufferWriter>
    implements RemoteStream<M, P> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteStreamImpl.class);
  private final AggregatedRemoteStream<M> stream;
  private final RemoteStreamPusher<P> streamer;
  private final Executor executor;

  public RemoteStreamImpl(
      final AggregatedRemoteStream<M> stream,
      final RemoteStreamPusher<P> streamer,
      final Executor executor) {
    this.stream = stream;
    this.streamer = streamer;
    this.executor = executor;
  }

  @Override
  public M metadata() {
    return stream.logicalId().metadata();
  }

  @Override
  public void push(final P payload, final ErrorHandler<P> errorHandler) {
    executor.execute(
        () -> {
          final List<StreamConsumer<M>> streamConsumers = stream.streamConsumers();
          final var randomClient =
              streamConsumers.get(ThreadLocalRandom.current().nextInt(streamConsumers.size()));
          streamer.pushAsync(
              payload,
              (error, p) -> {
                final var consumers = new ArrayList<>(streamConsumers);
                consumers.remove(randomClient);
                Collections.shuffle(consumers);
                new RetryHandler(errorHandler, consumers).retry(error, payload);
              },
              randomClient.id());
        });
  }

  private final class RetryHandler {
    private final Iterator<StreamConsumer<M>> iter;
    private final ErrorHandler<P> errorHandler;

    public RetryHandler(
        final ErrorHandler<P> errorHandler, final List<StreamConsumer<M>> consumers) {
      this.errorHandler = errorHandler;
      iter = consumers.iterator();
    }

    void retry(final Throwable throwable, final P payload) {
      if (iter.hasNext()) {
        LOGGER.debug("Failed to push payload {}, retrying with next stream", payload);
        streamer.pushAsync(payload, this::retry, iter.next().id());
      } else {
        LOGGER.debug("Failed to push payload {}, no more streams to retry", payload);
        errorHandler.handleError(throwable, payload);
      }
    }
  }
}
