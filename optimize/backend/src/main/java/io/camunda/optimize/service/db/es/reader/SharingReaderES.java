/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_SHARE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.REPORT_SHARE_INDEX_NAME;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.SharingReader;
import io.camunda.optimize.service.db.schema.index.DashboardShareIndex;
import io.camunda.optimize.service.db.schema.index.ReportShareIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class SharingReaderES implements SharingReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(SharingReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public SharingReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ReportShareRestDto> getReportShare(final String shareId) {
    Optional<ReportShareRestDto> result = Optional.empty();
    log.debug("Fetching report share with id [{}]", shareId);
    final GetRequest getRequest = new GetRequest(REPORT_SHARE_INDEX_NAME).id(shareId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
      final String reason = String.format("Could not fetch report share with id [%s]", shareId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      try {
        result =
            Optional.of(
                objectMapper.readValue(getResponse.getSourceAsString(), ReportShareRestDto.class));
      } catch (final IOException e) {
        final String reason =
            "Could deserialize report share with id [" + shareId + "] from Elasticsearch.";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  @Override
  public Optional<DashboardShareRestDto> findDashboardShare(final String shareId) {
    Optional<DashboardShareRestDto> result = Optional.empty();
    log.debug("Fetching dashboard share with id [{}]", shareId);
    final GetRequest getRequest = new GetRequest(DASHBOARD_SHARE_INDEX_NAME).id(shareId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
      final String reason = String.format("Could not fetch dashboard share with id [%s]", shareId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      try {
        result =
            Optional.of(
                objectMapper.readValue(
                    getResponse.getSourceAsString(), DashboardShareRestDto.class));
      } catch (final IOException e) {
        final String reason =
            "Could deserialize dashboard share with id [" + shareId + "] from Elasticsearch.";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  @Override
  public Optional<ReportShareRestDto> findShareForReport(final String reportId) {
    log.debug("Fetching share for resource [{}]", reportId);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder =
        boolQueryBuilder.must(QueryBuilders.termQuery(ReportShareIndex.REPORT_ID, reportId));
    return findReportShareByQuery(boolQueryBuilder);
  }

  @Override
  public Optional<DashboardShareRestDto> findShareForDashboard(final String dashboardId) {
    log.debug("Fetching share for resource [{}]", dashboardId);

    final SearchResponse searchResponse = performSearchShareForDashboardIdRequest(dashboardId);
    return extractDashboardShareFromResponse(dashboardId, searchResponse);
  }

  @Override
  public Map<String, ReportShareRestDto> findShareForReports(final List<String> reports) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder =
        boolQueryBuilder.must(QueryBuilders.termsQuery(ReportShareIndex.REPORT_ID, reports));
    return findReportSharesByQuery(boolQueryBuilder);
  }

  @Override
  public Map<String, DashboardShareRestDto> findShareForDashboards(final List<String> dashboards) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder =
        boolQueryBuilder.must(
            QueryBuilders.termsQuery(DashboardShareIndex.DASHBOARD_ID, dashboards));
    return findDashboardSharesByQuery(boolQueryBuilder);
  }

  @Override
  public long getShareCount(final String indexName) {
    final CountRequest countRequest = new CountRequest(indexName);
    try {
      return esClient.count(countRequest).getCount();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          String.format("Was not able to retrieve count for type: %s", indexName), e);
    }
  }

  private Optional<ReportShareRestDto> findReportShareByQuery(final QueryBuilder query) {
    Optional<ReportShareRestDto> result = Optional.empty();

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    final SearchRequest searchRequest =
        new SearchRequest(REPORT_SHARE_INDEX_NAME).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch report share.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getTotalHits().value != 0) {
      try {
        result =
            Optional.of(
                objectMapper.readValue(
                    searchResponse.getHits().getAt(0).getSourceAsString(),
                    ReportShareRestDto.class));
      } catch (final IOException e) {
        log.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  private Optional<DashboardShareRestDto> extractDashboardShareFromResponse(
      final String dashboardId, final SearchResponse searchResponse) {
    Optional<DashboardShareRestDto> result = Optional.empty();
    if (searchResponse.getHits().getTotalHits().value != 0) {
      final String firstHitSource = searchResponse.getHits().getAt(0).getSourceAsString();
      try {
        result = Optional.of(objectMapper.readValue(firstHitSource, DashboardShareRestDto.class));
      } catch (final IOException e) {
        final String reason =
            "Could deserialize dashboard share with id [" + dashboardId + "] from Elasticsearch.";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  private SearchResponse performSearchShareForDashboardIdRequest(final String dashboardId) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder =
        boolQueryBuilder.must(
            QueryBuilders.termQuery(DashboardShareIndex.DASHBOARD_ID, dashboardId));
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(boolQueryBuilder);
    searchSourceBuilder.size(1);
    final SearchRequest searchRequest =
        new SearchRequest(DASHBOARD_SHARE_INDEX_NAME).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch share for dashboard with id [%s]", dashboardId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return searchResponse;
  }

  private Map<String, ReportShareRestDto> findReportSharesByQuery(final QueryBuilder query) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).size(LIST_FETCH_LIMIT);
    final SearchRequest searchRequest =
        new SearchRequest(REPORT_SHARE_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (final IOException e) {
      log.error("Was not able to retrieve report shares!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve report shares!", e);
    }

    final List<ReportShareRestDto> reportShareDtos =
        ElasticsearchReaderUtil.retrieveAllScrollResults(
            scrollResp,
            ReportShareRestDto.class,
            objectMapper,
            esClient,
            configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());

    return reportShareDtos.stream()
        .collect(Collectors.toMap(ReportShareRestDto::getReportId, Function.identity()));
  }

  private Map<String, DashboardShareRestDto> findDashboardSharesByQuery(
      final BoolQueryBuilder query) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).size(LIST_FETCH_LIMIT);
    final SearchRequest searchRequest =
        new SearchRequest(DASHBOARD_SHARE_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (final IOException e) {
      log.error("Was not able to retrieve dashboard shares!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve dashboard shares!", e);
    }

    final List<DashboardShareRestDto> dashboardShareDtos =
        ElasticsearchReaderUtil.retrieveAllScrollResults(
            scrollResp,
            DashboardShareRestDto.class,
            objectMapper,
            esClient,
            configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());

    return dashboardShareDtos.stream()
        .collect(Collectors.toMap(DashboardShareRestDto::getDashboardId, Function.identity()));
  }
}
