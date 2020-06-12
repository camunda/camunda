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
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.search.aggregations.metrics.Stats;
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
    CombinedAutomaticDateIntervalSelectionCalculator dateIntervalCalculator =
      new CombinedAutomaticDateIntervalSelectionCalculator(dateTimeFormatter);
    CombinedNumberIntervalSelectionCalculator numberRangeCalculator = new CombinedNumberIntervalSelectionCalculator();

    singleReportDefinitions
      .forEach(
        reportDefinition -> {
          Command<?> command = singleReportEvaluator.extractCommand(reportDefinition);
          CommandContext<SingleProcessReportDefinitionDto> commandContext = new CommandContext<>();
          commandContext.setReportDefinition(reportDefinition);

          Optional<Stats> dateStat = command.calculateDateRangeForAutomaticGroupByDate(commandContext);
          dateStat.ifPresent(dateIntervalCalculator::addStat);

          Optional<Stats> numberStat = command.calculateNumberRangeForCombinedGroupByNumberVariable(commandContext);
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
