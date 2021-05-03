/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.transport.impl;

import io.atomix.cluster.messaging.MessagingService;
import io.zeebe.transport.RequestHandler;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class AtomixServerTransport extends Actor implements ServerTransport {

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private static final String API_TOPIC_FORMAT = "command-api-%d";
  private static final String ERROR_MSG_MISSING_PARTITON_MAP =
      "Node already unsubscribed from partition %d, this can only happen when atomix does not cleanly remove its handlers.";

  private final Int2ObjectHashMap<Long2ObjectHashMap<CompletableFuture<byte[]>>>
      partitionsRequestMap;
  private final AtomicLong requestCount;
  private final DirectBuffer reusableRequestBuffer;
  private final MessagingService messagingService;
  private final String actorName;

  public AtomixServerTransport(final int nodeId, final MessagingService messagingService) {
    this.messagingService = messagingService;
    partitionsRequestMap = new Int2ObjectHashMap<>();
    requestCount = new AtomicLong(0);
    reusableRequestBuffer = new UnsafeBuffer(0, 0);
    actorName = buildActorName(nodeId, "ServerTransport");
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  public void close() {
    actor
        .call(
            () -> {
              for (int partitionId : partitionsRequestMap.keySet()) {
                removePartition(partitionId);
              }
              actor.close();
            })
        .join();
  }

  @Override
  public ActorFuture<Void> subscribe(final int partitionId, final RequestHandler requestHandler) {
    return actor.call(
        () -> {
          final var topicName = topicName(partitionId);
          if (LOG.isTraceEnabled()) {
            LOG.trace("Subscribe for topic {}", topicName);
          }
          partitionsRequestMap.put(partitionId, new Long2ObjectHashMap<>());
          messagingService.registerHandler(
              topicName,
              (sender, request) -> handleAtomixRequest(request, partitionId, requestHandler));
        });
  }

  @Override
  public ActorFuture<Void> unsubscribe(final int partitionId) {
    return actor.call(() -> removePartition(partitionId));
  }

  private void removePartition(final int partitionId) {
    final var topicName = topicName(partitionId);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Unsubscribe from topic {}", topicName);
    }

    messagingService.unregisterHandler(topicName);

    final var requestMap = partitionsRequestMap.remove(partitionId);
    if (requestMap != null) {
      requestMap.clear();
    }
  }

  private CompletableFuture<byte[]> handleAtomixRequest(
      final byte[] requestBytes, final int partitionId, final RequestHandler requestHandler) {
    final var completableFuture = new CompletableFuture<byte[]>();
    actor.call(
        () -> {
          final var requestId = requestCount.getAndIncrement();
          final var requestMap = partitionsRequestMap.get(partitionId);
          if (requestMap == null) {
            final var errorMsg = String.format(ERROR_MSG_MISSING_PARTITON_MAP, partitionId);
            LOG.trace(errorMsg);
            completableFuture.completeExceptionally(new IllegalStateException(errorMsg));
            return;
          }

          try {
            reusableRequestBuffer.wrap(requestBytes);
            requestHandler.onRequest(
                this, partitionId, requestId, reusableRequestBuffer, 0, requestBytes.length);
            if (LOG.isTraceEnabled()) {
              LOG.trace("Handled request {} for topic {}", requestId, topicName(partitionId));
            }
            // we only add the request to the map after successful handling
            requestMap.put(requestId, completableFuture);
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
    final var partitionId = response.getPartitionId();
    final var length = response.getLength();
    final var bytes = new byte[length];

    // here we can't reuse an buffer, because sendResponse can be called concurrently
    final var unsafeBuffer = new UnsafeBuffer(bytes);
    response.write(unsafeBuffer, 0);

    actor.run(
        () -> {
          final var requestMap = partitionsRequestMap.get(partitionId);
          if (requestMap == null) {
            LOG.warn(
                "Node is no longer leader for partition {}, tried to respond on request with id {}",
                partitionId,
                requestId);
            return;
          }

          final var completableFuture = requestMap.remove(requestId);
          if (completableFuture != null) {
            if (LOG.isTraceEnabled()) {
              LOG.trace(
                  "Send response to request {} for topic {}", requestId, topicName(partitionId));
            }

            completableFuture.complete(bytes);
          } else if (LOG.isTraceEnabled()) {
            LOG.trace(
                "Wasn't able to send response to request {} for topic {}",
                requestId,
                topicName(partitionId));
          }
        });
  }

  static String topicName(final int partitionId) {
    return String.format(API_TOPIC_FORMAT, partitionId);
  }
}
