/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.service.es.schema.type.DashboardShareType;
import org.camunda.optimize.service.es.schema.type.ReportShareType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_SHARE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.REPORT_SHARE_TYPE;

@RequiredArgsConstructor
@Component
@Slf4j
public class SharingReader {

  private final RestHighLevelClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  private Optional<ReportShareDto> findReportShareByQuery(QueryBuilder query) {
    Optional<ReportShareDto> result = Optional.empty();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(REPORT_SHARE_TYPE))
        .types(REPORT_SHARE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Was not able to fetch report share.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getTotalHits() != 0) {
      try {
        result = Optional.of(
          objectMapper.readValue(
            searchResponse.getHits().getAt(0).getSourceAsString(),
            ReportShareDto.class
          )
        );
      } catch (IOException e) {
        log.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  public Optional<ReportShareDto> getReportShare(String shareId) {
    Optional<ReportShareDto> result = Optional.empty();
    log.debug("Fetching report share with id [{}]", shareId);
    GetRequest getRequest = new GetRequest(
      getOptimizeIndexAliasForType(REPORT_SHARE_TYPE),
      REPORT_SHARE_TYPE,
      shareId
    );

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch report share with id [%s]", shareId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      try {
        result = Optional.of(objectMapper.readValue(getResponse.getSourceAsString(), ReportShareDto.class));
      } catch (IOException e) {
        String reason = "Could deserialize report share with id [" + shareId + "] from Elasticsearch.";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  public Optional<DashboardShareDto> findDashboardShare(String shareId) {
    Optional<DashboardShareDto> result = Optional.empty();
    log.debug("Fetching dashboard share with id [{}]", shareId);
    GetRequest getRequest = new GetRequest(
      getOptimizeIndexAliasForType(DASHBOARD_SHARE_TYPE),
      DASHBOARD_SHARE_TYPE,
      shareId
    );

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch dashboard share with id [%s]", shareId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      try {
        result = Optional.of(objectMapper.readValue(getResponse.getSourceAsString(), DashboardShareDto.class));
      } catch (IOException e) {
        String reason = "Could deserialize dashboard share with id [" + shareId + "] from Elasticsearch.";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  public Optional<ReportShareDto> findShareForReport(String reportId) {
    log.debug("Fetching share for resource [{}]", reportId);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
      .must(QueryBuilders.termQuery(ReportShareType.REPORT_ID, reportId));
    return findReportShareByQuery(boolQueryBuilder);
  }

  public Optional<DashboardShareDto> findShareForDashboard(String dashboardId) {
    log.debug("Fetching share for resource [{}]", dashboardId);

    SearchResponse searchResponse = performSearchShareForDashboardIdRequest(dashboardId);
    return extractDashboardShareFromResponse(dashboardId, searchResponse);
  }

  private Optional<DashboardShareDto> extractDashboardShareFromResponse(String dashboardId,
                                                                        SearchResponse searchResponse) {
    Optional<DashboardShareDto> result = Optional.empty();
    if (searchResponse.getHits().getTotalHits() != 0) {
      String firstHitSource = searchResponse.getHits().getAt(0).getSourceAsString();
      try {
        result = Optional.of(
          objectMapper.readValue(firstHitSource, DashboardShareDto.class)
        );
      } catch (IOException e) {
        String reason = "Could deserialize dashboard share with id [" + dashboardId + "] from Elasticsearch.";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  private SearchResponse performSearchShareForDashboardIdRequest(String dashboardId) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
      .must(QueryBuilders.termQuery(DashboardShareType.DASHBOARD_ID, dashboardId));
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(boolQueryBuilder);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DASHBOARD_SHARE_TYPE))
        .types(DASHBOARD_SHARE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch share for dashboard with id [%s]", dashboardId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return searchResponse;
  }

  private Map<String, ReportShareDto> findReportSharesByQuery(QueryBuilder query) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(REPORT_SHARE_TYPE))
        .types(REPORT_SHARE_TYPE)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve report shares!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve report shares!", e);
    }

    List<ReportShareDto> reportShareDtos = ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      ReportShareDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );

    return reportShareDtos.stream().collect(Collectors.toMap(
      ReportShareDto::getReportId,
      Function.identity()
    ));
  }

  public Map<String, ReportShareDto> findShareForReports(List<String> reports) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
      .must(QueryBuilders.termsQuery(ReportShareType.REPORT_ID, reports));
    return findReportSharesByQuery(boolQueryBuilder);
  }

  public Map<String, DashboardShareDto> findShareForDashboards(List<String> dashboards) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
      .must(QueryBuilders.termsQuery(DashboardShareType.DASHBOARD_ID, dashboards));
    return findDashboardSharesByQuery(boolQueryBuilder);
  }

  private Map<String, DashboardShareDto> findDashboardSharesByQuery(BoolQueryBuilder query) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DASHBOARD_SHARE_TYPE))
        .types(DASHBOARD_SHARE_TYPE)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve dashboard shares!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve dashboard shares!", e);
    }

    List<DashboardShareDto> dashboardShareDtos = ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      DashboardShareDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );

    return dashboardShareDtos.stream().collect(Collectors.toMap(
      DashboardShareDto::getDashboardId,
      Function.identity()
    ));
  }
}
