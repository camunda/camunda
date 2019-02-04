package org.camunda.optimize.service.es.report;

import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.report.command.AutomaticGroupByDateCommand;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.ReportResult;
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

  public List<ReportResult> evaluate(List<SingleProcessReportDefinitionDto> singleReportDefinitions) {
    addIntervalToReportEvaluator(singleReportDefinitions);
    List<ReportResult> resultList = new ArrayList<>();
    for (SingleProcessReportDefinitionDto report : singleReportDefinitions) {
      Optional<ReportResult> singleReportResult = evaluateWithoutThrowingError(report);
      singleReportResult.ifPresent(resultList::add);
    }
    return resultList;
  }

  private void addIntervalToReportEvaluator(List<SingleProcessReportDefinitionDto> singleReportDefinitions) {
    CombinedAutomaticIntervalSelectionCalculator calculator =
      new CombinedAutomaticIntervalSelectionCalculator(dateTimeFormatter);
    singleReportDefinitions
      .forEach(
        r -> {
          CommandContext commandContext = singleReportEvaluator.createCommandContext(r.getData());
          Command command =
            singleReportEvaluator.extractCommand(r.getData());
          if (command instanceof AutomaticGroupByDateCommand) {
            Optional<Stats> stat = ((AutomaticGroupByDateCommand) command).evaluateGroupByDateValueStats(commandContext);
            stat.ifPresent(calculator::addStat);
          }
        }
      );
    Optional<Range<OffsetDateTime>> dateHistogramIntervalForCombinedReport = calculator.calculateInterval();
    dateHistogramIntervalForCombinedReport.ifPresent(
      interval -> singleReportEvaluator.setStartDateIntervalRange(interval)
    );
  }

  private Optional<ReportResult> evaluateWithoutThrowingError(SingleProcessReportDefinitionDto reportDefinition) {
    Optional<ReportResult> result = Optional.empty();
      try {
        ReportResult singleResult = singleReportEvaluator.evaluate(reportDefinition.getData());
        singleResult.copyMetaData(reportDefinition);
        result = Optional.of(singleResult);
      } catch (OptimizeException | OptimizeValidationException onlyForLogging) {
        // we just ignore reports that cannot be evaluated in a combined report
        logger.debug("Single report with id [{}] could not be evaluated for a combined report.",
                     reportDefinition.getId(),
                     onlyForLogging);
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
    public CommandContext createCommandContext(ReportDataDto reportData) {
      CommandContext commandContext = super.createCommandContext(reportData);
      commandContext.setDateIntervalRange(startDateIntervalRange);
      return commandContext;
    }
  }
}
