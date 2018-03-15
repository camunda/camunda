package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DashboardReader {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public DashboardDefinitionDto getDashboard(String dashboardId) {
    logger.debug("Fetching dashboard with id [{}]", dashboardId);
    GetResponse getResponse = esclient
      .prepareGet(
        configurationService.getOptimizeIndex(configurationService.getDashboardType()),
        configurationService.getDashboardType(),
        dashboardId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, DashboardDefinitionDto.class);
      } catch (IOException e) {
        String reason = "Could not deserialize dashboard information for dashboard " + dashboardId;
        logger.error("Was not able to retrieve dashboard with id [{}] from Elasticsearch. Reason: reason");
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      logger.error("Was not able to retrieve dashboard with id [{}] from Elasticsearch.", dashboardId);
      throw new OptimizeRuntimeException("Dashboard does not exist! Tried to retried dashboard with id " + dashboardId);
    }
  }

  public List<DashboardDefinitionDto> getAllDashboards() throws IOException {
    logger.debug("Fetching all available dashboards");
    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getDashboardType()))
      .setTypes(configurationService.getDashboardType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(100)
      .get();
    List<DashboardDefinitionDto> storedDashboards = new ArrayList<>();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        String responseAsString = hit.getSourceAsString();
        storedDashboards.add(objectMapper.readValue(responseAsString, DashboardDefinitionDto.class));
      }

      scrollResp = esclient
        .prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return storedDashboards;
  }

}
