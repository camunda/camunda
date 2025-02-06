/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.DATA;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORTS;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORT_ITEM_ID;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.INSTANT_PREVIEW_REPORT;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.get.GetResult;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeCountRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeMultiGetOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ReportReaderES implements ReportReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ReportReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public ReportReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ReportDefinitionDto> getReport(final String reportId) {
    LOG.debug("Fetching report with id [{}]", reportId);
    final MgetResponse<ReportDefinitionDto> multiGetItemResponses =
        performMultiGetReportRequest(reportId, ReportDefinitionDto.class);

    Optional<ReportDefinitionDto> result = Optional.empty();
    for (final MultiGetResponseItem<ReportDefinitionDto> itemResponse :
        multiGetItemResponses.docs()) {
      final GetResult<ReportDefinitionDto> response = itemResponse.result();
      final Optional<ReportDefinitionDto> reportDefinitionDto =
          processGetReportResponse(reportId, response);
      if (reportDefinitionDto.isPresent()) {
        result = reportDefinitionDto;
        break;
      }
    }
    return result;
  }

  @Override
  public Optional<SingleProcessReportDefinitionRequestDto> getSingleProcessReportOmitXml(
      final String reportId) {
    LOG.debug("Fetching single process report with id [{}]", reportId);
    final GetRequest getRequest = getGetRequestOmitXml(SINGLE_PROCESS_REPORT_INDEX_NAME, reportId);
    final GetResponse<SingleProcessReportDefinitionRequestDto> getResponse;
    try {
      getResponse = esClient.get(getRequest, SingleProcessReportDefinitionRequestDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format("Could not fetch single process report with id [%s]", reportId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.found()) {
      return Optional.empty();
    }

    return Optional.of(getResponse.source());
  }

  @Override
  public Optional<SingleDecisionReportDefinitionRequestDto> getSingleDecisionReportOmitXml(
      final String reportId) {
    LOG.debug("Fetching single decision report with id [{}]", reportId);
    final GetRequest getRequest = getGetRequestOmitXml(SINGLE_DECISION_REPORT_INDEX_NAME, reportId);

    final GetResponse<SingleDecisionReportDefinitionRequestDto> getResponse;
    try {
      getResponse = esClient.get(getRequest, SingleDecisionReportDefinitionRequestDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format("Could not fetch single decision report with id [%s]", reportId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.found()) {
      return Optional.empty();
    }

    return Optional.of(getResponse.source());
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForIdsOmitXml(final List<String> reportIds) {
    if (reportIds.isEmpty()) {
      LOG.debug("No report IDs supplied so no reports to fetch");
      return Collections.emptyList();
    }
    LOG.debug("Fetching all report definitions for Ids {}", reportIds);
    final Query.Builder qb = new Query.Builder();
    qb.ids(i -> i.values(reportIds));

    final SearchResponse<ReportDefinitionDto> searchResponse =
        performGetReportRequestOmitXml(
            qb, ALL_REPORT_INDICES, LIST_FETCH_LIMIT, ReportDefinitionDto.class);
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        ReportDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(
      final String definitionKey) {
    final List<ReportDefinitionDto> processReportsForKey =
        getAllProcessReportsForDefinitionKeyOmitXml(definitionKey);
    final List<String> processReportIds =
        processReportsForKey.stream().map(ReportDefinitionDto::getId).collect(Collectors.toList());
    final List<CombinedReportDefinitionRequestDto> combinedReports =
        getCombinedReportsForSimpleReports(processReportIds);
    processReportsForKey.addAll(combinedReports);
    return processReportsForKey;
  }

  @Override
  public List<ReportDefinitionDto> getAllPrivateReportsOmitXml() {
    LOG.debug("Fetching all available private reports");

    final Query.Builder qb = new Query.Builder();
    qb.bool(
        b ->
            b.mustNot(m -> m.term(t -> t.field(DATA + "." + MANAGEMENT_REPORT).value(true)))
                .mustNot(m -> m.term(t -> t.field(DATA + "." + INSTANT_PREVIEW_REPORT).value(true)))
                .mustNot(m -> m.exists(e -> e.field(COLLECTION_ID))));
    final SearchResponse<ReportDefinitionDto> searchResponse =
        performGetReportRequestOmitXml(
            qb, ALL_REPORT_INDICES, LIST_FETCH_LIMIT, ReportDefinitionDto.class);
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        ReportDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public List<SingleProcessReportDefinitionRequestDto> getAllSingleProcessReportsForIdsOmitXml(
      final List<String> reportIds) {
    LOG.debug("Fetching all available single process reports for IDs [{}]", reportIds);
    final Class<SingleProcessReportDefinitionRequestDto> reportType =
        SingleProcessReportDefinitionRequestDto.class;
    final String[] indices = new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME};
    return getReportDefinitionDtos(reportIds, reportType, indices);
  }

  @Override
  public List<ReportDefinitionDto> getReportsForCollectionOmitXml(final String collectionId) {
    return getReportsForCollection(collectionId, false);
  }

  @Override
  public List<ReportDefinitionDto> getReportsForCollectionIncludingXml(final String collectionId) {
    return getReportsForCollection(collectionId, true);
  }

  @Override
  public List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReport(
      final String simpleReportId) {
    return getCombinedReportsForSimpleReports(Collections.singletonList(simpleReportId));
  }

  @Override
  public long getReportCount(final ReportType reportType) {
    final CountRequest countRequest;
    if (ReportType.PROCESS.equals(reportType)) {
      countRequest =
          OptimizeCountRequestBuilderES.of(
              r ->
                  r.optimizeIndex(esClient, SINGLE_PROCESS_REPORT_INDEX_NAME)
                      .query(
                          q ->
                              q.bool(
                                  b ->
                                      b.mustNot(
                                              m ->
                                                  m.term(
                                                      t ->
                                                          t.field(DATA + "." + MANAGEMENT_REPORT)
                                                              .value(true)))
                                          .mustNot(
                                              m ->
                                                  m.term(
                                                      t ->
                                                          t.field(
                                                                  DATA
                                                                      + "."
                                                                      + INSTANT_PREVIEW_REPORT)
                                                              .value(true))))));
    } else {
      countRequest =
          OptimizeCountRequestBuilderES.of(
              f -> f.optimizeIndex(esClient, SINGLE_DECISION_REPORT_INDEX_NAME));
    }
    try {
      return esClient.count(countRequest).count();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve report counts!", e);
    }
  }

  @Override
  public long getUserTaskReportCount() {
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b ->
            b.mustNot(m -> m.term(t -> t.field(DATA + "." + MANAGEMENT_REPORT).value(true)))
                .mustNot(
                    m -> m.term(t -> t.field(DATA + "." + INSTANT_PREVIEW_REPORT).value(true))));
    final SearchRequest searchRequest =
        getSearchRequestOmitXml(builder, new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME}).build();
    final SearchResponse<SingleProcessReportDefinitionRequestDto> searchResponse;
    try {
      searchResponse =
          esClient.search(searchRequest, SingleProcessReportDefinitionRequestDto.class);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch process reports to count userTask reports.";
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    final List<SingleProcessReportDefinitionRequestDto> allProcessReports =
        ElasticsearchReaderUtil.mapHits(
            searchResponse.hits(), SingleProcessReportDefinitionRequestDto.class, objectMapper);
    return allProcessReports.stream().filter(report -> report.getData().isUserTaskReport()).count();
  }

  private List<ReportDefinitionDto> getAllProcessReportsForDefinitionKeyOmitXml(
      final String definitionKey) {
    LOG.debug(
        "Fetching all available process reports for process definition key {}", definitionKey);

    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b ->
            b.must(
                m ->
                    m.term(
                        t ->
                            t.field(
                                    String.join(
                                        ".",
                                        DATA,
                                        SingleReportDataDto.Fields.definitions,
                                        ReportDataDefinitionDto.Fields.key))
                                .value(definitionKey))));
    final SearchResponse<ReportDefinitionDto> searchResponse =
        performGetReportRequestOmitXml(
            builder,
            new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME},
            LIST_FETCH_LIMIT,
            ReportDefinitionDto.class);
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        ReportDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  private List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReports(
      final List<String> simpleReportIds) {
    LOG.debug("Fetching first combined reports using simpleReports with ids {}", simpleReportIds);

    final Query.Builder builder = new Query.Builder();
    builder.nested(
        n ->
            n.path(DATA)
                .query(
                    q ->
                        q.nested(
                            nn ->
                                nn.query(
                                        qq ->
                                            qq.terms(
                                                t ->
                                                    t.field(
                                                            String.join(
                                                                ".", DATA, REPORTS, REPORT_ITEM_ID))
                                                        .terms(
                                                            tt ->
                                                                tt.value(
                                                                    simpleReportIds.stream()
                                                                        .map(FieldValue::of)
                                                                        .toList()))))
                                    .path(String.join(".", DATA, REPORTS))
                                    .scoreMode(ChildScoreMode.None)))
                .scoreMode(ChildScoreMode.None));
    final SearchRequest searchRequest =
        getSearchRequestOmitXml(builder, new String[] {COMBINED_REPORT_INDEX_NAME}).build();

    final SearchResponse<CombinedReportDefinitionRequestDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, CombinedReportDefinitionRequestDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch combined reports that contain reports with ids [%s]",
              simpleReportIds);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), CombinedReportDefinitionRequestDto.class, objectMapper);
  }

  private List<ReportDefinitionDto> getReportsForCollection(
      final String collectionId, final boolean includeXml) {
    LOG.debug("Fetching reports using collection with id {}", collectionId);

    final Query.Builder qb = new Query.Builder();
    qb.bool(
        b ->
            b.must(m -> m.term(t -> t.field(COLLECTION_ID).value(collectionId)))
                .mustNot(m -> m.term(t -> t.field(DATA + "." + MANAGEMENT_REPORT).value(true)))
                .mustNot(
                    m -> m.term(t -> t.field(DATA + "." + INSTANT_PREVIEW_REPORT).value(true))));
    final SearchRequest searchRequest;
    final String[] indices = {
      COMBINED_REPORT_INDEX_NAME,
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SINGLE_DECISION_REPORT_INDEX_NAME
    };
    if (includeXml) {
      searchRequest = getSearchRequestIncludingXml(qb, indices).build();
    } else {
      searchRequest = getSearchRequestOmitXml(qb, indices).build();
    }

    final SearchResponse<ReportDefinitionDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, ReportDefinitionDto.class);
      return ElasticsearchReaderUtil.mapHits(
          searchResponse.hits(), ReportDefinitionDto.class, objectMapper);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch reports for collection with id [%s]", collectionId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private <T extends ReportDefinitionDto> List<T> getReportDefinitionDtos(
      final List<String> reportIds, final Class<T> reportType, final String[] indices) {
    if (reportIds.isEmpty()) {
      return Collections.emptyList();
    }
    final Query.Builder builder = new Query.Builder();
    builder.ids(i -> i.values(reportIds));
    final SearchResponse<T> searchResponse =
        performGetReportRequestOmitXml(builder, indices, reportIds.size(), reportType);

    return mapResponseToReportList(searchResponse, reportType).stream()
        // make sure that the order of the reports corresponds to the one from the single report ids
        // list
        .sorted(Comparator.comparingInt(a -> reportIds.indexOf(a.getId())))
        .collect(Collectors.toList());
  }

  private <T extends ReportDefinitionDto> List<T> mapResponseToReportList(
      final SearchResponse<T> searchResponse, final Class<T> c) {
    final List<T> reportDefinitionDtos = new ArrayList<>();
    for (final Hit<T> hit : searchResponse.hits().hits()) {
      reportDefinitionDtos.add(hit.source());
    }
    return reportDefinitionDtos;
  }

  private Optional<ReportDefinitionDto> processGetReportResponse(
      final String reportId, final GetResult<ReportDefinitionDto> getResponse) {
    final Optional<ReportDefinitionDto> result = Optional.empty();
    if (getResponse != null && getResponse.found()) {
      return Optional.of(getResponse.source());
    }
    return result;
  }

  private GetRequest getGetRequestOmitXml(final String index, final String reportId) {
    return OptimizeGetRequestBuilderES.of(
        g ->
            g.optimizeIndex(esClient, index)
                .id(reportId)
                .sourceExcludes(List.of(REPORT_LIST_EXCLUDES))
                .source(s -> s.fetch(true)));
  }

  private SearchRequest.Builder getSearchRequestOmitXml(
      final Query.Builder query, final String[] indices) {
    return getSearchRequestOmitXml(query, indices, LIST_FETCH_LIMIT);
  }

  private SearchRequest.Builder getSearchRequestIncludingXml(
      final Query.Builder query, final String[] indices) {
    return getSearchRequest(query, indices, LIST_FETCH_LIMIT, new String[] {});
  }

  private SearchRequest.Builder getSearchRequestOmitXml(
      final Query.Builder query, final String[] indices, final int size) {
    return getSearchRequest(query, indices, size, REPORT_LIST_EXCLUDES);
  }

  private SearchRequest.Builder getSearchRequest(
      final Query.Builder query,
      final String[] indices,
      final int size,
      final String[] excludeFields) {
    final OptimizeSearchRequestBuilderES builder = new OptimizeSearchRequestBuilderES();
    builder.optimizeIndex(esClient, indices);
    builder.query(query.build());
    builder.size(size);
    if (excludeFields.length == 0) {
      builder.source(s -> s.fetch(true));
    } else {
      builder.source(s -> s.filter(f -> f.excludes(List.of(excludeFields))));
    }
    return builder;
  }

  private <T> SearchResponse<T> performGetReportRequestOmitXml(
      final Query.Builder query, final String[] indices, final int size, final Class<T> clas) {
    final SearchRequest.Builder searchRequestBuilder =
        getSearchRequestOmitXml(query, indices, size)
            .scroll(
                s ->
                    s.time(
                        configurationService
                                .getElasticSearchConfiguration()
                                .getScrollTimeoutInSeconds()
                            + "s"));

    try {
      return esClient.search(searchRequestBuilder.build(), clas);
    } catch (final IOException e) {
      LOG.error("Was not able to retrieve reports!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve reports!", e);
    }
  }

  private <T> MgetResponse<T> performMultiGetReportRequest(
      final String reportId, final Class<T> clas) {
    final MgetRequest.Builder request = new MgetRequest.Builder();
    request.docs(
        MultiGetOperation.of(
            m ->
                new OptimizeMultiGetOperationBuilderES()
                    .optimizeIndex(esClient, SINGLE_PROCESS_REPORT_INDEX_NAME)
                    .id(reportId)),
        MultiGetOperation.of(
            m ->
                new OptimizeMultiGetOperationBuilderES()
                    .optimizeIndex(esClient, SINGLE_DECISION_REPORT_INDEX_NAME)
                    .id(reportId)),
        MultiGetOperation.of(
            m ->
                new OptimizeMultiGetOperationBuilderES()
                    .optimizeIndex(esClient, COMBINED_REPORT_INDEX_NAME)
                    .id(reportId)));

    final MgetResponse<T> multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request.build(), clas);
    } catch (final IOException e) {
      final String reason = String.format("Could not fetch report with id [%s]", reportId);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }
}
