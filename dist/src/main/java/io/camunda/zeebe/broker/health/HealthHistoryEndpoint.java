/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.history.HealthHistoryEntry;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/**
 * Prototype: the broker's health history, newest last. Each entry is a status change of a health
 * tree component or an event such as a restart or a leader change. Answers 404 while the health
 * detectors are disabled.
 *
 * <p>Query parameters, all optional: {@code since} (ISO-8601 instant), {@code component} (a health
 * tree path; matches that component and everything below it), {@code type} (for example {@code
 * HEALTH_CHANGED} or {@code NODE_STARTED}) and {@code limit} (default 100).
 */
@Component
@WebEndpoint(id = "healthHistory")
@NullMarked
public final class HealthHistoryEndpoint {

  private static final int DEFAULT_LIMIT = 100;

  private final SpringBrokerBridge brokerBridge;

  public HealthHistoryEndpoint(final SpringBrokerBridge brokerBridge) {
    this.brokerBridge = brokerBridge;
  }

  @ReadOperation
  public WebEndpointResponse<?> history(
      final @Nullable String since,
      final @Nullable String component,
      final @Nullable String type,
      final @Nullable Integer limit) {
    final var history =
        brokerBridge
            .getBrokerHealthCheckService()
            .flatMap(BrokerHealthCheckService::getHealthHistory);
    if (history.isEmpty()) {
      return error(404, "The health history is disabled; enable experimental.healthDetectors");
    }

    final Instant sinceInstant;
    try {
      sinceInstant = since == null || since.isBlank() ? null : Instant.parse(since);
    } catch (final DateTimeParseException e) {
      return error(
          400, "Expected 'since' to be an ISO-8601 instant, but got '%s'".formatted(since));
    }
    if (limit != null && limit < 1) {
      return error(400, "Expected 'limit' to be at least 1, but got " + limit);
    }

    final List<HealthHistoryEntry> entries =
        history
            .get()
            .query(
                sinceInstant,
                component == null || component.isBlank() ? null : component,
                type == null || type.isBlank() ? null : type,
                limit == null ? DEFAULT_LIMIT : limit);
    return new WebEndpointResponse<>(
        new HealthHistoryResponse(history.get().root(), entries),
        200,
        MimeTypeUtils.APPLICATION_JSON);
  }

  private static WebEndpointResponse<?> error(final int status, final String message) {
    return new WebEndpointResponse<>(
        Map.of("error", message), status, MimeTypeUtils.APPLICATION_JSON);
  }

  public record HealthHistoryResponse(String broker, List<HealthHistoryEntry> entries) {}
}
