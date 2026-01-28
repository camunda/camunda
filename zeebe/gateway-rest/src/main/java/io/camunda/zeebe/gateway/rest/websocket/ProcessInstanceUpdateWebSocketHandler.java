/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessInstanceServices;
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
public class ProcessInstanceUpdateWebSocketHandler extends TextWebSocketHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceUpdateWebSocketHandler.class);
  private static final long POLL_INTERVAL_MS = 1000;

  private final ProcessInstanceServices processInstanceServices;
  private final TaskScheduler taskScheduler;
  private final ObjectMapper objectMapper;
  private final Map<String, SubscriptionState> subscriptions = new ConcurrentHashMap<>();
  private final CamundaAuthenticationProvider authenticationProvider;

  @Autowired
  public ProcessInstanceUpdateWebSocketHandler(
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
    final Long processInstanceKey = extractProcessInstanceKey(session);
    if (processInstanceKey == null) {
      session.close(CloseStatus.BAD_DATA.withReason("Missing processInstanceKey in URL"));
      return;
    }

    LOGGER.debug("WebSocket connection established for process instance: {}", processInstanceKey);

    final SubscriptionState subscription =
        new SubscriptionState(
            session,
            processInstanceKey,
            processInstanceServices,
            objectMapper,
            authenticationProvider);
    subscriptions.put(session.getId(), subscription);

    try {
      subscription.startPolling(taskScheduler, POLL_INTERVAL_MS);
    } catch (final Exception e) {
      LOGGER.error("Failed to start polling for session: {}", session.getId(), e);
      session.close(CloseStatus.SERVER_ERROR.withReason("Failed to start subscription"));
    }
  }

  @Override
  protected void handleTextMessage(final WebSocketSession session, final TextMessage message)
      throws Exception {
    final SubscriptionState subscription = subscriptions.get(session.getId());
    if (subscription != null) {
      subscription.handleClientMessage(message.getPayload());
    }
  }

  @Override
  public void handleTransportError(final WebSocketSession session, final Throwable exception)
      throws Exception {
    LOGGER.error("WebSocket transport error for session: {}", session.getId(), exception);
    final SubscriptionState subscription = subscriptions.remove(session.getId());
    if (subscription != null) {
      subscription.stopPolling();
    }
    session.close(CloseStatus.SERVER_ERROR.withReason("Transport error"));
  }

  @Override
  public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status)
      throws Exception {
    final SubscriptionState subscription = subscriptions.remove(session.getId());
    if (subscription != null) {
      subscription.stopPolling();
      LOGGER.debug(
          "WebSocket connection closed for process instance: {} (status: {})",
          subscription.getProcessInstanceKey(),
          status);
    }
  }

  private Long extractProcessInstanceKey(final WebSocketSession session) {
    final String uri = session.getUri().toString();
    final String path = session.getUri().getPath();
    final String[] segments = path.split("/");

    if (segments.length < 2) {
      return null;
    }

    try {
      return Long.parseLong(segments[segments.length - 1]);
    } catch (final NumberFormatException e) {
      return null;
    }
  }
}
