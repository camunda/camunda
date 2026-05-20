/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader.agentic;

import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import io.camunda.optimize.dto.optimize.query.agentic.SummaryResult;
import io.camunda.optimize.dto.optimize.rest.agentic.ChartsResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessBreakdownResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.TrendsResponse;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * ES/OS query layer for the Agentic Control Plane. One method per endpoint. No period-comparison
 * logic here — returns raw results only. Every implementation must apply
 * AgentBaselineFilterBuilder.build(params) as its root filter.
 */
@NullMarked
public interface AgenticControlPlaneRepository {

  /** Scalar KPIs: runs, duration percentiles, token averages, incident rate. L0/L1. */
  SummaryResult getSummary(AgentQueryParams params);

  /** Top processes ranked by total tokens consumed. L0 only. */
  ProcessBreakdownResponse getProcessBreakdown(AgentQueryParams params);

  /** All time-series trend data: token lines, token bands, duration bands. L0/L1. */
  TrendsResponse getTrends(AgentQueryParams params);

  /**
   * Bar chart data. Always: toolFrequency, avgTokensPerCall (per process). L1 only (when
   * processDefinitionKey != null): incidentRateByVersion.
   */
  ChartsResponse getCharts(AgentQueryParams params);

  /** All distinct processDefinitionKeys that have agent runs in the given date range. L0 only. */
  Set<String> getProcessDefinitionKeysWithAgentRuns(AgentQueryParams params);
}
