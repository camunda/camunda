package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class ReportCommand implements Command {

  private static final String ALL_VERSIONS = "ALL";
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

  protected BoolQueryBuilder setupBaseQuery(String processDefinitionId, String processDefinitionKey, String processDefinitionVersion) {
    BoolQueryBuilder query;
    if (processDefinitionKey != null && processDefinitionVersion != null) {
      query = boolQuery()
          .must(termQuery("processDefinitionKey", processDefinitionKey));
      if (!ALL_VERSIONS.equalsIgnoreCase(processDefinitionVersion)) {
        query = query
            .must(termQuery("processDefinitionVersion", processDefinitionVersion));
      }
    } else {
      query = boolQuery()
          .must(termQuery("processDefinitionId", processDefinitionId));
    }
    return query;
  }
}
