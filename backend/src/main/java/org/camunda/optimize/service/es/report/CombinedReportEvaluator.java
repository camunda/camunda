/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

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

  public List<ReportEvaluationResult> evaluate(final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
                                               final ZoneId timezone) {
    final SingleReportEvaluatorForCombinedReports singleReportEvaluator =
      new SingleReportEvaluatorForCombinedReports(singleReportEvaluatorInjected);

    addIntervalsToReportEvaluator(singleReportDefinitions, singleReportEvaluator, timezone);
    List<ReportEvaluationResult> resultList = new ArrayList<>();
    for (SingleProcessReportDefinitionRequestDto report : singleReportDefinitions) {
      Optional<ReportEvaluationResult> singleReportResult =
        evaluateWithoutThrowingError(report, singleReportEvaluator, timezone);
      singleReportResult.ifPresent(resultList::add);
    }
    return resultList;
  }

  public long evaluateCombinedReportInstanceCount(List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions) {
    if (CollectionUtils.isEmpty(singleReportDefinitions)) {
      return 0L;
    }
    final SingleReportEvaluatorForCombinedReports singleReportEvaluator =
      new SingleReportEvaluatorForCombinedReports(singleReportEvaluatorInjected);
    final List<BoolQueryBuilder> baseQueries = getAllBaseQueries(singleReportDefinitions, singleReportEvaluator);
    final CountRequest instanceCountRequest = createInstanceCountRequest(baseQueries);
    try {
      return esClient.count(instanceCountRequest, RequestOptions.DEFAULT).getCount();
    } catch (IOException e) {
      final String message = String.format(
        "Could not count instances in combined report with single report IDs: [%s]",
        singleReportDefinitions.stream().map(def -> def.getId())
      );
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private CountRequest createInstanceCountRequest(List<BoolQueryBuilder> baseQueries) {
    final BoolQueryBuilder baseQuery = new BoolQueryBuilder();
    baseQueries.forEach(baseQuery::should);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery);

    return new CountRequest(PROCESS_INSTANCE_INDEX_NAME).source(searchSourceBuilder);
  }

  private List<BoolQueryBuilder> getAllBaseQueries(List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions,
                                                   SingleReportEvaluatorForCombinedReports singleReportEvaluator) {
    return singleReportDefinitions
      .stream()
      .filter(reportDefinition -> !(singleReportEvaluator.extractCommand(reportDefinition) instanceof NotSupportedCommand))
      .map(
        reportDefinition -> {
          ProcessCmd<?> command = (ProcessCmd) singleReportEvaluator.extractCommand(reportDefinition);
          CommandContext<SingleProcessReportDefinitionRequestDto> commandContext = new CommandContext<>();
          commandContext.setReportDefinition(reportDefinition);
          return command.getBaseQuery(commandContext);
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
          final Command<SingleProcessReportDefinitionRequestDto> command =
            singleReportEvaluator.extractCommand(reportDefinition);
          final CommandContext<SingleProcessReportDefinitionRequestDto> commandContext = new CommandContext<>();
          commandContext.setReportDefinition(reportDefinition);
          commandContext.setTimezone(timezone);

          Optional<MinMaxStatDto> minMaxStatDto = command.getGroupByMinMaxStats(commandContext);
          minMaxStatDto.ifPresent(combinedIntervalCalculator::addStat);
        }
      );
    combinedIntervalCalculator.getGlobalMinMaxStats().ifPresent(singleReportEvaluator::setCombinedRangeMinMaxStats);
  }

  private Optional<ReportEvaluationResult> evaluateWithoutThrowingError(final SingleProcessReportDefinitionRequestDto reportDefinition,
                                                                        final SingleReportEvaluatorForCombinedReports singleReportEvaluator,
                                                                        final ZoneId timezone) {
    Optional<ReportEvaluationResult> result = Optional.empty();
    try {
      CommandContext<SingleProcessReportDefinitionRequestDto> commandContext = new CommandContext<>();
      commandContext.setReportDefinition(reportDefinition);
      commandContext.setTimezone(timezone);
      ReportEvaluationResult singleResult = singleReportEvaluator.evaluate(commandContext);
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
        evaluator.commandSuppliers.values()
      );
    }

    @Override
    <T extends ReportDefinitionDto<?>> ReportEvaluationResult<?, T> evaluate(final CommandContext<T> commandContext)
      throws OptimizeException {
      commandContext.setCombinedRangeMinMaxStats(combinedRangeMinMaxStats);
      return super.evaluate(commandContext);
    }
  }
}
