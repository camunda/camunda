/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Slf4j
@Component
public class CombinedReportEvaluator {

  private final SingleReportEvaluator singleReportEvaluatorInjected;
  private final OptimizeElasticsearchClient esClient;

  public CombinedReportEvaluator(final SingleReportEvaluator singleReportEvaluator,
                                 final OptimizeElasticsearchClient esClient) {
    this.singleReportEvaluatorInjected = singleReportEvaluator;
    this.esClient = esClient;
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
      .collect(Collectors.toList());
  }

  public long evaluateCombinedReportInstanceCount(List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions) {
    if (CollectionUtils.isEmpty(singleReportDefinitions)) {
      return 0L;
    }
    final SingleReportEvaluatorForCombinedReports singleReportEvaluator =
      new SingleReportEvaluatorForCombinedReports(singleReportEvaluatorInjected);
    final List<QueryBuilder> baseQueries = getAllBaseQueries(singleReportDefinitions, singleReportEvaluator);
    final CountRequest instanceCountRequest = createInstanceCountRequest(baseQueries);
    try {
      return esClient.count(instanceCountRequest).getCount();
    } catch (IOException e) {
      final String message = String.format(
        "Could not count instances in combined report with single report IDs: [%s]",
        singleReportDefinitions.stream().map(ReportDefinitionDto::getId)
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(e)) {
        log.info(
          "Could not evaluate combined instance count because no instance indices exist. " +
            "Returning a count of 0 instead."
        );
        return 0L;
      } else {
        throw e;
      }
    }
  }

  private CountRequest createInstanceCountRequest(final List<QueryBuilder> baseQueries) {
    final BoolQueryBuilder baseQuery = new BoolQueryBuilder();
    baseQueries.forEach(baseQuery::should);
    return new CountRequest(PROCESS_INSTANCE_MULTI_ALIAS).query(baseQuery);
  }

  private List<QueryBuilder> getAllBaseQueries(List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
                                               SingleReportEvaluatorForCombinedReports singleReportEvaluator) {
    return singleReportDefinitions
      .stream()
      .filter(reportDefinition ->
                singleReportEvaluator.extractCommands(reportDefinition).stream()
                  .noneMatch(command -> command.getClass().equals(NotSupportedCommand.class))
      )
      .map(
        reportDefinition -> {
          ProcessCmd<?> command = (ProcessCmd<?>) singleReportEvaluator.extractCommands(reportDefinition).get(0);
          ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> reportEvaluationContext =
            new ReportEvaluationContext<>();
          reportEvaluationContext.setReportDefinition(reportDefinition);
          return command.getBaseQuery(reportEvaluationContext);
        }
      )
      .collect(toList());
  }

  private void addIntervalsToReportEvaluator(final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
                                             final SingleReportEvaluatorForCombinedReports singleReportEvaluator,
                                             final ZoneId timezone) {
    final CombinedIntervalSelectionCalculator combinedIntervalCalculator = new CombinedIntervalSelectionCalculator();

    singleReportDefinitions
      .forEach(
        reportDefinition -> {
          final Command<?, SingleProcessReportDefinitionRequestDto> command = singleReportEvaluator
            .extractCommands(reportDefinition).get(0);
          final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> reportEvaluationContext =
            new ReportEvaluationContext<>();
          reportEvaluationContext.setReportDefinition(reportDefinition);
          reportEvaluationContext.setTimezone(timezone);

          Optional<MinMaxStatDto> minMaxStatDto = command.getGroupByMinMaxStats(reportEvaluationContext);
          minMaxStatDto.ifPresent(combinedIntervalCalculator::addStat);
        }
      );
    combinedIntervalCalculator.getGlobalMinMaxStats().ifPresent(singleReportEvaluator::setCombinedRangeMinMaxStats);
  }

  private Optional<SingleReportEvaluationResult<?>> evaluateWithoutThrowingError(
    final SingleProcessReportDefinitionRequestDto reportDefinition,
    final SingleReportEvaluatorForCombinedReports singleReportEvaluator,
    final ZoneId timezone) {
    Optional<SingleReportEvaluationResult<?>> result = Optional.empty();
    try {
      ReportEvaluationContext<ReportDefinitionDto<?>> reportEvaluationContext = new ReportEvaluationContext<>();
      reportEvaluationContext.setReportDefinition(reportDefinition);
      reportEvaluationContext.setTimezone(timezone);
      SingleReportEvaluationResult<?> singleResult = singleReportEvaluator.evaluate(reportEvaluationContext);
      result = Optional.of(singleResult);
    } catch (OptimizeException | OptimizeValidationException onlyForLogging) {
      // we just ignore reports that cannot be evaluated in a combined report
      log.debug(
        "Single report with id [{}] could not be evaluated for a combined report.",
        reportDefinition.getId(),
        onlyForLogging
      );
    }
    return result;
  }

  private static class SingleReportEvaluatorForCombinedReports extends SingleReportEvaluator {

    @Setter
    private MinMaxStatDto combinedRangeMinMaxStats;

    private SingleReportEvaluatorForCombinedReports(final SingleReportEvaluator evaluator) {
      super(
        evaluator.configurationService,
        evaluator.notSupportedCommand,
        evaluator.applicationContext,
        evaluator.commandSuppliers
      );
    }

    @Override
    public <T> SingleReportEvaluationResult<T> evaluate(
      final ReportEvaluationContext<ReportDefinitionDto<?>> reportEvaluationContext)
      throws OptimizeException {
      reportEvaluationContext.setCombinedRangeMinMaxStats(combinedRangeMinMaxStats);
      return super.evaluate(reportEvaluationContext);
    }
  }
}
