/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.report.command.AutomaticGroupByDateCommand;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class CombinedReportEvaluator {

  private final SingleReportEvaluator singleReportEvaluatorInjected;
  private final DateTimeFormatter dateTimeFormatter;

  public CombinedReportEvaluator(final SingleReportEvaluator singleReportEvaluator,
                                 final DateTimeFormatter dateTimeFormatter) {
    this.singleReportEvaluatorInjected = singleReportEvaluator;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public List<ReportEvaluationResult> evaluate(List<SingleProcessReportDefinitionDto> singleReportDefinitions) {
    final SingleReportEvaluatorForCombinedReports singleReportEvaluator =
      new SingleReportEvaluatorForCombinedReports(singleReportEvaluatorInjected);

    addIntervalToReportEvaluator(singleReportDefinitions, singleReportEvaluator);
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

  private void addIntervalToReportEvaluator(List<SingleProcessReportDefinitionDto> singleReportDefinitions,
                                            SingleReportEvaluatorForCombinedReports singleReportEvaluator) {
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
      singleReportEvaluator::setDateIntervalRange
    );
  }

  private Optional<ReportEvaluationResult> evaluateWithoutThrowingError(SingleProcessReportDefinitionDto reportDefinition,
                                                                        SingleReportEvaluatorForCombinedReports singleReportEvaluator) {
    Optional<ReportEvaluationResult> result = Optional.empty();
    try {
      ReportEvaluationResult singleResult = singleReportEvaluator.evaluate(reportDefinition);
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

  private class SingleReportEvaluatorForCombinedReports extends SingleReportEvaluator {

    @Setter
    private Range<OffsetDateTime> dateIntervalRange;

    private SingleReportEvaluatorForCombinedReports(SingleReportEvaluator evaluator) {
      super(
        evaluator.configurationService,
        evaluator.objectMapper,
        evaluator.processQueryFilterEnhancer,
        evaluator.decisionQueryFilterEnhancer,
        evaluator.esClient,
        evaluator.intervalAggregationService,
        evaluator.processDefinitionReader,
        evaluator.decisionDefinitionReader,
        evaluator.applicationContext,
        evaluator.commands
      );
    }

    @Override
    protected <T extends ReportDefinitionDto> CommandContext<T> createCommandContext(final T reportDefinition,
                                                                                     final Integer customRecordLimit) {
      CommandContext<T> commandContext = super.createCommandContext(reportDefinition, customRecordLimit);
      commandContext.setDateIntervalRange(dateIntervalRange);
      return commandContext;
    }
  }
}
