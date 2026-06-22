/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport.publish;

import io.camunda.eventbridge.broker.logstreams.EventStreamWriter;
import io.camunda.eventbridge.broker.publish.PublishResponse;
import io.camunda.eventbridge.broker.transport.RequestHandler;
import io.camunda.eventbridge.protocol.RejectionReason;
import java.util.concurrent.CompletableFuture;

public final class PublishRequestHandler implements RequestHandler {

  // Must match the topic the gateway BrokerClient sends to: "{partitionGroup}-{type}-api-{id}"
  // with the default partition group.
  private static final String TOPIC_FORMAT = "default-publish-api-%d";

  private final int partitionId;
  private final EventStreamWriter writer;
  private final PublishRequestCorrelator correlator;

  public PublishRequestHandler(
      final int partitionId,
      final EventStreamWriter writer,
      final PublishRequestCorrelator correlator) {
    this.partitionId = partitionId;
    this.writer = writer;
    this.correlator = correlator;
  }

  @Override
  public CompletableFuture<byte[]> handle(final byte[] requestBytes) {
    final PublishRequest request;
    try {
      request = PublishRequest.from(requestBytes);
    } catch (final IllegalArgumentException e) {
      return CompletableFuture.failedFuture(e);
    }

    final var registration = correlator.register();

    if (!writer.tryWrite(
        registration.requestId(),
        request.requestBytes(),
        request.batchOffset(),
        request.batchLength())) {
      correlator.cancel(registration.requestId());
      return CompletableFuture.completedFuture(
          PublishResponse.error(RejectionReason.BACKPRESSURE, "Partition " + partitionId));
    }

    return registration.future();
  }

  public static String topicName(final int partitionId) {
    return String.format(TOPIC_FORMAT, partitionId);
  }
}
