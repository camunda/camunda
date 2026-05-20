/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.agentic;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import io.camunda.optimize.dto.optimize.query.agentic.SummaryResult;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import io.camunda.optimize.dto.optimize.rest.agentic.ChartsResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessBreakdownResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.SummaryResponse;
import io.camunda.optimize.dto.optimize.rest.agentic.TrendsResponse;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.agentic.AgenticControlPlaneRepository;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class AgenticControlPlaneServiceImpl implements AgenticControlPlaneService {

  private final AgenticControlPlaneRepository repository;
  private final PeriodComparisonExecutor periodComparisonExecutor;
  private final DefinitionService definitionService;

  public AgenticControlPlaneServiceImpl(
      final AgenticControlPlaneRepository repository,
      final PeriodComparisonExecutor periodComparisonExecutor,
      final DefinitionService definitionService) {
    this.repository = repository;
    this.periodComparisonExecutor = periodComparisonExecutor;
    this.definitionService = definitionService;
  }

  @Override
  public SummaryResponse getSummary(final AgentQueryParams params) {
    final PeriodComparisonResult<SummaryResult> comparison =
        periodComparisonExecutor.execute(params, repository::getSummary);
    final SummaryResult cur = comparison.current();
    final SummaryResult prev = comparison.previous();

    final double curRate = IncidentRateHelper.computeRate(cur.incidentCount(), cur.totalRuns());
    final double prevRate = IncidentRateHelper.computeRate(prev.incidentCount(), prev.totalRuns());

    final long avgTokensPerRun =
        cur.totalRuns() > 0
            ? (cur.totalInputTokens() + cur.totalOutputTokens()) / cur.totalRuns()
            : 0L;

    final Long totalRunsDelta = prev.totalRuns() > 0 ? cur.totalRuns() - prev.totalRuns() : null;
    final Long avgDurationDelta =
        prev.totalRuns() > 0 ? cur.avgDurationMs() - prev.avgDurationMs() : null;
    final Double incidentRateDelta = prev.totalRuns() > 0 ? curRate - prevRate : null;

    return new SummaryResponse(
        cur.totalRuns(),
        totalRunsDelta,
        cur.avgDurationMs(),
        avgDurationDelta,
        curRate,
        incidentRateDelta,
        cur.incidentCount(),
        cur.totalRuns(), // activationCount = totalRuns at L0 and L1
        avgTokensPerRun,
        cur.medianTokensPerRun(),
        cur.p50DurationMs(),
        cur.p95DurationMs());
  }

  @Override
  public ProcessBreakdownResponse getProcessBreakdown(final AgentQueryParams params) {
    return repository.getProcessBreakdown(params);
  }

  @Override
  public TrendsResponse getTrends(final AgentQueryParams params) {
    return repository.getTrends(params);
  }

  @Override
  public ChartsResponse getCharts(final AgentQueryParams params) {
    return repository.getCharts(params);
  }

  @Override
  public List<DefinitionKeyResponseDto> getProcessDefinitionsWithAgentRuns(
      final AgentQueryParams params, final String userId) {
    final Set<String> agentKeys = repository.getProcessDefinitionKeysWithAgentRuns(params);
    return definitionService.getFullyImportedDefinitions(DefinitionType.PROCESS, userId).stream()
        .filter(d -> agentKeys.contains(d.getKey()))
        .map(d -> new DefinitionKeyResponseDto(d.getKey(), d.getName()))
        .toList();
  }
}
