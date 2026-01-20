/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.impl;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerResponse;
import io.camunda.zeebe.transport.ServerTransport;
import java.util.concurrent.CompletableFuture;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.IdGenerator;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class AtomixServerTransport extends Actor implements ServerTransport {

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private static final String API_TOPIC_FORMAT = "%s-api-%s-%d";

  private final Long2ObjectHashMap<CompletableFuture<byte[]>> partitionsRequestMap;
  private final MessagingService messagingService;

  private final IdGenerator requestIdGenerator;

  public AtomixServerTransport(
      final MessagingService messagingService, final IdGenerator requestIdGenerator) {
    this.messagingService = messagingService;
    this.requestIdGenerator = requestIdGenerator;
    partitionsRequestMap = new Long2ObjectHashMap<>();
  }

  @Override
  public String getName() {
    return "ServerTransport";
  }

  @Override
  public void close() {
    actor
        .call(
            () -> {
              partitionsRequestMap.clear();
              actor.close();
            })
        .join();
  }

  @Override
  public ActorFuture<Void> subscribe(
      final PartitionId partitionId,
      final RequestType requestType,
      final RequestHandler requestHandler) {
    return actor.call(
        () -> {
          final var topicName = topicName(partitionId, requestType);
          LOG.trace("Subscribe for topic {}", topicName);
          messagingService.registerHandler(
              topicName,
              (sender, request) ->
                  handleAtomixRequest(request, partitionId, requestType, requestHandler));
        });
  }

  @Override
  public ActorFuture<Void> unsubscribe(
      final PartitionId partitionId, final RequestType requestType) {
    return actor.call(() -> removeRequestHandlers(partitionId, requestType));
  }

  private void removeRequestHandlers(final PartitionId partitionId, final RequestType requestType) {
    final var topicName = topicName(partitionId, requestType);
    LOG.trace("Unsubscribe from topic {}", topicName);
    messagingService.unregisterHandler(topicName);
  }

  private CompletableFuture<byte[]> handleAtomixRequest(
      final byte[] requestBytes,
      final PartitionId partitionId,
      final RequestType requestType,
      final RequestHandler requestHandler) {
    final var completableFuture = new CompletableFuture<byte[]>();
    actor.call(
        () -> {
          final long requestId = requestIdGenerator.nextId();

          try {
            requestHandler.onRequest(
                this,
                partitionId,
                requestId,
                new UnsafeBuffer(requestBytes),
                0,
                requestBytes.length);
            if (LOG.isTraceEnabled()) {
              LOG.trace(
                  "Handled request {} for topic {}",
                  requestId,
                  topicName(partitionId, requestType));
            }
            // we only add the request to the map after successful handling
            partitionsRequestMap.put(requestId, completableFuture);
          } catch (final Exception exception) {
            LOG.error(
                "Unexpected exception on handling request for partition {}.",
                partitionId,
                exception);
            completableFuture.completeExceptionally(exception);
          }
        });

    return completableFuture;
  }

  @Override
  public void sendResponse(final ServerResponse response) {
    final var requestId = response.getRequestId();
    final var length = response.getLength();
    final var bytes = new byte[length];

    // here we can't reuse a buffer, because sendResponse can be called concurrently
    final var unsafeBuffer = new UnsafeBuffer(bytes);
    response.write(unsafeBuffer, 0);

    actor.run(
        () -> {
          final var completableFuture = partitionsRequestMap.remove(requestId);
          if (completableFuture != null) {
            if (LOG.isTraceEnabled()) {
              LOG.trace("Send response to request {}", requestId);
            }

            completableFuture.complete(bytes);
          } else if (LOG.isTraceEnabled()) {
            LOG.trace("Wasn't able to send response to request {}", requestId);
          }
        });
  }

  static String topicName(final PartitionId partitionId, final RequestType requestType) {
    return String.format(
        API_TOPIC_FORMAT, requestType.getId(), partitionId.group(), partitionId.id());
  }
}
