/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.transport.publish;

import io.atomix.cluster.messaging.ByteArrayPayload;
import io.atomix.cluster.messaging.InboundPayload;
import io.camunda.eventbridge.messaging.publish.PublishResponse;
import io.camunda.eventbridge.messaging.stream.EventStreamWriter;
import io.camunda.eventbridge.protocol.RejectionReason;
import io.camunda.eventbridge.transport.RequestHandler;
import java.util.concurrent.CompletableFuture;

public final class PublishRequestHandler implements RequestHandler {

  // Must match the topic the gateway BrokerClient sends to: "{partitionGroup}-{type}-api-{id}".
  // The group is "default" for data partitions and the topic group name for topic partitions, so
  // partitions with the same id in different groups don't collide on the messaging subject.
  private static final String TOPIC_FORMAT = "%s-publish-api-%d";

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
    return handleInbound(new ByteArrayPayload(requestBytes));
  }

  @Override
  public CompletableFuture<byte[]> handleInbound(final InboundPayload payload) {
    final PublishRequest request;
    try {
      request = PublishRequest.from(payload);
    } catch (final IllegalArgumentException e) {
      payload.release();
      return CompletableFuture.failedFuture(e);
    }

    final var registration = correlator.register();

    if (!writer.tryWrite(
        registration.requestId(),
        request.payload(),
        request.batchOffset(),
        request.batchLength())) {
      // the pipeline did not take ownership, so the payload is released here
      correlator.cancel(registration.requestId());
      payload.release();
      return CompletableFuture.completedFuture(
          PublishResponse.error(RejectionReason.BACKPRESSURE, "Partition " + partitionId));
    }

    return registration.future();
  }

  public static String topicName(final String routingGroup, final int partitionId) {
    return String.format(TOPIC_FORMAT, routingGroup, partitionId);
  }
}
