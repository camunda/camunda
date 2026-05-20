/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.agentic;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Immutable query parameters for all Agentic Control Plane endpoints. Constructed by
 * AgenticControlPlaneRestService from request params + JWT-resolved tenantIds. Never expose
 * tenantIds as a client-facing request parameter.
 */
@NullMarked
public record AgentQueryParams(
    List<String> tenantIds,
    @Nullable String processDefinitionKey,
    @Nullable String agentElementId, // Phase 2 only; always null in Phase 1
    Instant startDateFrom,
    Instant startDateTo) {

  /**
   * Returns a copy with the date range shifted back by the same duration as the selected range, for
   * period-comparison computation.
   */
  public AgentQueryParams forPreviousPeriodWindow() {
    final Duration range = Duration.between(startDateFrom, startDateTo);
    return new AgentQueryParams(
        tenantIds,
        processDefinitionKey,
        agentElementId,
        startDateFrom.minus(range),
        startDateTo.minus(range));
  }
}
