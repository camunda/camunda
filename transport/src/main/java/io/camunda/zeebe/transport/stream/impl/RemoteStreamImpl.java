/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.transport.stream.impl.ImmutableStreamRegistry.StreamConsumer;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class RemoteStreamImpl<M extends BufferReader, P extends BufferWriter>
    implements RemoteStream<M, P> {

  private final StreamConsumer<M> consumer;
  private final Set<StreamConsumer<M>> alternateConsumers;
  private final M metadata;
  private final RemoteStreamPusher<P> streamer;

  public RemoteStreamImpl(
      final StreamConsumer<M> consumer,
      final Set<StreamConsumer<M>> alternateConsumers,
      final M metadata,
      final RemoteStreamPusher<P> streamer) {
    this.consumer = consumer;
    this.alternateConsumers = alternateConsumers;
    this.metadata = metadata;
    this.streamer = streamer;
  }

  @Override
  public M metadata() {
    return metadata;
  }

  @Override
  public void push(final P payload, final ErrorHandler<P> errorHandler) {
    streamer.pushAsync(payload, (error, p) -> onError(error, p, errorHandler), consumer.id());
  }

  private void onError(
      final Throwable throwable, final P payload, final ErrorHandler<P> errorHandler) {
    new RetryHandler(errorHandler).retry(throwable, payload);
  }

  private final class RetryHandler {
    private final Iterator<StreamConsumer<M>> iter;
    private final ErrorHandler<P> errorHandler;

    public RetryHandler(final ErrorHandler<P> errorHandler) {
      this.errorHandler = errorHandler;
      final var randomOrder = new ArrayList<>(alternateConsumers);
      randomOrder.remove(consumer);
      Collections.shuffle(randomOrder);
      iter = randomOrder.iterator();
    }

    void retry(final Throwable throwable, final P payload) {
      if (iter.hasNext()) {
        streamer.pushAsync(payload, this::retry, iter.next().id());
      } else {
        errorHandler.handleError(throwable, payload);
      }
    }
  }
}
