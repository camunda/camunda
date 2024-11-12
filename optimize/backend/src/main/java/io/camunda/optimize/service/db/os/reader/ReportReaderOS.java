/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.DATA;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORTS;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORT_ITEM_ID;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.INSTANT_PREVIEW_REPORT;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
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
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ReportReaderOS implements ReportReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ReportReaderOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  public ReportReaderOS(
      final OptimizeOpenSearchClient osClient, final ConfigurationService configurationService) {
    this.osClient = osClient;
    this.configurationService = configurationService;
  }

  @Override
  public Optional<ReportDefinitionDto> getReport(final String reportId) {
    LOG.debug("Fetching report with id [{}]", reportId);
    final MgetResponse<ReportDefinitionDto> multiGetItemResponses =
        performMultiGetReportRequest(reportId);

    Optional<ReportDefinitionDto> result = Optional.empty();

    for (final MultiGetResponseItem<ReportDefinitionDto> itemResponse :
        multiGetItemResponses.docs()) {
      final GetResult<ReportDefinitionDto> response = itemResponse.result();
      final Optional<ReportDefinitionDto> reportDefinitionDto =
          OpensearchReaderUtil.processGetResponse(response);
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
    final GetRequest.Builder getRequest =
        getGetRequestOmitXml(SINGLE_PROCESS_REPORT_INDEX_NAME, reportId);

    final String errorMessage =
        String.format("Could not fetch single process report with id [%s]", reportId);

    final GetResponse<SingleProcessReportDefinitionRequestDto> getResponse =
        osClient.get(getRequest, SingleProcessReportDefinitionRequestDto.class, errorMessage);

    if (!getResponse.found()) {
      return Optional.empty();
    }

    return Optional.ofNullable(getResponse.source());
  }

  @Override
  public Optional<SingleDecisionReportDefinitionRequestDto> getSingleDecisionReportOmitXml(
      final String reportId) {
    LOG.debug("Fetching single decision report with id [{}]", reportId);
    final GetRequest.Builder getRequest =
        getGetRequestOmitXml(SINGLE_DECISION_REPORT_INDEX_NAME, reportId);

    final String errorMessage =
        String.format("Could not fetch single decision report with id [%s]", reportId);

    final GetResponse<SingleDecisionReportDefinitionRequestDto> getResponse =
        osClient.get(getRequest, SingleDecisionReportDefinitionRequestDto.class, errorMessage);

    if (!getResponse.found()) {
      return Optional.empty();
    }

    return Optional.ofNullable(getResponse.source());
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForIdsOmitXml(final List<String> reportIds) {
    if (reportIds.isEmpty()) {
      LOG.debug("No report IDs supplied so no reports to fetch");
      return Collections.emptyList();
    }
    LOG.debug("Fetching all report definitions for Ids {}", reportIds);
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    final Query reportIdsQuery = QueryDSL.ids(reportIdsAsArray);

    final SearchRequest.Builder searchRequest =
        getSearchRequestOmitXml(reportIdsQuery, ALL_REPORT_INDICES, LIST_FETCH_LIMIT)
            .scroll(
                new Time.Builder()
                    .time(
                        String.valueOf(
                            configurationService
                                .getOpenSearchConfiguration()
                                .getScrollTimeoutInSeconds()))
                    .build());

    final OpenSearchDocumentOperations.AggregatedResult<Hit<ReportDefinitionDto>> searchResponse;
    try {
      searchResponse = osClient.retrieveAllScrollResults(searchRequest, ReportDefinitionDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch all reports definitions for ids [%s]", reportIds);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(searchResponse);
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(
      final String definitionKey) {
    final List<ReportDefinitionDto> processReportsForKey =
        getAllProcessReportsForDefinitionKeyOmitXml(definitionKey);
    final List<String> processReportIds =
        processReportsForKey.stream().map(ReportDefinitionDto::getId).toList();
    final List<CombinedReportDefinitionRequestDto> combinedReports =
        getCombinedReportsForSimpleReports(processReportIds);
    processReportsForKey.addAll(combinedReports);
    return processReportsForKey;
  }

  @Override
  public List<ReportDefinitionDto> getAllPrivateReportsOmitXml() {
    LOG.debug("Fetching all available private reports");

    final Query privateReportsQuery =
        new BoolQuery.Builder()
            .mustNot(QueryDSL.exists(COLLECTION_ID))
            .mustNot(QueryDSL.term(DATA + "." + MANAGEMENT_REPORT, true))
            .mustNot(QueryDSL.term(DATA + "." + INSTANT_PREVIEW_REPORT, true))
            .build()
            .toQuery();

    final SearchRequest.Builder searchRequest =
        getSearchRequestOmitXml(privateReportsQuery, ALL_REPORT_INDICES, LIST_FETCH_LIMIT)
            .scroll(
                new Time.Builder()
                    .time(
                        String.valueOf(
                            configurationService
                                .getOpenSearchConfiguration()
                                .getScrollTimeoutInSeconds()))
                    .build());

    final OpenSearchDocumentOperations.AggregatedResult<Hit<ReportDefinitionDto>> searchResponse;
    try {
      searchResponse = osClient.retrieveAllScrollResults(searchRequest, ReportDefinitionDto.class);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch all private reports";
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(searchResponse);
  }

  @Override
  public List<ReportDefinitionDto> getReportsForCollectionIncludingXml(final String collectionId) {
    return getReportsForCollection(collectionId, true);
  }

  @Override
  public List<SingleProcessReportDefinitionRequestDto> getAllSingleProcessReportsForIdsOmitXml(
      final List<String> reportIds) {
    LOG.debug("Fetching all available single process reports for IDs [{}]", reportIds);
    final String[] indices = new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME};
    return getReportDefinitionDtos(reportIds, indices);
  }

  @Override
  public List<ReportDefinitionDto> getReportsForCollectionOmitXml(final String collectionId) {
    return getReportsForCollection(collectionId, false);
  }

  @Override
  public List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReport(
      final String simpleReportId) {
    return getCombinedReportsForSimpleReports(Collections.singletonList(simpleReportId));
  }

  @Override
  public long getReportCount(final ReportType reportType) {
    final String errorMessage =
        String.format("Was not able to retrieve %s report counts!", reportType);
    if (ReportType.PROCESS.equals(reportType)) {
      final Query query =
          new BoolQuery.Builder()
              .mustNot(QueryDSL.term(DATA + "." + MANAGEMENT_REPORT, true))
              .mustNot(QueryDSL.term(DATA + "." + INSTANT_PREVIEW_REPORT, true))
              .build()
              .toQuery();
      return osClient.count(new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME}, query, errorMessage);
    } else {
      return osClient.count(
          new String[] {SINGLE_DECISION_REPORT_INDEX_NAME}, QueryDSL.matchAll(), errorMessage);
    }
  }

  @Override
  public long getUserTaskReportCount() {
    final Query query =
        new BoolQuery.Builder()
            .mustNot(QueryDSL.term(DATA + "." + MANAGEMENT_REPORT, true))
            .mustNot(QueryDSL.term(DATA + "." + INSTANT_PREVIEW_REPORT, true))
            .build()
            .toQuery();
    final SearchRequest.Builder searchRequest =
        getSearchRequestOmitXml(query, new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME});
    final String errorMessage = "Was not able to fetch process reports to count userTask reports.";
    final SearchResponse<SingleProcessReportDefinitionRequestDto> searchResponse =
        osClient.search(searchRequest, SingleProcessReportDefinitionRequestDto.class, errorMessage);
    final List<SingleProcessReportDefinitionRequestDto> allProcessReports =
        OpensearchReaderUtil.extractResponseValues(searchResponse);
    return allProcessReports.stream().filter(report -> report.getData().isUserTaskReport()).count();
  }

  private List<ReportDefinitionDto> getAllProcessReportsForDefinitionKeyOmitXml(
      final String definitionKey) {
    LOG.debug(
        "Fetching all available process reports for process definition key {}", definitionKey);

    final Query processReportQuery =
        new BoolQuery.Builder()
            .must(
                QueryDSL.term(
                    String.join(
                        ".",
                        DATA,
                        SingleReportDataDto.Fields.definitions,
                        ReportDataDefinitionDto.Fields.key),
                    definitionKey))
            .build()
            .toQuery();

    final SearchRequest.Builder searchRequest =
        getSearchRequestOmitXml(
                processReportQuery,
                new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME},
                LIST_FETCH_LIMIT)
            .scroll(
                new Time.Builder()
                    .time(
                        String.valueOf(
                            configurationService
                                .getOpenSearchConfiguration()
                                .getScrollTimeoutInSeconds()))
                    .build());

    final OpenSearchDocumentOperations.AggregatedResult<Hit<ReportDefinitionDto>> searchResponse;
    try {
      searchResponse = osClient.retrieveAllScrollResults(searchRequest, ReportDefinitionDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch all process reports for definition key [%s]", definitionKey);
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(searchResponse);
  }

  private List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReports(
      final List<String> simpleReportIds) {
    LOG.debug("Fetching first combined reports using simpleReports with ids {}", simpleReportIds);

    final Query inner =
        new NestedQuery.Builder()
            .path(String.join(".", DATA, REPORTS))
            .query(
                QueryDSL.terms(
                    String.join(".", DATA, REPORTS, REPORT_ITEM_ID),
                    simpleReportIds,
                    FieldValue::of))
            .scoreMode(ChildScoreMode.None)
            .build()
            .toQuery();

    final Query getCombinedReportsBySimpleReportIdQuery =
        new NestedQuery.Builder()
            .path(DATA)
            .query(inner)
            .scoreMode(ChildScoreMode.None)
            .build()
            .toQuery();

    final SearchRequest.Builder searchRequest =
        getSearchRequestOmitXml(
            getCombinedReportsBySimpleReportIdQuery, new String[] {COMBINED_REPORT_INDEX_NAME});

    final String errorMessage =
        String.format(
            "Was not able to fetch combined reports that contain reports with ids [%s]",
            simpleReportIds);
    final SearchResponse<CombinedReportDefinitionRequestDto> searchResponse =
        osClient.search(searchRequest, CombinedReportDefinitionRequestDto.class, errorMessage);

    return OpensearchReaderUtil.extractResponseValues(searchResponse);
  }

  private List<ReportDefinitionDto> getReportsForCollection(
      final String collectionId, final boolean includeXml) {
    LOG.debug("Fetching reports using collection with id {}", collectionId);

    final Query reportByCollectionQuery =
        new BoolQuery.Builder()
            .must(QueryDSL.term(COLLECTION_ID, collectionId))
            .mustNot(QueryDSL.term(DATA + "." + MANAGEMENT_REPORT, true))
            .mustNot(QueryDSL.term(DATA + "." + INSTANT_PREVIEW_REPORT, true))
            .build()
            .toQuery();

    final SearchRequest.Builder searchRequest;
    if (includeXml) {
      searchRequest = getSearchRequestIncludingXml(reportByCollectionQuery, ALL_REPORT_INDICES);
    } else {
      searchRequest = getSearchRequestOmitXml(reportByCollectionQuery, ALL_REPORT_INDICES);
    }

    final String errorMessage =
        String.format("Was not able to fetch reports for collection with id [%s]", collectionId);
    final SearchResponse<ReportDefinitionDto> searchResponse =
        osClient.search(searchRequest, ReportDefinitionDto.class, errorMessage);
    return OpensearchReaderUtil.extractResponseValues(searchResponse);
  }

  private <T extends ReportDefinitionDto> List<T> getReportDefinitionDtos(
      final List<String> reportIds, final String[] indices) {
    if (reportIds.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    final Query qb = QueryDSL.ids(reportIdsAsArray);

    final SearchResponse<T> searchResponse =
        performGetReportRequestOmitXml(qb, indices, reportIdsAsArray.length);
    return OpensearchReaderUtil.extractResponseValues(searchResponse).stream()
        // make sure that the order of the reports corresponds to the one from the single report ids
        // list
        .sorted(Comparator.comparingInt(element -> reportIds.indexOf(element.getId())))
        .collect(Collectors.toList());
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

  private SearchRequest.Builder getSearchRequestIncludingXml(
      final Query query, final String[] indices) {
    return getSearchRequest(query, indices, LIST_FETCH_LIMIT, new String[] {});
  }

  private SearchRequest.Builder getSearchRequestOmitXml(
      final Query query, final String[] indices, final int size) {
    return getSearchRequest(query, indices, size, REPORT_LIST_EXCLUDES);
  }

  private SearchRequest.Builder getSearchRequest(
      final Query query, final String[] indices, final int size, final String[] excludeFields) {
    final SourceConfig.Builder sourceConfig = new SourceConfig.Builder();

    if (excludeFields.length == 0) {
      sourceConfig.fetch(true);
    } else {
      final SourceFilter filter =
          new SourceFilter.Builder().excludes(Arrays.asList(excludeFields)).build();
      sourceConfig.filter(filter);
    }
    return new SearchRequest.Builder()
        .index(Arrays.asList(indices))
        .query(query)
        .size(size)
        .source(sourceConfig.build());
  }

  private SearchResponse performGetReportRequestOmitXml(
      final Query query, final String[] indices, final int size) {
    final SearchRequest.Builder searchRequest = getSearchRequestOmitXml(query, indices, size);
    final String errorMessage = "Was not able to retrieve reports!";
    return osClient.search(searchRequest, ReportDefinitionDto.class, errorMessage);
  }

  private MgetResponse<ReportDefinitionDto> performMultiGetReportRequest(final String reportId) {
    final String errorMessage = String.format("Could not fetch report with id [%s]", reportId);

    final Map<String, String> indexesToEntitiesId = new HashMap<>();
    indexesToEntitiesId.put(SINGLE_PROCESS_REPORT_INDEX_NAME, reportId);
    indexesToEntitiesId.put(SINGLE_DECISION_REPORT_INDEX_NAME, reportId);
    indexesToEntitiesId.put(COMBINED_REPORT_INDEX_NAME, reportId);
    return osClient.mget(ReportDefinitionDto.class, errorMessage, indexesToEntitiesId);
  }
}
