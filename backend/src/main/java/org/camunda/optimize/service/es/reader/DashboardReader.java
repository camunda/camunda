package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
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

import javax.ws.rs.NotFoundException;
import java.io.IOException;
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
      throw new NotFoundException("Dashboard does not exist! Tried to retried dashboard with id " + dashboardId);
    }
  }

  public List<DashboardDefinitionDto> findFirstDashboardsForReport(String reportId) {
    // Note: this is capped to 1000 as a generous practical limit, no paging
    final int limit = 1000;
    logger.debug("Fetching first {} dashboards using report with id {}", limit, reportId);

    final QueryBuilder getCombinedReportsBySimpleReportIdQuery = QueryBuilders.boolQuery()
      .filter(QueryBuilders.nestedQuery(
        "reports",
        QueryBuilders.termQuery("reports.id", reportId),
        ScoreMode.None
      ));

    SearchResponse searchResponse = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getDashboardType()))
      .setTypes(configurationService.getDashboardType())
      .setQuery(getCombinedReportsBySimpleReportIdQuery)
      .setSize(limit)
      .get();

    return ElasticsearchHelper.mapHits(searchResponse.getHits(), DashboardDefinitionDto.class, objectMapper);
  }

  public List<DashboardDefinitionDto> getAllDashboards() {
    logger.debug("Fetching all available dashboards");
    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getDashboardType()))
      .setTypes(configurationService.getDashboardType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(100)
      .get();

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      DashboardDefinitionDto.class,
      objectMapper,
      esclient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

}
