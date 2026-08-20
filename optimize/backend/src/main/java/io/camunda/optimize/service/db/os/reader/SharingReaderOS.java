/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_SHARE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.REPORT_SHARE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.reader.SharingReader;
import io.camunda.optimize.service.db.schema.index.DashboardShareIndex;
import io.camunda.optimize.service.db.schema.index.ReportShareIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class SharingReaderOS implements SharingReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SharingReaderOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  public SharingReaderOS(
      final OptimizeOpenSearchClient osClient, final ConfigurationService configurationService) {
    this.osClient = osClient;
    this.configurationService = configurationService;
  }

  @Override
  public Optional<ReportShareRestDto> getReportShare(final String shareId) {
    LOG.debug("Fetching report share with id [{}]", shareId);
    final GetRequest.Builder getReqBuilder =
        new GetRequest.Builder().index(REPORT_SHARE_INDEX_NAME).id(shareId);

    final String errorMessage = String.format("Could not fetch report share with id [%s]", shareId);
    final GetResponse<ReportShareRestDto> getResponse =
        osClient.get(getReqBuilder, ReportShareRestDto.class, errorMessage);

    if (getResponse.found()) {
      return Optional.ofNullable(getResponse.source());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<DashboardShareRestDto> findDashboardShare(final String shareId) {
    LOG.debug("Fetching dashboard share with id [{}]", shareId);
    final GetRequest.Builder getReqBuilder =
        new GetRequest.Builder().index(DASHBOARD_SHARE_INDEX_NAME).id(shareId);

    final String errorMessage =
        String.format("Could not fetch dashboard share with id [%s]", shareId);
    final GetResponse<DashboardShareRestDto> getResponse =
        osClient.get(getReqBuilder, DashboardShareRestDto.class, errorMessage);
    if (getResponse.found()) {
      return Optional.ofNullable(getResponse.source());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<ReportShareRestDto> findShareForReport(final String reportId) {
    LOG.debug("Fetching share for resource [{}]", reportId);
    final BoolQuery.Builder boolQueryBuilder =
        new BoolQuery.Builder().must(QueryDSL.term(ReportShareIndex.REPORT_ID, reportId));
    return findReportShareByQuery(boolQueryBuilder.build());
  }

  @Override
  public Optional<DashboardShareRestDto> findShareForDashboard(final String dashboardId) {
    LOG.debug("Fetching share for resource [{}]", dashboardId);
    final SearchResponse<DashboardShareRestDto> searchResponse =
        performSearchShareForDashboardIdRequest(dashboardId);
    final List<DashboardShareRestDto> results =
        OpensearchReaderUtil.extractResponseValues(searchResponse);
    if (!results.isEmpty()) {
      return Optional.of(results.get(0));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Map<String, ReportShareRestDto> findShareForReports(final List<String> reports) {
    final BoolQuery.Builder boolQueryBuilder =
        new BoolQuery.Builder()
            .must(QueryDSL.terms(ReportShareIndex.REPORT_ID, reports, FieldValue::of));
    return findSharesByQuery(
            boolQueryBuilder.build(),
            REPORT_SHARE_INDEX_NAME,
            ReportShareRestDto.class,
            "Was not able to retrieve report shares!")
        .stream()
        .collect(Collectors.toMap(ReportShareRestDto::getReportId, Function.identity()));
  }

  @Override
  public Map<String, DashboardShareRestDto> findShareForDashboards(final List<String> dashboards) {
    final BoolQuery.Builder boolQueryBuilder =
        new BoolQuery.Builder()
            .must(QueryDSL.terms(DashboardShareIndex.DASHBOARD_ID, dashboards, FieldValue::of));
    return findSharesByQuery(
            boolQueryBuilder.build(),
            DASHBOARD_SHARE_INDEX_NAME,
            DashboardShareRestDto.class,
            "Was not able to retrieve dashboards shares!")
        .stream()
        .collect(Collectors.toMap(DashboardShareRestDto::getDashboardId, Function.identity()));
  }

  @Override
  public long getShareCount(final String indexName) {
    final String errorMessage =
        String.format("Was not able to retrieve count for type: %s", indexName);
    return osClient.count(indexName, errorMessage);
  }

  private Optional<ReportShareRestDto> findReportShareByQuery(final BoolQuery query) {

    final SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder().index(REPORT_SHARE_INDEX_NAME).size(1).query(query.toQuery());

    final String errorMessage = "Was not able to fetch report share.";
    final SearchResponse<ReportShareRestDto> searchResponse =
        osClient.search(searchReqBuilder, ReportShareRestDto.class, errorMessage);
    final List<ReportShareRestDto> results =
        OpensearchReaderUtil.extractResponseValues(searchResponse);
    if (!results.isEmpty()) {
      return Optional.of(results.get(0));
    } else {
      return Optional.empty();
    }
  }

  private SearchResponse<DashboardShareRestDto> performSearchShareForDashboardIdRequest(
      final String dashboardId) {
    final BoolQuery boolQuery =
        new BoolQuery.Builder()
            .must(QueryDSL.term(DashboardShareIndex.DASHBOARD_ID, dashboardId))
            .build();

    final SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(DASHBOARD_SHARE_INDEX_NAME)
            .size(1)
            .query(boolQuery.toQuery());

    final String errorMessage =
        String.format("Was not able to fetch share for dashboard with id [%s]", dashboardId);
    return osClient.search(searchReqBuilder, DashboardShareRestDto.class, errorMessage);
  }

  private <T> List<T> findSharesByQuery(
      final BoolQuery query,
      final String index,
      final Class<T> responseType,
      final String errorMessage) {
    final SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(index)
            .size(LIST_FETCH_LIMIT)
            .query(query.toQuery())
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    final OpenSearchDocumentOperations.AggregatedResult<Hit<T>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchReqBuilder, responseType);
    } catch (final IOException e) {
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }
}
