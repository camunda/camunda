/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.report.command.Command;
import io.camunda.optimize.service.db.es.report.command.NotSupportedCommand;
import io.camunda.optimize.service.db.es.report.command.ProcessCmd;
import io.camunda.optimize.service.exceptions.OptimizeException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class CombinedReportEvaluator {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CombinedReportEvaluator.class);
  private final SingleReportEvaluator singleReportEvaluatorInjected;
  private final DatabaseClient databaseClient;

  public CombinedReportEvaluator(
      final SingleReportEvaluator singleReportEvaluator, final DatabaseClient databaseClient) {
    singleReportEvaluatorInjected = singleReportEvaluator;
    this.databaseClient = databaseClient;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T> List<SingleReportEvaluationResult<T>> evaluate(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
      final ZoneId timezone) {
    final SingleReportEvaluatorForCombinedReports singleReportEvaluator =
        new SingleReportEvaluatorForCombinedReports(singleReportEvaluatorInjected);

    addIntervalsToReportEvaluator(singleReportDefinitions, singleReportEvaluator, timezone);
    return singleReportDefinitions.stream()
        .map(report -> evaluateWithoutThrowingError(report, singleReportEvaluator, timezone))
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
    final SingleReportEvaluatorForCombinedReports singleReportEvaluator =
        new SingleReportEvaluatorForCombinedReports(singleReportEvaluatorInjected);
    final List<QueryBuilder> baseQueries =
        getAllBaseQueries(singleReportDefinitions, singleReportEvaluator);
    final QueryBuilder instanceCountRequestQuery = createInstanceCountRequestQueries(baseQueries);
    try {
      return databaseClient.count(
          new String[] {PROCESS_INSTANCE_MULTI_ALIAS}, instanceCountRequestQuery);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not count instances in combined report with single report IDs: [%s]",
              singleReportDefinitions.stream().map(ReportDefinitionDto::getId));
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    } catch (final RuntimeException e) {
      if (isInstanceIndexNotFoundException(e)) {
        log.info(
            "Could not evaluate combined instance count because no instance indices exist. "
                + "Returning a count of 0 instead.");
        return 0L;
      } else {
        throw e;
      }
    }
  }

  private QueryBuilder createInstanceCountRequestQueries(final List<QueryBuilder> baseQueries) {
    final BoolQueryBuilder baseQuery = new BoolQueryBuilder();
    baseQueries.forEach(baseQuery::should);
    return baseQuery;
  }

  private List<QueryBuilder> getAllBaseQueries(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
      final SingleReportEvaluatorForCombinedReports singleReportEvaluator) {
    return singleReportDefinitions.stream()
        .filter(
            reportDefinition ->
                singleReportEvaluator.extractCommands(reportDefinition).stream()
                    .noneMatch(command -> command.getClass().equals(NotSupportedCommand.class)))
        .map(
            reportDefinition -> {
              final ProcessCmd<?> command =
                  (ProcessCmd<?>) singleReportEvaluator.extractCommands(reportDefinition).get(0);
              final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
                  reportEvaluationContext = new ReportEvaluationContext<>();
              reportEvaluationContext.setReportDefinition(reportDefinition);
              return command.getBaseQuery(reportEvaluationContext);
            })
        .collect(toList());
  }

  private void addIntervalsToReportEvaluator(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
      final SingleReportEvaluatorForCombinedReports singleReportEvaluator,
      final ZoneId timezone) {
    final CombinedIntervalSelectionCalculator combinedIntervalCalculator =
        new CombinedIntervalSelectionCalculator();

    singleReportDefinitions.forEach(
        reportDefinition -> {
          final Command<?, SingleProcessReportDefinitionRequestDto> command =
              singleReportEvaluator.extractCommands(reportDefinition).get(0);
          final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
              reportEvaluationContext = new ReportEvaluationContext<>();
          reportEvaluationContext.setReportDefinition(reportDefinition);
          reportEvaluationContext.setTimezone(timezone);

          final Optional<MinMaxStatDto> minMaxStatDto =
              command.getGroupByMinMaxStats(reportEvaluationContext);
          minMaxStatDto.ifPresent(combinedIntervalCalculator::addStat);
        });
    combinedIntervalCalculator
        .getGlobalMinMaxStats()
        .ifPresent(singleReportEvaluator::setCombinedRangeMinMaxStats);
  }

  private Optional<SingleReportEvaluationResult<?>> evaluateWithoutThrowingError(
      final SingleProcessReportDefinitionRequestDto reportDefinition,
      final SingleReportEvaluatorForCombinedReports singleReportEvaluator,
      final ZoneId timezone) {
    Optional<SingleReportEvaluationResult<?>> result = Optional.empty();
    try {
      final ReportEvaluationContext<ReportDefinitionDto<?>> reportEvaluationContext =
          new ReportEvaluationContext<>();
      reportEvaluationContext.setReportDefinition(reportDefinition);
      reportEvaluationContext.setTimezone(timezone);
      final SingleReportEvaluationResult<?> singleResult =
          singleReportEvaluator.evaluate(reportEvaluationContext);
      result = Optional.of(singleResult);
    } catch (final OptimizeException | OptimizeValidationException onlyForLogging) {
      // we just ignore reports that cannot be evaluated in a combined report
      log.debug(
          "Single report with id [{}] could not be evaluated for a combined report.",
          reportDefinition.getId(),
          onlyForLogging);
    }
    return result;
  }

  private static class SingleReportEvaluatorForCombinedReports extends SingleReportEvaluator {

    private MinMaxStatDto combinedRangeMinMaxStats;

    private SingleReportEvaluatorForCombinedReports(final SingleReportEvaluator evaluator) {
      super(
          evaluator.configurationService,
          evaluator.notSupportedCommand,
          evaluator.applicationContext,
          evaluator.commandSuppliers);
    }

    @Override
    public <T> SingleReportEvaluationResult<T> evaluate(
        final ReportEvaluationContext<ReportDefinitionDto<?>> reportEvaluationContext)
        throws OptimizeException {
      reportEvaluationContext.setCombinedRangeMinMaxStats(combinedRangeMinMaxStats);
      return super.evaluate(reportEvaluationContext);
    }

    public void setCombinedRangeMinMaxStats(final MinMaxStatDto combinedRangeMinMaxStats) {
      this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
    }
  }
}
