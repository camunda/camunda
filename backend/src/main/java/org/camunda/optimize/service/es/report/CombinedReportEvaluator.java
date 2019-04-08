/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.report.command.AutomaticGroupByDateCommand;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class CombinedReportEvaluator {
  private static final Logger logger = LoggerFactory.getLogger(CombinedReportEvaluator.class);

  private SingleReportEvaluatorForCombinedReports singleReportEvaluator;
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  public CombinedReportEvaluator(SingleReportEvaluator singleReportEvaluator, DateTimeFormatter dateTimeFormatter) {
    this.singleReportEvaluator = new SingleReportEvaluatorForCombinedReports(singleReportEvaluator);
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public List<ReportEvaluationResult> evaluate(List<SingleProcessReportDefinitionDto> singleReportDefinitions) {
    addIntervalToReportEvaluator(singleReportDefinitions);
    List<ReportEvaluationResult> resultList = new ArrayList<>();
    for (SingleProcessReportDefinitionDto report : singleReportDefinitions) {
      Optional<ReportEvaluationResult> singleReportResult = evaluateWithoutThrowingError(report);
      singleReportResult.ifPresent(resultList::add);
    }
    return resultList;
  }

  private void addIntervalToReportEvaluator(List<SingleProcessReportDefinitionDto> singleReportDefinitions) {
    CombinedAutomaticIntervalSelectionCalculator calculator =
      new CombinedAutomaticIntervalSelectionCalculator(dateTimeFormatter);
    singleReportDefinitions
      .forEach(
        reportDefinition -> {
          CommandContext commandContext = singleReportEvaluator.createCommandContext(reportDefinition);
          Command command = singleReportEvaluator.extractCommand(reportDefinition);
          if (command instanceof AutomaticGroupByDateCommand) {
            Optional<Stats> stat =
              ((AutomaticGroupByDateCommand) command).evaluateGroupByDateValueStats(commandContext);
            stat.ifPresent(calculator::addStat);
          }
        }
      );
    Optional<Range<OffsetDateTime>> dateHistogramIntervalForCombinedReport = calculator.calculateInterval();
    dateHistogramIntervalForCombinedReport.ifPresent(
      interval -> singleReportEvaluator.setStartDateIntervalRange(interval)
    );
  }

  private Optional<ReportEvaluationResult> evaluateWithoutThrowingError(SingleProcessReportDefinitionDto reportDefinition) {
    Optional<ReportEvaluationResult> result = Optional.empty();
    try {
      ReportEvaluationResult singleResult = singleReportEvaluator.evaluate(reportDefinition);
      result = Optional.of(singleResult);
    } catch (OptimizeException | OptimizeValidationException onlyForLogging) {
      // we just ignore reports that cannot be evaluated in a combined report
      logger.debug(
        "Single report with id [{}] could not be evaluated for a combined report.",
        reportDefinition.getId(),
        onlyForLogging
      );
    }
    return result;
  }

  private class SingleReportEvaluatorForCombinedReports extends SingleReportEvaluator {

    private Range<OffsetDateTime> startDateIntervalRange;

    private SingleReportEvaluatorForCombinedReports(SingleReportEvaluator evaluator) {
      super(
        evaluator.configurationService,
        evaluator.objectMapper,
        evaluator.processQueryFilterEnhancer,
        evaluator.decisionQueryFilterEnhancer,
        evaluator.esClient,
        evaluator.intervalAggregationService
      );
    }

    public void setStartDateIntervalRange(Range<OffsetDateTime> startDateIntervalRange) {
      this.startDateIntervalRange = startDateIntervalRange;
    }

    @Override
    protected <T extends ReportDefinitionDto> CommandContext<T> createCommandContext(final T reportDefinition) {
      CommandContext<T> commandContext = super.createCommandContext(reportDefinition);
      commandContext.setDateIntervalRange(startDateIntervalRange);
      return commandContext;
    }
  }
}
