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
import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.RejectionReason;
import io.camunda.eventbridge.protocol.request.coordination.CleanupPolicy;
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
  private final CleanupPolicy cleanupPolicy;

  public PublishRequestHandler(
      final int partitionId,
      final EventStreamWriter writer,
      final PublishRequestCorrelator correlator,
      final CleanupPolicy cleanupPolicy) {
    this.partitionId = partitionId;
    this.writer = writer;
    this.correlator = correlator;
    this.cleanupPolicy = cleanupPolicy;
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

    // A COMPACT topic keeps only the latest record per key (event-bridge ADR 0001, decision 1);
    // publishing a batch without the KEYED attribute would leave the cleaner nothing to coalesce
    // on, so it is rejected here rather than silently copied forward forever. Keyed publishes to a
    // DELETE topic remain allowed (KEYED is just unused there).
    if (cleanupPolicy == CleanupPolicy.COMPACT && !isKeyed(request)) {
      payload.release();
      return CompletableFuture.completedFuture(
          PublishResponse.error(
              RejectionReason.UNKEYED_ON_COMPACTED_TOPIC,
              "Partition "
                  + partitionId
                  + " has cleanup policy COMPACT; publish batches must declare the KEYED"
                  + " attribute"));
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

  /** Reads the batch-level {@code KEYED} attribute directly out of the request's raw bytes. */
  private static boolean isKeyed(final PublishRequest request) {
    final int attributes =
        EventBridgeBatch.getAttributes(request.payload().view(), request.batchOffset());
    return EventBridgeBatch.isKeyed(attributes);
  }
}
