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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerResponse;
import io.camunda.zeebe.transport.ServerTransport;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.IdGenerator;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class AtomixServerTransport extends Actor implements ServerTransport {

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private static final String LEGACY_API_TOPIC_FORMAT = "%s-api-%d";
  private static final String API_TOPIC_FORMAT = "%s-%s-api-%d";
  private static final String ERROR_MSG_NOT_SUBSCRIBED =
      "Node already unsubscribed from topic %s, this can only happen when atomix does not cleanly remove its handlers.";

  private final Long2ObjectHashMap<CompletableFuture<byte[]>> pendingRequests;
  private final Map<PartitionId, Set<String>> subscribedTopics;
  private final MessagingService messagingService;

  private final IdGenerator requestIdGenerator;
  private final boolean receiveOnLegacySubject;

  public AtomixServerTransport(
      final MessagingService messagingService,
      final IdGenerator requestIdGenerator,
      final boolean receiveOnLegacySubject) {
    this.messagingService = messagingService;
    this.requestIdGenerator = requestIdGenerator;
    this.receiveOnLegacySubject = receiveOnLegacySubject;
    pendingRequests = new Long2ObjectHashMap<>();
    subscribedTopics = new HashMap<>();
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
              subscribedTopics.values().stream()
                  .flatMap(Set::stream)
                  .forEach(this::unregisterHandler);
              subscribedTopics.clear();
              pendingRequests.clear();
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
          final var topics = topicNames(partitionId, requestType);
          topics.forEach(topic -> registerHandler(topic, partitionId, requestHandler));
          subscribedTopics.computeIfAbsent(partitionId, id -> new HashSet<>()).addAll(topics);
        });
  }

  @Override
  public ActorFuture<Void> unsubscribe(
      final PartitionId partitionId, final RequestType requestType) {
    return actor.call(
        () -> {
          final var topics = topicNames(partitionId, requestType);
          topics.forEach(this::unregisterHandler);
          final var registered = subscribedTopics.get(partitionId);
          if (registered != null) {
            topics.forEach(registered::remove);
            if (registered.isEmpty()) {
              subscribedTopics.remove(partitionId);
            }
          }
        });
  }

  private List<String> topicNames(final PartitionId partitionId, final RequestType requestType) {
    final var topic = topicName(partitionId.group(), partitionId.id(), requestType);
    if (receiveOnLegacySubject
        && Protocol.DEFAULT_PARTITION_GROUP_NAME.equals(partitionId.group())) {
      return List.of(topic, legacyTopicName(partitionId.id(), requestType));
    }
    return List.of(topic);
  }

  private void registerHandler(
      final String topic, final PartitionId partitionId, final RequestHandler handler) {
    LOG.trace("Subscribe for topic {}", topic);
    messagingService.registerHandler(
        topic, (sender, request) -> handleAtomixRequest(request, topic, partitionId, handler));
  }

  private void unregisterHandler(final String topic) {
    LOG.trace("Unsubscribe from topic {}", topic);
    messagingService.unregisterHandler(topic);
  }

  private CompletableFuture<byte[]> handleAtomixRequest(
      final byte[] requestBytes,
      final String topicName,
      final PartitionId partitionId,
      final RequestHandler requestHandler) {
    final var completableFuture = new CompletableFuture<byte[]>();
    actor.call(
        () -> {
          final long requestId = requestIdGenerator.nextId();
          final var topics = subscribedTopics.get(partitionId);
          if (topics == null || !topics.contains(topicName)) {
            final var errorMsg = String.format(ERROR_MSG_NOT_SUBSCRIBED, topicName);
            LOG.trace(errorMsg);
            completableFuture.completeExceptionally(new IllegalStateException(errorMsg));
            return;
          }

          try {
            requestHandler.onRequest(
                this,
                partitionId.id(),
                requestId,
                new UnsafeBuffer(requestBytes),
                0,
                requestBytes.length);
            if (LOG.isTraceEnabled()) {
              LOG.trace("Handled request {} for topic {}", requestId, topicName);
            }
            // we only add the request to the map after successful handling
            pendingRequests.put(requestId, completableFuture);
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
          final var completableFuture = pendingRequests.remove(requestId);
          if (completableFuture != null) {
            if (LOG.isTraceEnabled()) {
              LOG.trace("Send response to request {}", requestId);
            }

            completableFuture.complete(bytes);
          } else if (LOG.isTraceEnabled()) {
            LOG.trace(
                "Wasn't able to send response to request {} of partition {}",
                requestId,
                response.getPartitionId());
          }
        });
  }

  static String topicName(
      final String prefix, final int partitionId, final RequestType requestType) {
    return String.format(API_TOPIC_FORMAT, prefix, requestType.getId(), partitionId);
  }

  static String legacyTopicName(final int partitionId, final RequestType requestType) {
    return String.format(LEGACY_API_TOPIC_FORMAT, requestType.getId(), partitionId);
  }
}
