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
  private final Map<String, SequenceFlowsSubscription> sequenceFlowsSubscriptions =
      new ConcurrentHashMap<>();
  private final Map<String, ElementInstanceStatisticsSubscription> elementStatisticsSubscriptions =
      new ConcurrentHashMap<>();
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
    return switch (topic) {
      case "process-instance" -> subscribeToProcessInstance(parameters);
      case "sequence-flows" -> subscribeToSequenceFlows(parameters);
      case "element-instance-statistics" -> subscribeToElementStatistics(parameters);
      default -> throw new IllegalArgumentException("Unsupported topic: " + topic);
    };
  }

  private String subscribeToProcessInstance(final Map<String, Object> parameters) {
    final Long processInstanceKey = extractProcessInstanceKey(parameters);

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

  private String subscribeToSequenceFlows(final Map<String, Object> parameters) {
    final Long processInstanceKey = extractProcessInstanceKey(parameters);

    // Generate subscription ID
    final String subscriptionId = UUID.randomUUID().toString();

    // Create and start subscription
    final SequenceFlowsSubscription subscription =
        new SequenceFlowsSubscription(
            subscriptionId,
            processInstanceKey,
            session,
            processInstanceServices,
            objectMapper,
            authenticationProvider);

    sequenceFlowsSubscriptions.put(subscriptionId, subscription);
    subscription.startPolling(taskScheduler, 200L); // 200ms poll interval (0.2s)

    LOGGER.debug(
        "Created subscription {} for sequence flows of process instance {}",
        subscriptionId,
        processInstanceKey);

    return subscriptionId;
  }

  private String subscribeToElementStatistics(final Map<String, Object> parameters) {
    final Long processInstanceKey = extractProcessInstanceKey(parameters);

    // Generate subscription ID
    final String subscriptionId = UUID.randomUUID().toString();

    // Create and start subscription
    final ElementInstanceStatisticsSubscription subscription =
        new ElementInstanceStatisticsSubscription(
            subscriptionId,
            processInstanceKey,
            session,
            processInstanceServices,
            objectMapper,
            authenticationProvider);

    elementStatisticsSubscriptions.put(subscriptionId, subscription);
    subscription.startPolling(taskScheduler, 200L); // 200ms poll interval (0.2s)

    LOGGER.debug(
        "Created subscription {} for element statistics of process instance {}",
        subscriptionId,
        processInstanceKey);

    return subscriptionId;
  }

  private Long extractProcessInstanceKey(final Map<String, Object> parameters) {
    final Object keyObj = parameters.get("processInstanceKey");
    if (keyObj == null) {
      throw new IllegalArgumentException("Missing processInstanceKey parameter");
    }

    if (keyObj instanceof Number) {
      return ((Number) keyObj).longValue();
    } else if (keyObj instanceof String) {
      try {
        return Long.parseLong((String) keyObj);
      } catch (final NumberFormatException e) {
        throw new IllegalArgumentException("Invalid processInstanceKey format: " + keyObj);
      }
    } else {
      throw new IllegalArgumentException("Invalid processInstanceKey type: " + keyObj.getClass());
    }
  }

  public void unsubscribe(final String subscriptionId) {
    // Try process instance subscriptions
    final ProcessInstanceSubscription piSubscription = subscriptions.remove(subscriptionId);
    if (piSubscription != null) {
      piSubscription.stopPolling();
      LOGGER.debug("Removed process instance subscription {}", subscriptionId);
      return;
    }

    // Try sequence flows subscriptions
    final SequenceFlowsSubscription sfSubscription =
        sequenceFlowsSubscriptions.remove(subscriptionId);
    if (sfSubscription != null) {
      sfSubscription.stopPolling();
      LOGGER.debug("Removed sequence flows subscription {}", subscriptionId);
      return;
    }

    // Try element statistics subscriptions
    final ElementInstanceStatisticsSubscription esSubscription =
        elementStatisticsSubscriptions.remove(subscriptionId);
    if (esSubscription != null) {
      esSubscription.stopPolling();
      LOGGER.debug("Removed element statistics subscription {}", subscriptionId);
    }
  }

  public void stopAll() {
    final int totalCount =
        subscriptions.size()
            + sequenceFlowsSubscriptions.size()
            + elementStatisticsSubscriptions.size();
    LOGGER.debug("Stopping all {} subscriptions", totalCount);

    subscriptions.values().forEach(ProcessInstanceSubscription::stopPolling);
    subscriptions.clear();

    sequenceFlowsSubscriptions.values().forEach(SequenceFlowsSubscription::stopPolling);
    sequenceFlowsSubscriptions.clear();

    elementStatisticsSubscriptions
        .values()
        .forEach(ElementInstanceStatisticsSubscription::stopPolling);
    elementStatisticsSubscriptions.clear();
  }

  public boolean hasSubscription(final String subscriptionId) {
    return subscriptions.containsKey(subscriptionId)
        || sequenceFlowsSubscriptions.containsKey(subscriptionId)
        || elementStatisticsSubscriptions.containsKey(subscriptionId);
  }
}
