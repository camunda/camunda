package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.es.schema.type.AlertType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;


@Component
public class AlertReader {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public List<AlertDefinitionDto> getStoredAlerts() {
    logger.debug("getting all stored alerts");
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getAlertType()))
        .setTypes(configurationService.getAlertType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
      .setSize(1000)
      .get();

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      AlertDefinitionDto.class,
      objectMapper,
      esclient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public AlertDefinitionDto findAlert(String alertId) {
    logger.debug("Fetching alert with id [{}]", alertId);
    GetResponse getResponse = esclient
        .prepareGet(
            configurationService.getOptimizeIndex(configurationService.getAlertType()),
            configurationService.getAlertType(),
            alertId
        )
        .setRealtime(false)
        .get();

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, AlertDefinitionDto.class);
      } catch (IOException e) {
        logError(alertId);
        throw new OptimizeRuntimeException("Can't fetch alert");
      }
    } else {
      logError(alertId);
      throw new OptimizeRuntimeException("Alert does not exist!");
    }
  }

  public List<AlertDefinitionDto> findFirstAlertsForReport(String reportId) {
    // Note: this is capped to 1000 as a generous practical limit, no paging
    final int limit = 1000;
    logger.debug("Fetching first {} alerts using report with id {}", limit, reportId);

    final QueryBuilder query = QueryBuilders.termQuery(AlertType.REPORT_ID, reportId);
    final SearchResponse searchResponse = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getAlertType()))
        .setTypes(configurationService.getAlertType())
        .setQuery(query)
      .setSize(limit)
      .get();

    return ElasticsearchHelper.mapHits(searchResponse.getHits(), AlertDefinitionDto.class, objectMapper);
  }

  private void logError(String alertId) {
    logger.error("Was not able to retrieve alert with id [{}] from Elasticsearch.", alertId);
  }

}
