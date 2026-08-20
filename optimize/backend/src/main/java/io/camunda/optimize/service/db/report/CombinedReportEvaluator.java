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
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class CombinedReportEvaluator {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CombinedReportEvaluator.class);
  private final ExecutionPlanExtractor executionPlanExtractor;
  private final ExecutionPlanInterpreterFacade interpreter;
  private final SingleReportEvaluator singleReportEvaluator;
  private final CombinedReportInstanceCounter<?> combinedReportInstanceCounter;

  public CombinedReportEvaluator(
      final ExecutionPlanExtractor executionPlanExtractor,
      final ExecutionPlanInterpreterFacade interpreter,
      final SingleReportEvaluator singleReportEvaluator,
      final CombinedReportInstanceCounter<?> combinedReportInstanceCounter) {
    this.executionPlanExtractor = executionPlanExtractor;
    this.interpreter = interpreter;
    this.singleReportEvaluator = singleReportEvaluator;
    this.combinedReportInstanceCounter = combinedReportInstanceCounter;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T> List<SingleReportEvaluationResult<T>> evaluate(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
      final ZoneId timezone) {
    final MinMaxStatDto combinedRangeMinMaxStats;
    try {
      combinedRangeMinMaxStats =
          getGlobalMinMaxStats(singleReportDefinitions, timezone).orElse(null);
    } catch (final OptimizeValidationException e) {
      LOG.error("Failed to evaluate combined report! Reason: ", e);
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
    } catch (final OptimizeValidationException e) {
      LOG.error("Failed to evaluate combined report instance count! Reason: ", e);
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
          final ExecutionContext executionContext =
              ExecutionContextFactory.buildExecutionContext(plan, reportEvaluationContext);

          final Optional<MinMaxStatDto> minMaxStatDto =
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
      final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
          reportEvaluationContext = new ReportEvaluationContext<>();
      reportEvaluationContext.setReportDefinition(reportDefinition);
      reportEvaluationContext.setTimezone(timezone);
      reportEvaluationContext.setCombinedRangeMinMaxStats(combinedRangeMinMaxStats);
      final SingleReportEvaluationResult<?> singleResult =
          singleReportEvaluator.evaluate(reportEvaluationContext);
      result = Optional.of(singleResult);
    } catch (final OptimizeException | OptimizeValidationException onlyForLogging) {
      // we just ignore reports that cannot be evaluated in a combined report
      LOG.debug(
          "Single report with id [{}] could not be evaluated for a combined report.",
          reportDefinition.getId(),
          onlyForLogging);
    }
    return result;
  }
}
