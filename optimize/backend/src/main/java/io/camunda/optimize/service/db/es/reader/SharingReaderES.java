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

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeCountRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
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
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class SharingReaderES implements SharingReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SharingReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public SharingReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ReportShareRestDto> getReportShare(final String shareId) {
    Optional<ReportShareRestDto> result = Optional.empty();
    LOG.debug("Fetching report share with id [{}]", shareId);

    final GetResponse<ReportShareRestDto> getResponse;
    try {
      getResponse =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, REPORT_SHARE_INDEX_NAME).id(shareId)),
              ReportShareRestDto.class);
    } catch (final IOException e) {
      final String reason = String.format("Could not fetch report share with id [%s]", shareId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.found()) {
      result = Optional.of(getResponse.source());
    }
    return result;
  }

  @Override
  public Optional<DashboardShareRestDto> findDashboardShare(final String shareId) {
    Optional<DashboardShareRestDto> result = Optional.empty();
    LOG.debug("Fetching dashboard share with id [{}]", shareId);

    final GetResponse<DashboardShareRestDto> getResponse;
    try {
      getResponse =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, DASHBOARD_SHARE_INDEX_NAME).id(shareId)),
              DashboardShareRestDto.class);
    } catch (final IOException e) {
      final String reason = String.format("Could not fetch dashboard share with id [%s]", shareId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.found()) {
      result = Optional.of(getResponse.source());
    }
    return result;
  }

  @Override
  public Optional<ReportShareRestDto> findShareForReport(final String reportId) {
    LOG.debug("Fetching share for resource [{}]", reportId);
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b -> b.must(m -> m.term(t -> t.field(ReportShareIndex.REPORT_ID).value(reportId))));
    return findReportShareByQuery(builder);
  }

  @Override
  public Optional<DashboardShareRestDto> findShareForDashboard(final String dashboardId) {
    LOG.debug("Fetching share for resource [{}]", dashboardId);

    final SearchResponse<DashboardShareRestDto> searchResponse =
        performSearchShareForDashboardIdRequest(dashboardId, DashboardShareRestDto.class);
    return extractDashboardShareFromResponse(dashboardId, searchResponse);
  }

  @Override
  public Map<String, ReportShareRestDto> findShareForReports(final List<String> reports) {

    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b ->
            b.must(
                m ->
                    m.terms(
                        t ->
                            t.field(ReportShareIndex.REPORT_ID)
                                .terms(
                                    tt ->
                                        tt.value(reports.stream().map(FieldValue::of).toList())))));
    return findReportSharesByQuery(builder);
  }

  @Override
  public Map<String, DashboardShareRestDto> findShareForDashboards(final List<String> dashboards) {
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b ->
            b.must(
                m ->
                    m.terms(
                        t ->
                            t.field(DashboardShareIndex.DASHBOARD_ID)
                                .terms(
                                    tt ->
                                        tt.value(
                                            dashboards.stream().map(FieldValue::of).toList())))));
    return findDashboardSharesByQuery(builder);
  }

  private Optional<ReportShareRestDto> findReportShareByQuery(final Query.Builder query) {
    Optional<ReportShareRestDto> result = Optional.empty();

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s -> s.optimizeIndex(esClient, REPORT_SHARE_INDEX_NAME).query(query.build()).size(1));

    final SearchResponse<ReportShareRestDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, ReportShareRestDto.class);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch report share.";
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.hits().total().value() != 0) {
      result = Optional.of(searchResponse.hits().hits().get(0).source());
    }
    return result;
  }

  @Override
  public long getShareCount(final String indexName) {
    try {
      return esClient
          .count(OptimizeCountRequestBuilderES.of(c -> c.optimizeIndex(esClient, indexName)))
          .count();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          String.format("Was not able to retrieve count for type: %s", indexName), e);
    }
  }

  private Optional<DashboardShareRestDto> extractDashboardShareFromResponse(
      final String dashboardId, final SearchResponse<DashboardShareRestDto> searchResponse) {
    Optional<DashboardShareRestDto> result = Optional.empty();
    if (searchResponse.hits().total().value() != 0) {
      result = Optional.of(searchResponse.hits().hits().get(0).source());
    }
    return result;
  }

  private <T> SearchResponse<T> performSearchShareForDashboardIdRequest(
      final String dashboardId, final Class<T> clas) {
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, DASHBOARD_SHARE_INDEX_NAME)
                    .query(
                        m ->
                            m.bool(
                                b ->
                                    b.must(
                                        r ->
                                            r.term(
                                                t ->
                                                    t.field(DashboardShareIndex.DASHBOARD_ID)
                                                        .value(dashboardId)))))
                    .size(1));

    final SearchResponse<T> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, clas);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch share for dashboard with id [%s]", dashboardId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return searchResponse;
  }

  private Map<String, ReportShareRestDto> findReportSharesByQuery(final Query.Builder query) {
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, REPORT_SHARE_INDEX_NAME)
                    .query(query.build())
                    .size(LIST_FETCH_LIMIT)
                    .scroll(
                        s1 ->
                            s1.time(
                                configurationService
                                        .getElasticSearchConfiguration()
                                        .getScrollTimeoutInSeconds()
                                    + "s")));

    final SearchResponse<ReportShareRestDto> scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, ReportShareRestDto.class);
    } catch (final IOException e) {
      LOG.error("Was not able to retrieve report shares!", e);
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

  private Map<String, DashboardShareRestDto> findDashboardSharesByQuery(final Query.Builder query) {
    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, DASHBOARD_SHARE_INDEX_NAME)
                    .query(query.build())
                    .size(LIST_FETCH_LIMIT)
                    .scroll(
                        s1 ->
                            s1.time(
                                configurationService
                                        .getElasticSearchConfiguration()
                                        .getScrollTimeoutInSeconds()
                                    + "s")));

    final SearchResponse<DashboardShareRestDto> scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, DashboardShareRestDto.class);
    } catch (final IOException e) {
      LOG.error("Was not able to retrieve dashboard shares!", e);
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
