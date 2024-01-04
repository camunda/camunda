/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.reader.ReportReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.get.GetResult;
import org.opensearch.client.opensearch.core.mget.MultiGetResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.core.search.SourceFilter;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.DATA;
import static org.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORTS;
import static org.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORT_ITEM_ID;
import static org.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.INSTANT_PREVIEW_REPORT;
import static org.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ReportReaderOS implements ReportReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Optional<ReportDefinitionDto> getReport(String reportId) {
    log.debug("Fetching report with id [{}]", reportId);
    MgetResponse<ReportDefinitionDto> multiGetItemResponses = performMultiGetReportRequest(reportId);

    Optional<ReportDefinitionDto> result = Optional.empty();

    for (MultiGetResponseItem<ReportDefinitionDto> itemResponse : multiGetItemResponses.docs()) {
      GetResult<ReportDefinitionDto> response = itemResponse.result();
      Optional<ReportDefinitionDto> reportDefinitionDto = processGetReportResponse(response);
      if (reportDefinitionDto.isPresent()) {
        result = reportDefinitionDto;
        break;
      }
    }
    return result;
  }

  @Override
  public Optional<SingleProcessReportDefinitionRequestDto> getSingleProcessReportOmitXml(final String reportId) {
    log.debug("Fetching single process report with id [{}]", reportId);
    GetRequest.Builder getRequest = getGetRequestOmitXml(SINGLE_PROCESS_REPORT_INDEX_NAME, reportId);

    String errorMessage = String.format("Could not fetch single process report with id [%s]", reportId);

    GetResponse<SingleProcessReportDefinitionRequestDto> getResponse =
      osClient.get(getRequest, SingleProcessReportDefinitionRequestDto.class, errorMessage);

    if (!getResponse.found()) {
      return Optional.empty();
    }

    return Optional.ofNullable(getResponse.source());
  }

  @Override
  public Optional<SingleDecisionReportDefinitionRequestDto> getSingleDecisionReportOmitXml(final String reportId) {
    log.debug("Fetching single decision report with id [{}]", reportId);
    GetRequest.Builder getRequest = getGetRequestOmitXml(SINGLE_DECISION_REPORT_INDEX_NAME, reportId);

    String errorMessage = String.format("Could not fetch single decision report with id [%s]", reportId);

    GetResponse<SingleDecisionReportDefinitionRequestDto> getResponse =
      osClient.get(getRequest, SingleDecisionReportDefinitionRequestDto.class, errorMessage);

    if (!getResponse.found()) {
      return Optional.empty();
    }

    return Optional.ofNullable(getResponse.source());
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForIdsOmitXml(final List<String> reportIds) {
    if (reportIds.isEmpty()) {
      log.debug("No report IDs supplied so no reports to fetch");
      return Collections.emptyList();
    }
    log.debug("Fetching all report definitions for Ids {}", reportIds);
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    Query reportIdsQuery = QueryDSL.ids(reportIdsAsArray);

    SearchRequest.Builder searchRequest = getSearchRequestOmitXml(
      reportIdsQuery,
      ALL_REPORT_INDICES,
      LIST_FETCH_LIMIT
    ).scroll(new Time.Builder()
               .time(String.valueOf(configurationService.getOpenSearchConfiguration()
                                      .getScrollTimeoutInSeconds())).build());

    OpenSearchDocumentOperations.AggregatedResult<Hit<ReportDefinitionDto>> searchResponse;
    try {
      searchResponse = osClient.scrollOs(searchRequest, ReportDefinitionDto.class);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch all reports definitions for ids [%s]", reportIds);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return searchResponse.values().stream().map(Hit::source).toList();
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(final String definitionKey) {
    final List<ReportDefinitionDto> processReportsForKey = getAllProcessReportsForDefinitionKeyOmitXml(definitionKey);
    final List<String> processReportIds = processReportsForKey.stream()
      .map(ReportDefinitionDto::getId)
      .toList();
    final List<CombinedReportDefinitionRequestDto> combinedReports =
      getCombinedReportsForSimpleReports(processReportIds);
    processReportsForKey.addAll(combinedReports);
    return processReportsForKey;
  }

  @Override
  public List<ReportDefinitionDto> getAllPrivateReportsOmitXml() {
    log.debug("Fetching all available private reports");

    Query privateReportsQuery =
      new BoolQuery.Builder()
        .filter(QueryDSL.exists(COLLECTION_ID))
        .mustNot(QueryDSL.term(DATA + "." + MANAGEMENT_REPORT, true))
        .mustNot(QueryDSL.term(DATA + "." + INSTANT_PREVIEW_REPORT, true))
        .build()
        ._toQuery();

    SearchRequest.Builder searchRequest = getSearchRequestOmitXml(
      privateReportsQuery,
      ALL_REPORT_INDICES,
      LIST_FETCH_LIMIT
    ).scroll(new Time.Builder()
               .time(String.valueOf(configurationService.getOpenSearchConfiguration()
                                      .getScrollTimeoutInSeconds())).build());

    OpenSearchDocumentOperations.AggregatedResult<Hit<ReportDefinitionDto>> searchResponse;
    try {
      searchResponse = osClient.scrollOs(searchRequest, ReportDefinitionDto.class);
    } catch (IOException e) {
      String reason = "Was not able to fetch all private reports";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return searchResponse.values().stream().map(Hit::source).toList();
  }

  @Override
  public List<SingleProcessReportDefinitionRequestDto> getAllSingleProcessReportsForIdsOmitXml(final List<String> reportIds) {
    log.debug("Fetching all available single process reports for IDs [{}]", reportIds);
    final String[] indices = new String[]{SINGLE_PROCESS_REPORT_INDEX_NAME};
    return getReportDefinitionDtos(reportIds, indices);
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
  public List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReport(String simpleReportId) {
    return getCombinedReportsForSimpleReports(Collections.singletonList(simpleReportId));
  }

  @Override
  public long getReportCount(final ReportType reportType) {
    final CountRequest.Builder countRequest;
    if (ReportType.PROCESS.equals(reportType)) {

      Query query = new BoolQuery.Builder()
        .mustNot(QueryDSL.term(DATA + "." + MANAGEMENT_REPORT, true))
        .mustNot(QueryDSL.term(DATA + "." + INSTANT_PREVIEW_REPORT, true))
        .build()._toQuery();

      countRequest = new CountRequest.Builder()
        .index(List.of(SINGLE_PROCESS_REPORT_INDEX_NAME))
        .query(query);
    } else {
      countRequest = new CountRequest.Builder()
        .index(SINGLE_DECISION_REPORT_INDEX_NAME);
    }
    String reason = "Was not able to retrieve report counts!";
    return osClient.countOs(countRequest, reason).count();
  }

  private List<ReportDefinitionDto> getAllProcessReportsForDefinitionKeyOmitXml(final String definitionKey) {
    log.debug("Fetching all available process reports for process definition key {}", definitionKey);

    final Query processReportQuery = new BoolQuery.Builder()
      .must(QueryDSL.term(
        String.join(".", DATA, SingleReportDataDto.Fields.definitions, ReportDataDefinitionDto.Fields.key),
        definitionKey
      ))
      .build()
      ._toQuery();

    SearchRequest.Builder searchRequest = getSearchRequestOmitXml(
      processReportQuery,
      new String[]{SINGLE_PROCESS_REPORT_INDEX_NAME},
      LIST_FETCH_LIMIT
    )
      .scroll(new Time.Builder()
                .time(String.valueOf(configurationService.getOpenSearchConfiguration().getScrollTimeoutInSeconds()))
                .build());

    OpenSearchDocumentOperations.AggregatedResult<Hit<ReportDefinitionDto>> searchResponse;
    try {
      searchResponse = osClient.scrollOs(searchRequest, ReportDefinitionDto.class);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch all process reports for definition key [%s]",
        definitionKey
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return searchResponse.values().stream().map(Hit::source).toList();
  }

  private List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReports(List<String> simpleReportIds) {
    log.debug("Fetching first combined reports using simpleReports with ids {}", simpleReportIds);

    final Query inner = new NestedQuery.Builder().
      path(String.join(".", DATA, REPORTS))
      .query(QueryDSL.terms(String.join(".", DATA, REPORTS, REPORT_ITEM_ID), simpleReportIds, FieldValue::of))
      .scoreMode(ChildScoreMode.None)
      .build()._toQuery();

    final Query getCombinedReportsBySimpleReportIdQuery = new NestedQuery.Builder().
      path(DATA)
      .query(inner)
      .scoreMode(ChildScoreMode.None)
      .build()._toQuery();

    SearchRequest.Builder searchRequest = getSearchRequestOmitXml(
      getCombinedReportsBySimpleReportIdQuery,
      new String[]{COMBINED_REPORT_INDEX_NAME}
    );

    SearchResponse<CombinedReportDefinitionRequestDto> searchResponse;
    try {
      searchResponse = osClient.search(searchRequest, CombinedReportDefinitionRequestDto.class);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch combined reports that contain reports with ids [%s]",
        simpleReportIds
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  private List<ReportDefinitionDto> getReportsForCollection(final String collectionId, final boolean includeXml) {
    log.debug("Fetching reports using collection with id {}", collectionId);

    Query reportByCollectionQuery =
      new BoolQuery.Builder()
        .must(QueryDSL.term(COLLECTION_ID, collectionId))
        .mustNot(QueryDSL.term(DATA + "." + MANAGEMENT_REPORT, true))
        .mustNot(QueryDSL.term(DATA + "." + INSTANT_PREVIEW_REPORT, true))
        .build()
        ._toQuery();

    SearchRequest.Builder searchRequest;
    if (includeXml) {
      searchRequest = getSearchRequestIncludingXml(reportByCollectionQuery, ALL_REPORT_INDICES);
    } else {
      searchRequest = getSearchRequestOmitXml(reportByCollectionQuery, ALL_REPORT_INDICES);
    }

    SearchResponse<ReportDefinitionDto> searchResponse;
    try {
      searchResponse = osClient.search(searchRequest, ReportDefinitionDto.class);
      return searchResponse.hits().hits().stream().map(Hit::source).toList();
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch reports for collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private <T extends ReportDefinitionDto> List<T> getReportDefinitionDtos(final List<String> reportIds,
                                                                          final String[] indices) {
    if (reportIds.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    Query qb = QueryDSL.ids(reportIdsAsArray);

    final SearchResponse<T> searchResponse = performGetReportRequestOmitXml(
      qb,
      indices,
      reportIdsAsArray.length
    );
    return searchResponse.hits().hits().stream().map(Hit::source)
      // make sure that the order of the reports corresponds to the one from the single report ids list
      .sorted(Comparator.comparingInt(a -> reportIds.indexOf(a.getId())))
      .toList();
  }

  private Optional<ReportDefinitionDto> processGetReportResponse(GetResult<ReportDefinitionDto> getResponse) {
    Optional<ReportDefinitionDto> result = Optional.empty();
    if (getResponse != null && getResponse.found()) {
      result = Optional.ofNullable(getResponse.source());
    }
    return result;
  }

  private GetRequest.Builder getGetRequestOmitXml(final String index, final String reportId) {
    return new GetRequest.Builder()
      .index(index)
      .id(reportId)
      .sourceExcludes(Arrays.asList(REPORT_LIST_EXCLUDES));
  }

  private SearchRequest.Builder getSearchRequestOmitXml(final Query query, final String[] indices) {
    return getSearchRequestOmitXml(query, indices, LIST_FETCH_LIMIT);
  }

  private SearchRequest.Builder getSearchRequestIncludingXml(final Query query, final String[] indices) {
    return getSearchRequest(query, indices, LIST_FETCH_LIMIT, new String[]{});
  }

  private SearchRequest.Builder getSearchRequestOmitXml(final Query query,
                                                        final String[] indices,
                                                        final int size) {
    return getSearchRequest(query, indices, size, REPORT_LIST_EXCLUDES);
  }

  private SearchRequest.Builder getSearchRequest(final Query query, final String[] indices,
                                                 final int size, final String[] excludeFields) {
    SourceConfig.Builder sourceConfig = new SourceConfig.Builder();

    if (excludeFields.length == 0) {
      sourceConfig.fetch(true);
    } else {
      SourceFilter filter = new SourceFilter.Builder()
        .excludes(Arrays.asList(excludeFields))
        .build();
      sourceConfig.filter(filter);
    }
    return new SearchRequest.Builder()
      .index(Arrays.asList(indices))
      .query(query)
      .size(size)
      .source(sourceConfig.build());
  }

  private SearchResponse performGetReportRequestOmitXml(final Query query, final String[] indices,
                                                        final int size) {
    SearchRequest.Builder searchRequest = getSearchRequestOmitXml(
      query,
      indices,
      size
    ).scroll(new Time.Builder().time(String.valueOf(configurationService.getOpenSearchConfiguration()
                                                      .getScrollTimeoutInSeconds())).build());

    try {
      return osClient.search(searchRequest, Void.class);
    } catch (IOException e) {
      log.error("Was not able to retrieve reports!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve reports!", e);
    }
  }

  private MgetResponse<ReportDefinitionDto> performMultiGetReportRequest(String reportId) {
    String errorMessage = String.format("Could not fetch report with id [%s]", reportId);

    return osClient.mget(
      ReportDefinitionDto.class,
      errorMessage,
      reportId,
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SINGLE_DECISION_REPORT_INDEX_NAME,
      COMBINED_REPORT_INDEX_NAME
    );
  }

}
