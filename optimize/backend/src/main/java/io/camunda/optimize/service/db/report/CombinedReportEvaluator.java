/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.report.interpreter.plan.ExecutionPlanInterpreterFacade;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CombinedReportEvaluator {
  private final ExecutionPlanExtractor executionPlanExtractor;
  private final ExecutionPlanInterpreterFacade interpreter;
  private final SingleReportEvaluator singleReportEvaluator;
  private final CombinedReportInstanceCounter<?> combinedReportInstanceCounter;

  @SuppressWarnings(UNCHECKED_CAST)
  public <T> List<SingleReportEvaluationResult<T>> evaluate(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
      final ZoneId timezone) {
    final MinMaxStatDto combinedRangeMinMaxStats;
    try {
      combinedRangeMinMaxStats =
          getGlobalMinMaxStats(singleReportDefinitions, timezone).orElse(null);
    } catch (OptimizeValidationException e) {
      log.error("Failed to evaluate combined report! Reason: ", e);
      return List.of();
    }

    return singleReportDefinitions.stream()
        .map(report -> evaluateWithoutThrowingError(report, combinedRangeMinMaxStats, timezone))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(result -> (SingleReportEvaluationResult<T>) result)
        .collect(toList());
  }

  public long evaluateCombinedReportInstanceCount(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions) {
    if (CollectionUtils.isEmpty(singleReportDefinitions)) {
      return 0L;
    }
    try {
      return combinedReportInstanceCounter.count(singleReportDefinitions);
    } catch (OptimizeValidationException e) {
      log.error("Failed to evaluate combined report instance count! Reason: ", e);
      return 0L;
    }
  }

  private Optional<MinMaxStatDto> getGlobalMinMaxStats(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
      final ZoneId timezone) {
    final CombinedIntervalSelectionCalculator combinedIntervalCalculator =
        new CombinedIntervalSelectionCalculator();

    singleReportDefinitions.forEach(
        reportDefinition -> {
          final ExecutionPlan plan =
              executionPlanExtractor.extractExecutionPlans(reportDefinition).get(0);
          final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
              reportEvaluationContext = new ReportEvaluationContext<>();
          reportEvaluationContext.setReportDefinition(reportDefinition);
          reportEvaluationContext.setTimezone(timezone);
          ExecutionContext executionContext =
              ExecutionContextFactory.buildExecutionContext(plan, reportEvaluationContext);

          Optional<MinMaxStatDto> minMaxStatDto =
              interpreter.getGroupByMinMaxStats(executionContext);
          minMaxStatDto.ifPresent(combinedIntervalCalculator::addStat);
        });
    return combinedIntervalCalculator.getGlobalMinMaxStats();
  }

  private Optional<SingleReportEvaluationResult<?>> evaluateWithoutThrowingError(
      final SingleProcessReportDefinitionRequestDto reportDefinition,
      final MinMaxStatDto combinedRangeMinMaxStats,
      final ZoneId timezone) {
    Optional<SingleReportEvaluationResult<?>> result = Optional.empty();
    try {
      ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> reportEvaluationContext =
          new ReportEvaluationContext<>();
      reportEvaluationContext.setReportDefinition(reportDefinition);
      reportEvaluationContext.setTimezone(timezone);
      reportEvaluationContext.setCombinedRangeMinMaxStats(combinedRangeMinMaxStats);
      SingleReportEvaluationResult<?> singleResult =
          singleReportEvaluator.evaluate(reportEvaluationContext);
      result = Optional.of(singleResult);
    } catch (OptimizeException | OptimizeValidationException onlyForLogging) {
      // we just ignore reports that cannot be evaluated in a combined report
      log.debug(
          "Single report with id [{}] could not be evaluated for a combined report.",
          reportDefinition.getId(),
          onlyForLogging);
    }
    return result;
  }
}
