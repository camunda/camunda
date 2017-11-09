package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class ReportCommand implements Command {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ReportDataDto reportData;
  protected  Client esclient;
  protected  ConfigurationService configurationService;
  protected  ObjectMapper objectMapper;
  protected  QueryFilterEnhancer queryFilterEnhancer;

  @Override
  public ReportResultDto evaluate(CommandContext commandContext) throws IOException, OptimizeException {
    reportData = commandContext.getReportData();
    esclient = commandContext.getEsclient();
    configurationService = commandContext.getConfigurationService();
    objectMapper = commandContext.getObjectMapper();
    queryFilterEnhancer = commandContext.getQueryFilterEnhancer();
    return evaluate();
  }

  protected abstract ReportResultDto evaluate() throws IOException, OptimizeException;
}
