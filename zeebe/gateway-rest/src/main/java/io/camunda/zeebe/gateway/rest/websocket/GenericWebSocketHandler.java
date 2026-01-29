/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.zeebe.gateway.rest.websocket.message.ErrorMessage;
import io.camunda.zeebe.gateway.rest.websocket.message.SubscribeMessage;
import io.camunda.zeebe.gateway.rest.websocket.message.SubscribedMessage;
import io.camunda.zeebe.gateway.rest.websocket.message.UnsubscribeMessage;
import io.camunda.zeebe.gateway.rest.websocket.message.UnsubscribedMessage;
import io.camunda.zeebe.gateway.rest.websocket.subscription.SubscriptionManager;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GenericWebSocketHandler extends TextWebSocketHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GenericWebSocketHandler.class);

  private final Map<String, SubscriptionManager> sessionManagers = new ConcurrentHashMap<>();
  private final ProcessInstanceServices processInstanceServices;
  private final TaskScheduler taskScheduler;
  private final ObjectMapper objectMapper;
  private final CamundaAuthenticationProvider authenticationProvider;

  @Autowired
  public GenericWebSocketHandler(
      final ProcessInstanceServices processInstanceServices,
      final TaskScheduler taskScheduler,
      final ObjectMapper objectMapper,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processInstanceServices = processInstanceServices;
    this.taskScheduler = taskScheduler;
    this.objectMapper = objectMapper;
    this.authenticationProvider = authenticationProvider;
  }

  @Override
  public void afterConnectionEstablished(final WebSocketSession session) throws Exception {
    LOGGER.debug("WebSocket connection established: {}", session.getId());

    final SubscriptionManager manager =
        new SubscriptionManager(
            session, processInstanceServices, taskScheduler, objectMapper, authenticationProvider);

    sessionManagers.put(session.getId(), manager);
  }

  @Override
  protected void handleTextMessage(final WebSocketSession session, final TextMessage message)
      throws Exception {
    try {
      final JsonNode node = objectMapper.readTree(message.getPayload());
      final String action = node.has("action") ? node.get("action").asText() : null;

      if (action == null) {
        sendError(session, null, "Missing action field", "INVALID_MESSAGE");
        return;
      }

      final SubscriptionManager manager = sessionManagers.get(session.getId());
      if (manager == null) {
        LOGGER.error("No subscription manager for session: {}", session.getId());
        return;
      }

      switch (action) {
        case "SUBSCRIBE" -> handleSubscribe(session, manager, node);
        case "UNSUBSCRIBE" -> handleUnsubscribe(session, manager, node);
        default -> sendError(session, null, "Unknown action: " + action, "INVALID_MESSAGE");
      }

    } catch (final Exception e) {
      LOGGER.error("Error handling message", e);
      sendError(session, null, "Invalid message format: " + e.getMessage(), "INVALID_MESSAGE");
    }
  }

  private void handleSubscribe(
      final WebSocketSession session, final SubscriptionManager manager, final JsonNode node) {
    try {
      final SubscribeMessage msg = objectMapper.treeToValue(node, SubscribeMessage.class);

      final String subscriptionId = manager.subscribe(msg.topic(), msg.parameters());

      // Send SUBSCRIBED acknowledgment
      final SubscribedMessage response =
          new SubscribedMessage(
              "SUBSCRIBED", Instant.now(), subscriptionId, msg.topic(), msg.parameters());

      final String json = objectMapper.writeValueAsString(response);
      session.sendMessage(new TextMessage(json));

      LOGGER.debug("Created subscription {} for topic {}", subscriptionId, msg.topic());

    } catch (final IllegalArgumentException e) {
      sendError(session, null, e.getMessage(), "INVALID_MESSAGE");
    } catch (final Exception e) {
      LOGGER.error("Failed to create subscription", e);
      sendError(
          session, null, "Failed to create subscription: " + e.getMessage(), "INTERNAL_ERROR");
    }
  }

  private void handleUnsubscribe(
      final WebSocketSession session, final SubscriptionManager manager, final JsonNode node) {
    try {
      final UnsubscribeMessage msg = objectMapper.treeToValue(node, UnsubscribeMessage.class);

      if (!manager.hasSubscription(msg.subscriptionId())) {
        sendError(
            session, msg.subscriptionId(), "Subscription not found", "SUBSCRIPTION_NOT_FOUND");
        return;
      }

      manager.unsubscribe(msg.subscriptionId());

      // Send UNSUBSCRIBED acknowledgment
      final UnsubscribedMessage response =
          new UnsubscribedMessage("UNSUBSCRIBED", Instant.now(), msg.subscriptionId());

      final String json = objectMapper.writeValueAsString(response);
      session.sendMessage(new TextMessage(json));

      LOGGER.debug("Unsubscribed: {}", msg.subscriptionId());

    } catch (final Exception e) {
      LOGGER.error("Failed to unsubscribe", e);
      sendError(session, null, "Failed to unsubscribe: " + e.getMessage(), "INTERNAL_ERROR");
    }
  }

  @Override
  public void handleTransportError(final WebSocketSession session, final Throwable exception)
      throws Exception {
    LOGGER.error("WebSocket transport error for session: {}", session.getId(), exception);
    cleanupSession(session);
    session.close(CloseStatus.SERVER_ERROR.withReason("Transport error"));
  }

  @Override
  public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status)
      throws Exception {
    LOGGER.debug("WebSocket connection closed: {} (status: {})", session.getId(), status);
    cleanupSession(session);
  }

  private void cleanupSession(final WebSocketSession session) {
    final SubscriptionManager manager = sessionManagers.remove(session.getId());
    if (manager != null) {
      manager.stopAll();
    }
  }

  private void sendError(
      final WebSocketSession session,
      final String subscriptionId,
      final String error,
      final String code) {
    try {
      final ErrorMessage msg =
          new ErrorMessage("ERROR", Instant.now(), subscriptionId, error, code);
      final String json = objectMapper.writeValueAsString(msg);
      session.sendMessage(new TextMessage(json));
    } catch (final Exception e) {
      LOGGER.error("Failed to send error message", e);
    }
  }
}
