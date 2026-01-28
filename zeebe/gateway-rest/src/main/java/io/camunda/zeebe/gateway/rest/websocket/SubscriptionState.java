/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessInstanceServices;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class SubscriptionState {

  private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionState.class);

  private final WebSocketSession session;
  private final Long processInstanceKey;
  private final ProcessInstanceServices processInstanceServices;
  private final ObjectMapper objectMapper;
  private volatile ProcessInstanceEntity lastKnownState;
  private volatile ScheduledFuture<?> pollingTask;
  private final CamundaAuthenticationProvider authenticationProvider;

  SubscriptionState(
      final WebSocketSession session,
      final Long processInstanceKey,
      final ProcessInstanceServices processInstanceServices,
      final ObjectMapper objectMapper,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.session = session;
    this.processInstanceKey = processInstanceKey;
    this.processInstanceServices = processInstanceServices;
    this.objectMapper = objectMapper;
    this.authenticationProvider = authenticationProvider;
  }

  void startPolling(final TaskScheduler taskScheduler, final long pollIntervalMs) {
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

  void handleClientMessage(final String payload) {
    LOGGER.debug(
        "Received client message for process instance {}: {}", processInstanceKey, payload);
  }

  Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  private void pollForUpdates() {
    try {
      if (!session.isOpen()) {
        stopPolling();
        return;
      }

      final ProcessInstanceEntity currentState =
          processInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .getByKey(processInstanceKey);

      if (currentState == null) {
        LOGGER.warn("Process instance {} not found", processInstanceKey);
        sendErrorMessage("Process instance not found");
        closeSession(CloseStatus.BAD_DATA.withReason("Process instance not found"));
        return;
      }

      if (lastKnownState == null || hasChanged(lastKnownState, currentState)) {
        lastKnownState = currentState;
        sendUpdate(currentState);
      }

      if (isTerminalState(currentState)) {
        LOGGER.debug(
            "Process instance {} reached terminal state, closing connection", processInstanceKey);
        sendCompletedMessage(currentState);
        closeSession(CloseStatus.NORMAL.withReason("Process instance completed"));
      }

    } catch (final Exception e) {
      LOGGER.error("Error polling process instance {}", processInstanceKey, e);
      sendErrorMessage("Internal error: " + e.getMessage());
    }
  }

  private boolean hasChanged(
      final ProcessInstanceEntity previous, final ProcessInstanceEntity current) {
    if (previous == null || current == null) {
      return true;
    }

    return !java.util.Objects.equals(previous.state(), current.state())
        || !java.util.Objects.equals(previous.hasIncident(), current.hasIncident())
        || !java.util.Objects.equals(previous.endDate(), current.endDate());
  }

  private boolean isTerminalState(final ProcessInstanceEntity entity) {
    if (entity == null || entity.state() == null) {
      return false;
    }
    return entity.state() == ProcessInstanceEntity.ProcessInstanceState.COMPLETED
        || entity.state() == ProcessInstanceEntity.ProcessInstanceState.CANCELED;
  }

  private void sendUpdate(final ProcessInstanceEntity entity) {
    try {
      final var result = SearchQueryResponseMapper.toProcessInstance(entity);
      final var message = new ProcessInstanceUpdateMessage("UPDATE", Instant.now(), result);
      final String json = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(json));
    } catch (final Exception e) {
      LOGGER.error("Failed to send update for process instance {}", processInstanceKey, e);
    }
  }

  private void sendCompletedMessage(final ProcessInstanceEntity entity) {
    try {
      final var result = SearchQueryResponseMapper.toProcessInstance(entity);
      final var message = new ProcessInstanceUpdateMessage("COMPLETED", Instant.now(), result);
      final String json = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(json));
    } catch (final Exception e) {
      LOGGER.error(
          "Failed to send completion message for process instance {}", processInstanceKey, e);
    }
  }

  private void sendErrorMessage(final String error) {
    try {
      final var message =
          new ProcessInstanceErrorMessage("ERROR", Instant.now(), error, processInstanceKey);
      final String json = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(json));
    } catch (final Exception e) {
      LOGGER.error("Failed to send error message for process instance {}", processInstanceKey, e);
    }
  }

  private void closeSession(final CloseStatus status) {
    stopPolling();
    try {
      session.close(status);
    } catch (final Exception e) {
      LOGGER.error("Error closing session for process instance {}", processInstanceKey, e);
    }
  }
}
