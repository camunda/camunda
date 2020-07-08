/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Slf4j
@Component
public class CombinedReportEvaluator {

  private final SingleReportEvaluator singleReportEvaluatorInjected;
  private final DateTimeFormatter dateTimeFormatter;
  private final OptimizeElasticsearchClient esClient;

  public CombinedReportEvaluator(final SingleReportEvaluator singleReportEvaluator,
                                 final DateTimeFormatter dateTimeFormatter,
                                 final OptimizeElasticsearchClient esClient) {
    this.singleReportEvaluatorInjected = singleReportEvaluator;
    this.dateTimeFormatter = dateTimeFormatter;
    this.esClient = esClient;
  }

  public List<ReportEvaluationResult> evaluate(List<SingleProcessReportDefinitionDto> singleReportDefinitions) {
    final SingleReportEvaluatorForCombinedReports singleReportEvaluator =
      new SingleReportEvaluatorForCombinedReports(singleReportEvaluatorInjected);

    addIntervalsToReportEvaluator(singleReportDefinitions, singleReportEvaluator);
    List<ReportEvaluationResult> resultList = new ArrayList<>();
    for (SingleProcessReportDefinitionDto report : singleReportDefinitions) {
      Optional<ReportEvaluationResult> singleReportResult = evaluateWithoutThrowingError(
        report,
        singleReportEvaluator
      );
      singleReportResult.ifPresent(resultList::add);
    }
    return resultList;
  }

  public long evaluateCombinedReportInstanceCount(List<SingleProcessReportDefinitionDto> singleReportDefinitions) {
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

  private List<BoolQueryBuilder> getAllBaseQueries(List<SingleProcessReportDefinitionDto> singleReportDefinitions,
                                                   SingleReportEvaluatorForCombinedReports singleReportEvaluator) {
    return singleReportDefinitions
      .stream()
      .filter(reportDefinition -> !(singleReportEvaluator.extractCommand(reportDefinition) instanceof NotSupportedCommand))
      .map(
        reportDefinition -> {
          ProcessCmd<?> command = (ProcessCmd) singleReportEvaluator.extractCommand(reportDefinition);
          CommandContext<SingleProcessReportDefinitionDto> commandContext = new CommandContext<>();
          commandContext.setReportDefinition(reportDefinition);
          return command.getBaseQuery(commandContext);
        }
      )
      .collect(toList());
  }

  private void addIntervalsToReportEvaluator(List<SingleProcessReportDefinitionDto> singleReportDefinitions,
                                             SingleReportEvaluatorForCombinedReports singleReportEvaluator) {
    CombinedAutomaticDateIntervalSelectionCalculator dateIntervalCalculator =
      new CombinedAutomaticDateIntervalSelectionCalculator(dateTimeFormatter);
    CombinedNumberIntervalSelectionCalculator numberRangeCalculator = new CombinedNumberIntervalSelectionCalculator();

    singleReportDefinitions
      .forEach(
        reportDefinition -> {
          Command<?> command = singleReportEvaluator.extractCommand(reportDefinition);
          CommandContext<SingleProcessReportDefinitionDto> commandContext = new CommandContext<>();
          commandContext.setReportDefinition(reportDefinition);

          Optional<MinMaxStatDto> dateStats = command.retrieveStatsForCombinedAutomaticGroupByDate(commandContext);
          dateStats.ifPresent(dateIntervalCalculator::addStat);

          Optional<MinMaxStatDto> numberStat = command.retrieveStatsForCombinedGroupByNumberVariable(commandContext);
          numberStat.ifPresent(numberRangeCalculator::addStat);
        }
      );
    Optional<Range<OffsetDateTime>> dateHistogramIntervalForCombinedReport = dateIntervalCalculator.calculateInterval();
    dateHistogramIntervalForCombinedReport.ifPresent(
      singleReportEvaluator::setDateIntervalRange
    );
    Optional<Range<Double>> numberIntervalForCombinedReport = numberRangeCalculator.calculateInterval();
    numberIntervalForCombinedReport.ifPresent(
      singleReportEvaluator::setNumberVariableRange
    );
  }

  private Optional<ReportEvaluationResult> evaluateWithoutThrowingError(SingleProcessReportDefinitionDto reportDefinition,
                                                                        SingleReportEvaluatorForCombinedReports singleReportEvaluator) {
    Optional<ReportEvaluationResult> result = Optional.empty();
    try {
      CommandContext<SingleProcessReportDefinitionDto> commandContext = new CommandContext<>();
      commandContext.setReportDefinition(reportDefinition);
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
    private Range<OffsetDateTime> dateIntervalRange;
    @Setter
    private Range<Double> numberVariableRange;

    private SingleReportEvaluatorForCombinedReports(SingleReportEvaluator evaluator) {
      super(
        evaluator.notSupportedCommand,
        evaluator.applicationContext,
        evaluator.commandSuppliers.values()
      );
    }

    @Override
    <T extends ReportDefinitionDto<?>> ReportEvaluationResult<?, T> evaluate(final CommandContext<T> commandContext)
      throws OptimizeException {
      commandContext.setDateIntervalRange(dateIntervalRange);
      commandContext.setNumberVariableRange(numberVariableRange);
      return super.evaluate(commandContext);
    }
  }
}
