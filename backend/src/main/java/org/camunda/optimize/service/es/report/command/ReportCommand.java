package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

public abstract class ReportCommand<R extends ReportResult, RD extends ReportDataDto> implements Command<RD> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected RD reportData;
  protected RestHighLevelClient esClient;
  protected ConfigurationService configurationService;
  protected ObjectMapper objectMapper;
  protected Range<OffsetDateTime> dateIntervalRange;


  @Override
  public R evaluate(CommandContext<RD> commandContext) throws OptimizeException {
    reportData = commandContext.getReportData();
    esClient = commandContext.getEsClient();
    configurationService = commandContext.getConfigurationService();
    objectMapper = commandContext.getObjectMapper();
    dateIntervalRange = commandContext.getDateIntervalRange();
    beforeEvaluate(commandContext);

    final R evaluationResult = evaluate();
    evaluationResult.copyReportData(reportData);
    final R filteredResultData = filterResultData(commandContext, evaluationResult);
    final R sortedResultData = sortResultData(filteredResultData);
    return sortedResultData;
  }

  protected abstract void beforeEvaluate(CommandContext<RD> commandContext);

  protected abstract R evaluate() throws OptimizeException;

  protected abstract R sortResultData(R evaluationResult);

  protected R filterResultData(CommandContext<RD> commandContext, R evaluationResult) {
    return evaluationResult;
  }

  public RD getReportData() {
    return reportData;
  }
}
