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
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
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

class ElementInstanceStatisticsSubscription {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElementInstanceStatisticsSubscription.class);

  private final String subscriptionId;
  private final Long processInstanceKey;
  private final WebSocketSession session;
  private final ProcessInstanceServices processInstanceServices;
  private final ObjectMapper objectMapper;
  private final CamundaAuthenticationProvider authenticationProvider;
  private volatile List<ProcessFlowNodeStatisticsEntity> lastKnownStatistics;
  private volatile int lastKnownHash;
  private volatile ProcessInstanceEntity lastKnownProcessState;
  private volatile boolean hasReachedTerminalState = false;
  private volatile ScheduledFuture<?> pollingTask;

  ElementInstanceStatisticsSubscription(
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

      // Fetch element statistics
      final List<ProcessFlowNodeStatisticsEntity> currentStatistics =
          processInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .elementStatistics(processInstanceKey);

      if (currentStatistics == null) {
        LOGGER.warn(
            "Failed to fetch element statistics for process instance {}", processInstanceKey);
        sendErrorMessage("Failed to fetch element statistics", "INTERNAL_ERROR");
        return;
      }

      // Fetch process instance state to detect terminal state
      final ProcessInstanceEntity currentProcessState;
      try {
        currentProcessState =
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .getByKey(processInstanceKey);

        if (currentProcessState == null) {
          LOGGER.warn("Process instance {} not found", processInstanceKey);
          sendErrorMessage("Process instance not found", "RESOURCE_NOT_FOUND");
          stopPolling();
          return;
        }
      } catch (final Exception e) {
        LOGGER.error("Error fetching process instance state {}", processInstanceKey, e);
        sendErrorMessage("Failed to fetch process instance: " + e.getMessage(), "INTERNAL_ERROR");
        return;
      }

      // Check if we've reached terminal state
      final boolean isNowTerminal = isTerminalState(currentProcessState);

      // If already in terminal state and we've detected it before, stop sending updates
      if (hasReachedTerminalState && isNowTerminal) {
        // Already in terminal state, no more changes expected
        return; // Don't send updates
      }

      // Send update if changed OR if first poll (initial state)
      if (lastKnownStatistics == null || hasChanged(lastKnownStatistics, currentStatistics)) {
        sendUpdate(currentStatistics);
        lastKnownStatistics = currentStatistics;
        lastKnownHash = computeHash(currentStatistics);
      }

      // Track terminal state transition
      if (isNowTerminal && !hasReachedTerminalState) {
        hasReachedTerminalState = true;
        LOGGER.debug(
            "Process instance {} reached terminal state, will stop sending updates",
            processInstanceKey);
        // Don't send COMPLETED, just stop sending updates
      }

      lastKnownProcessState = currentProcessState;

    } catch (final Exception e) {
      LOGGER.error(
          "Error polling element statistics for process instance {}", processInstanceKey, e);
      sendErrorMessage("Internal error: " + e.getMessage(), "INTERNAL_ERROR");
    }
  }

  private boolean hasChanged(
      final List<ProcessFlowNodeStatisticsEntity> previous,
      final List<ProcessFlowNodeStatisticsEntity> current) {
    if (previous == null || current == null) {
      return true;
    }

    // Hash-based comparison for O(1) performance
    final int currentHash = computeHash(current);
    return lastKnownHash != currentHash;
  }

  private int computeHash(final List<ProcessFlowNodeStatisticsEntity> statistics) {
    if (statistics == null) {
      return 0;
    }

    // Compute hash from all statistics
    // Using hashCode() from record class which includes all fields
    int hash = 1;
    for (final ProcessFlowNodeStatisticsEntity stat : statistics) {
      hash = 31 * hash + (stat != null ? stat.hashCode() : 0);
    }
    return hash;
  }

  private boolean isTerminalState(final ProcessInstanceEntity entity) {
    if (entity == null || entity.state() == null) {
      return false;
    }
    return entity.state() == ProcessInstanceEntity.ProcessInstanceState.COMPLETED
        || entity.state() == ProcessInstanceEntity.ProcessInstanceState.CANCELED;
  }

  private void sendUpdate(final List<ProcessFlowNodeStatisticsEntity> statistics) {
    try {
      final var result =
          SearchQueryResponseMapper.toProcessInstanceElementStatisticsResult(statistics);
      final var message = new UpdateMessage("UPDATE", Instant.now(), subscriptionId, result);
      final String json = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(json));

      // Log every update sent for statistics
      LOGGER.info(
          "Sent UPDATE for element statistics subscription {} (PI: {}, elements: {})",
          subscriptionId,
          processInstanceKey,
          statistics.size());

    } catch (final Exception e) {
      LOGGER.error("Failed to send update for element statistics {}", processInstanceKey, e);
    }
  }

  private void sendErrorMessage(final String error, final String code) {
    try {
      final var message = new ErrorMessage("ERROR", Instant.now(), subscriptionId, error, code);
      final String json = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(json));
    } catch (final Exception e) {
      LOGGER.error("Failed to send error message for element statistics {}", processInstanceKey, e);
    }
  }
}
