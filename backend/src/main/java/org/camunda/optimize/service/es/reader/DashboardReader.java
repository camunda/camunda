package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
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

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;

@Component
public class DashboardReader {
  private static final Logger logger = LoggerFactory.getLogger(DashboardReader.class);

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
        configurationService.getOptimizeIndex(ElasticsearchConstants.DASHBOARD_TYPE),
        ElasticsearchConstants.DASHBOARD_TYPE,
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
    logger.debug("Fetching dashboards using report with id {}", reportId);

    final QueryBuilder getCombinedReportsBySimpleReportIdQuery = QueryBuilders.boolQuery()
      .filter(QueryBuilders.nestedQuery(
        "reports",
        QueryBuilders.termQuery("reports.id", reportId),
        ScoreMode.None
      ));

    SearchResponse searchResponse = esclient
      .prepareSearch(configurationService.getOptimizeIndex(ElasticsearchConstants.DASHBOARD_TYPE))
      .setTypes(ElasticsearchConstants.DASHBOARD_TYPE)
      .setQuery(getCombinedReportsBySimpleReportIdQuery)
      .setSize(LIST_FETCH_LIMIT)
      .get();

    return ElasticsearchHelper.mapHits(searchResponse.getHits(), DashboardDefinitionDto.class, objectMapper);
  }

  public List<DashboardDefinitionDto> getAllDashboards() {
    logger.debug("Fetching all available dashboards");
    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex(ElasticsearchConstants.DASHBOARD_TYPE))
      .setTypes(ElasticsearchConstants.DASHBOARD_TYPE)
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(QueryBuilders.matchAllQuery())
      .setSize(LIST_FETCH_LIMIT)
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
