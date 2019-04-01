package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.RestHighLevelClient;

import java.time.OffsetDateTime;

public class CommandContext<T extends ReportDefinitionDto> {

  private RestHighLevelClient esClient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;
  private QueryFilterEnhancer queryFilterEnhancer;
  private T reportDefinition;
  private Range<OffsetDateTime> dateIntervalRange;
  private IntervalAggregationService intervalAggregationService;

  public RestHighLevelClient getEsClient() {
    return esClient;
  }

  public void setEsClient(RestHighLevelClient esClient) {
    this.esClient = esClient;
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

  public T getReportDefinition() {
    return reportDefinition;
  }

  public void setReportDefinition(T report) {
    this.reportDefinition = report;
  }

  public Range<OffsetDateTime> getDateIntervalRange() {
    return dateIntervalRange;
  }

  public void setDateIntervalRange(Range<OffsetDateTime> startDateInterval) {
    this.dateIntervalRange = startDateInterval;
  }

  public IntervalAggregationService getIntervalAggregationService() {
    return intervalAggregationService;
  }

  public void setIntervalAggregationService(IntervalAggregationService intervalAggregationService) {
    this.intervalAggregationService = intervalAggregationService;
  }
}
