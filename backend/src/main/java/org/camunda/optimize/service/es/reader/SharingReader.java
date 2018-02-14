package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.camunda.optimize.service.es.schema.type.DashboardShareType;
import org.camunda.optimize.service.es.schema.type.ReportShareType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
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
import java.util.Optional;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
@Component
public class SharingReader {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  private Optional<ReportShareDto> findReportShareByQuery(QueryBuilder query) {
    Optional<ReportShareDto> result = Optional.empty();

    SearchResponse scrollResp = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getReportShareType()))
      .setTypes(configurationService.getReportShareType())
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .setSize(20)
      .get();

    if (scrollResp.getHits().getTotalHits() != 0) {
      try {
        result = Optional.of(
          objectMapper.readValue(
            scrollResp.getHits().getAt(0).getSourceAsString(),
              ReportShareDto.class
          )
        );
      } catch (IOException e) {
        logger.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  public Optional<ReportShareDto> findReportShare(String shareId) {
    Optional<ReportShareDto> result = Optional.empty();
    logger.debug("Fetching share with id [{}]", shareId);
    GetResponse getResponse = esclient
      .prepareGet(
          configurationService.getOptimizeIndex(configurationService.getReportShareType()),
          configurationService.getReportShareType(),
          shareId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      try {
        result = Optional.of(objectMapper.readValue(getResponse.getSourceAsString(), ReportShareDto.class));
      } catch (IOException e) {
        logger.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  public Optional<DashboardShareDto> findDashboardShare(String shareId) {
    Optional<DashboardShareDto> result = Optional.empty();
    logger.debug("Fetching share with id [{}]", shareId);
    GetResponse getResponse = esclient
      .prepareGet(
          configurationService.getOptimizeIndex(configurationService.getDashboardShareType()),
          configurationService.getDashboardShareType(),
          shareId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      try {
        result = Optional.of(objectMapper.readValue(getResponse.getSourceAsString(), DashboardShareDto.class));
      } catch (IOException e) {
        logger.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  public Optional<ReportShareDto> findShareForReport(String reportId, SharedResourceType type) {
    logger.debug("Fetching share for resource [{}]", reportId);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
        .must(QueryBuilders.termQuery(ReportShareType.REPORT_ID, reportId))
        .must(QueryBuilders.termQuery(ReportShareType.TYPE, type.toString()));
    return findReportShareByQuery(boolQueryBuilder);
  }

  public Optional<DashboardShareDto> findShareForDashboard(String dashboardId, SharedResourceType type) {
    logger.debug("Fetching share for resource [{}]", dashboardId);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
        .must(QueryBuilders.termQuery(DashboardShareType.DASHBOARD_ID, dashboardId))
        .must(QueryBuilders.termQuery(DashboardShareType.TYPE, type.toString()));
    return findDashboardShareByQuery(boolQueryBuilder);
  }

  private Optional<DashboardShareDto> findDashboardShareByQuery(BoolQueryBuilder query) {
    Optional<DashboardShareDto> result = Optional.empty();

    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getDashboardShareType()))
        .setTypes(configurationService.getDashboardShareType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(20)
        .get();

    if (scrollResp.getHits().getTotalHits() != 0) {
      String firstHitSource = scrollResp.getHits().getAt(0).getSourceAsString();
      try {
        result = Optional.of(
            objectMapper.readValue(firstHitSource, DashboardShareDto.class)
        );
      } catch (IOException e) {
        logger.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  public List<ReportShareDto> findReportSharesForResources(Set<String> resourceIds, SharedResourceType sharedResourceType) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder
        .must(QueryBuilders.termsQuery(ReportShareType.REPORT_ID, resourceIds))
        .must(QueryBuilders.termQuery(ReportShareType.TYPE, sharedResourceType.toString()));
    QueryBuilder query = boolQueryBuilder;
    return findReportSharesByQuery(query);
  }

  private List<ReportShareDto> findReportSharesByQuery(QueryBuilder query) {
    List<ReportShareDto> result = new ArrayList<>();
    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(configurationService.getReportShareType()))
        .setTypes(configurationService.getReportShareType())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(20)
        .get();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        try {
          result.add(objectMapper.readValue(hit.getSourceAsString(), ReportShareDto.class));
        } catch (IOException e) {
          logger.error("cant't map sharing hit", e);
        }
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);
    return result;
  }
}
