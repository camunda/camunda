/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.transport;

import io.atomix.cluster.messaging.MessagingService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry for all EventBridge topic handlers on a partition. All handlers use {@link
 * MessagingService}.
 */
public final class RequestHandlerRegistry implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(RequestHandlerRegistry.class);

  private final int partitionId;
  private final MessagingService messagingService;
  private final Map<String, RequestHandler> registeredTopics = new HashMap<>();

  public RequestHandlerRegistry(final int partitionId, final MessagingService messagingService) {
    this.partitionId = partitionId;
    this.messagingService = messagingService;
  }

  public void register(final String topic, final RequestHandler handler) {
    registeredTopics.put(topic, handler);
    messagingService.registerHandler(topic, (sender, requestBytes) -> handler.handle(requestBytes));
    LOG.info("Partition {} — registered handler: {}", partitionId, topic);
  }

  public void registerWithManagedPayload(final String topic, final RequestHandler handler) {
    registeredTopics.put(topic, handler);
    messagingService.registerHandlerWithManagedPayload(
        topic, (sender, requestBytes) -> handler.handleWithManagedPayload(requestBytes));
    LOG.info("Partition {} — registered handler: {}", partitionId, topic);
  }

  public void unregister(final String topic) {
    registeredTopics.remove(topic);
    try {
      messagingService.unregisterHandler(topic);
    } catch (final Exception e) {
      LOG.warn("Partition {} — error unregistering: {}", partitionId, topic, e);
    }
    LOG.info("Partition {} — unregistered handler: {}", partitionId, topic);
  }

  @Override
  public void close() {
    registeredTopics
        .keySet()
        .forEach(
            topic -> {
              try {
                messagingService.unregisterHandler(topic);
              } catch (final Exception e) {
                LOG.warn("Partition {} — error unregistering: {}", partitionId, topic, e);
              }
            });
    registeredTopics.clear();
  }
}
