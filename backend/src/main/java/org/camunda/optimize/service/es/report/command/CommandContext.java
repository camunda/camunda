package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;

public class CommandContext {
  
  private Client esclient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;
  private QueryFilterEnhancer queryFilterEnhancer;
  private ReportDataDto reportData;

  public Client getEsclient() {
    return esclient;
  }

  public void setEsclient(Client esclient) {
    this.esclient = esclient;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  public void setConfigurationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public QueryFilterEnhancer getQueryFilterEnhancer() {
    return queryFilterEnhancer;
  }

  public void setQueryFilterEnhancer(QueryFilterEnhancer queryFilterEnhancer) {
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  public ReportDataDto getReportData() {
    return reportData;
  }

  public void setReportData(ReportDataDto reportData) {
    this.reportData = reportData;
  }
}
