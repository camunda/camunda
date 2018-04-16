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
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
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
    List<AlertDefinitionDto> result = new ArrayList<>();
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getAlertType()))
        .setTypes(configurationService.getAlertType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(20)
        .get();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        result.add(mapHit(hit));
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return result;
  }

  private AlertDefinitionDto mapHit(SearchHit hit) {
    String content = hit.getSourceAsString();
    AlertDefinitionDto result = null;
    try {
      result = objectMapper.readValue(content, AlertDefinitionDto.class);
    } catch (IOException e) {
      logger.error("can't map data from elasticsearch to entity", e);
    }
    return result;
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

  private void logError(String alertId) {
    logger.error("Was not able to retrieve alert with id [{}] from Elasticsearch.", alertId);
  }

  public List<AlertDefinitionDto> findAlertsForReport(String reportId) {
    List<AlertDefinitionDto> result = new ArrayList<>();
    QueryBuilder query;
    query = QueryBuilders.termQuery(AlertType.REPORT_ID, reportId);

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getAlertType()))
        .setTypes(configurationService.getAlertType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(20)
        .get();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        result.add(mapHit(hit));
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return result;
  }
}
