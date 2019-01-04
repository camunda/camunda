package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ReportCommand <T extends ReportResultDto>  implements Command {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ReportDataDto reportData;
  protected RestHighLevelClient esClient;
  protected ConfigurationService configurationService;
  protected ObjectMapper objectMapper;


  @Override
  public T evaluate(CommandContext commandContext) throws OptimizeException {
    reportData = commandContext.getReportData();
    esClient = commandContext.getEsClient();
    configurationService = commandContext.getConfigurationService();
    objectMapper = commandContext.getObjectMapper();
    beforeEvaluate(commandContext);

    T evaluationResult = evaluate();
    return filterResultData(evaluationResult);
  }

  protected abstract void beforeEvaluate(CommandContext commandContext);

  protected T filterResultData(T evaluationResult) {
    return evaluationResult;
  }

  protected abstract T evaluate() throws OptimizeException;

}
