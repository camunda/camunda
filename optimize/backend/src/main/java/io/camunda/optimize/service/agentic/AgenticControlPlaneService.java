/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.agentic;

import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import io.camunda.optimize.dto.optimize.rest.agentic.ChartsResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessBreakdownResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.SummaryResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.TrendsResponse;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Business logic layer for the Agentic Control Plane. Handles period-delta computation,
 * authorization, and mapping from raw repository results to response DTOs.
 */
@NullMarked
public interface AgenticControlPlaneService {

  /** Runs current + previous period queries in parallel and computes delta fields. */
  SummaryResponse getSummary(AgentQueryParams params);

  /** No period comparison. L0 only. */
  ProcessBreakdownResponse getProcessBreakdown(AgentQueryParams params);

  /** No period comparison. L0/L1. */
  TrendsResponse getTrends(AgentQueryParams params);

  /** No period comparison. L0/L1; incidentRateByVersion is null at L0. */
  ChartsResponse getCharts(AgentQueryParams params);

  /** Authorized process definitions that have agent runs in the given date range. L0 only. */
  List<DefinitionKeyResponseDto> getProcessDefinitionsWithAgentRuns(
      AgentQueryParams params, String userId);
}
