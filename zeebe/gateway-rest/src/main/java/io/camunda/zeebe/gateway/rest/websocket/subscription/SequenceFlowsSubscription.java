/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.websocket.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.zeebe.gateway.rest.websocket.message.ErrorMessage;
import io.camunda.zeebe.gateway.rest.websocket.message.UpdateMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class SequenceFlowsSubscription {

  private static final Logger LOGGER = LoggerFactory.getLogger(SequenceFlowsSubscription.class);

  private final String subscriptionId;
  private final Long processInstanceKey;
  private final WebSocketSession session;
  private final ProcessInstanceServices processInstanceServices;
  private final ObjectMapper objectMapper;
  private final CamundaAuthenticationProvider authenticationProvider;
  private volatile ScheduledFuture<?> pollingTask;

  SequenceFlowsSubscription(
      final String subscriptionId,
      final Long processInstanceKey,
      final WebSocketSession session,
      final ProcessInstanceServices processInstanceServices,
      final ObjectMapper objectMapper,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.subscriptionId = subscriptionId;
    this.processInstanceKey = processInstanceKey;
    this.session = session;
    this.processInstanceServices = processInstanceServices;
    this.objectMapper = objectMapper;
    this.authenticationProvider = authenticationProvider;
  }

  String getSubscriptionId() {
    return subscriptionId;
  }

  void startPolling(final TaskScheduler taskScheduler, final long pollIntervalMs) {
    // Send initial state immediately
    pollForUpdates();

    // Then schedule periodic polling
    pollingTask =
        taskScheduler.scheduleAtFixedRate(
            this::pollForUpdates,
            Instant.now().plusMillis(pollIntervalMs),
            Duration.ofMillis(pollIntervalMs));
  }

  void stopPolling() {
    if (pollingTask != null) {
      pollingTask.cancel(false);
      pollingTask = null;
    }
  }

  private void pollForUpdates() {
    try {
      if (!session.isOpen()) {
        stopPolling();
        return;
      }

      // Fetch sequence flows
      final List<SequenceFlowEntity> currentFlows =
          processInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .sequenceFlows(processInstanceKey);

      if (currentFlows == null) {
        LOGGER.warn("Failed to fetch sequence flows for process instance {}", processInstanceKey);
        sendErrorMessage("Failed to fetch sequence flows", "INTERNAL_ERROR");
        // Continue polling even on failure
        return;
      }

      // Always send update on every poll
      sendUpdate(currentFlows);

    } catch (final Exception e) {
      LOGGER.error("Error polling sequence flows for process instance {}", processInstanceKey, e);
      sendErrorMessage("Internal error: " + e.getMessage(), "INTERNAL_ERROR");
      // Continue polling on error
    }
  }

  private void sendUpdate(final List<SequenceFlowEntity> flows) {
    try {
      final var result = SearchQueryResponseMapper.toSequenceFlowsResult(flows);
      final var message = new UpdateMessage("UPDATE", Instant.now(), subscriptionId, result);
      final String json = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(json));
    } catch (final Exception e) {
      LOGGER.error("Failed to send update for sequence flows {}", processInstanceKey, e);
    }
  }

  private void sendErrorMessage(final String error, final String code) {
    try {
      final var message = new ErrorMessage("ERROR", Instant.now(), subscriptionId, error, code);
      final String json = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(json));
    } catch (final Exception e) {
      LOGGER.error("Failed to send error message for sequence flows {}", processInstanceKey, e);
    }
  }
}
