/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.websocket.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessInstanceServices;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketSession;

public class SubscriptionManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionManager.class);

  private final Map<String, ProcessInstanceSubscription> subscriptions = new ConcurrentHashMap<>();
  private final WebSocketSession session;
  private final ProcessInstanceServices processInstanceServices;
  private final TaskScheduler taskScheduler;
  private final ObjectMapper objectMapper;
  private final CamundaAuthenticationProvider authenticationProvider;

  public SubscriptionManager(
      final WebSocketSession session,
      final ProcessInstanceServices processInstanceServices,
      final TaskScheduler taskScheduler,
      final ObjectMapper objectMapper,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.session = session;
    this.processInstanceServices = processInstanceServices;
    this.taskScheduler = taskScheduler;
    this.objectMapper = objectMapper;
    this.authenticationProvider = authenticationProvider;
  }

  public String subscribe(final String topic, final Map<String, Object> parameters) {
    // Validate topic
    if (!"process-instance".equals(topic)) {
      throw new IllegalArgumentException("Unsupported topic: " + topic);
    }

    // Extract processInstanceKey
    final Object keyObj = parameters.get("processInstanceKey");
    if (keyObj == null) {
      throw new IllegalArgumentException("Missing processInstanceKey parameter");
    }

    final Long processInstanceKey;
    if (keyObj instanceof Number) {
      processInstanceKey = ((Number) keyObj).longValue();
    } else if (keyObj instanceof String) {
      try {
        processInstanceKey = Long.parseLong((String) keyObj);
      } catch (final NumberFormatException e) {
        throw new IllegalArgumentException("Invalid processInstanceKey format: " + keyObj);
      }
    } else {
      throw new IllegalArgumentException("Invalid processInstanceKey type: " + keyObj.getClass());
    }

    // Generate subscription ID
    final String subscriptionId = UUID.randomUUID().toString();

    // Create and start subscription
    final ProcessInstanceSubscription subscription =
        new ProcessInstanceSubscription(
            subscriptionId,
            processInstanceKey,
            session,
            processInstanceServices,
            objectMapper,
            authenticationProvider);

    subscriptions.put(subscriptionId, subscription);
    subscription.startPolling(taskScheduler, 1000L); // 1 second poll interval

    LOGGER.debug(
        "Created subscription {} for process instance {}", subscriptionId, processInstanceKey);

    return subscriptionId;
  }

  public void unsubscribe(final String subscriptionId) {
    final ProcessInstanceSubscription subscription = subscriptions.remove(subscriptionId);
    if (subscription != null) {
      subscription.stopPolling();
      LOGGER.debug("Removed subscription {}", subscriptionId);
    }
  }

  public void stopAll() {
    LOGGER.debug("Stopping all {} subscriptions", subscriptions.size());
    subscriptions.values().forEach(ProcessInstanceSubscription::stopPolling);
    subscriptions.clear();
  }

  public boolean hasSubscription(final String subscriptionId) {
    return subscriptions.containsKey(subscriptionId);
  }
}
