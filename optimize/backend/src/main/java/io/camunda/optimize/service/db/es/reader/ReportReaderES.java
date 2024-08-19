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
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
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
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ReportReaderES implements ReportReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ReportReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public ReportReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ReportDefinitionDto> getReport(final String reportId) {
    log.debug("Fetching report with id [{}]", reportId);
    final MultiGetResponse multiGetItemResponses = performMultiGetReportRequest(reportId);

    Optional<ReportDefinitionDto> result = Optional.empty();
    for (final MultiGetItemResponse itemResponse : multiGetItemResponses) {
      final GetResponse response = itemResponse.getResponse();
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
    log.debug("Fetching single process report with id [{}]", reportId);
    final GetRequest getRequest = getGetRequestOmitXml(SINGLE_PROCESS_REPORT_INDEX_NAME, reportId);
    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
      final String reason =
          String.format("Could not fetch single process report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.isExists()) {
      return Optional.empty();
    }

    final String responseAsString = getResponse.getSourceAsString();
    try {
      return Optional.ofNullable(
          objectMapper.readValue(responseAsString, SingleProcessReportDefinitionRequestDto.class));
    } catch (final IOException e) {
      log.error(
          "Was not able to retrieve single process report with id [{}] from Elasticsearch.",
          reportId);
      throw new OptimizeRuntimeException("Can't fetch report", e);
    }
  }

  @Override
  public Optional<SingleDecisionReportDefinitionRequestDto> getSingleDecisionReportOmitXml(
      final String reportId) {
    log.debug("Fetching single decision report with id [{}]", reportId);
    final GetRequest getRequest = getGetRequestOmitXml(SINGLE_DECISION_REPORT_INDEX_NAME, reportId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
      final String reason =
          String.format("Could not fetch single decision report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.isExists()) {
      return Optional.empty();
    }

    final String responseAsString = getResponse.getSourceAsString();
    try {
      return Optional.ofNullable(
          objectMapper.readValue(responseAsString, SingleDecisionReportDefinitionRequestDto.class));
    } catch (final IOException e) {
      log.error(
          "Was not able to retrieve single decision report with id [{}] from Elasticsearch.",
          reportId);
      throw new OptimizeRuntimeException("Can't fetch report", e);
    }
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForIdsOmitXml(final List<String> reportIds) {
    if (reportIds.isEmpty()) {
      log.debug("No report IDs supplied so no reports to fetch");
      return Collections.emptyList();
    }
    log.debug("Fetching all report definitions for Ids {}", reportIds);
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    final QueryBuilder qb = QueryBuilders.idsQuery().addIds(reportIdsAsArray);
    final SearchResponse searchResponse =
        performGetReportRequestOmitXml(qb, ALL_REPORT_INDICES, LIST_FETCH_LIMIT);
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
    log.debug("Fetching all available private reports");

    final QueryBuilder qb =
        boolQuery()
            .mustNot(existsQuery(COLLECTION_ID))
            .mustNot(termQuery(DATA + "." + MANAGEMENT_REPORT, true))
            .mustNot(termQuery(DATA + "." + INSTANT_PREVIEW_REPORT, true));
    final SearchResponse searchResponse =
        performGetReportRequestOmitXml(qb, ALL_REPORT_INDICES, LIST_FETCH_LIMIT);
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        ReportDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  @Override
  public List<ReportDefinitionDto> getReportsForCollectionIncludingXml(final String collectionId) {
    return getReportsForCollection(collectionId, true);
  }

  @Override
  public List<SingleProcessReportDefinitionRequestDto> getAllSingleProcessReportsForIdsOmitXml(
      final List<String> reportIds) {
    log.debug("Fetching all available single process reports for IDs [{}]", reportIds);
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
  public List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReport(
      final String simpleReportId) {
    return getCombinedReportsForSimpleReports(Collections.singletonList(simpleReportId));
  }

  @Override
  public long getReportCount(final ReportType reportType) {
    final CountRequest countRequest;
    if (ReportType.PROCESS.equals(reportType)) {
      countRequest =
          new CountRequest(
              new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME},
              boolQuery()
                  .mustNot(termQuery(DATA + "." + MANAGEMENT_REPORT, true))
                  .mustNot(termQuery(DATA + "." + INSTANT_PREVIEW_REPORT, true)));
    } else {
      countRequest = new CountRequest(SINGLE_DECISION_REPORT_INDEX_NAME);
    }
    try {
      return esClient.count(countRequest).getCount();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve report counts!", e);
    }
  }

  @Override
  public long getUserTaskReportCount() {
    final BoolQueryBuilder query =
        boolQuery()
            .mustNot(termQuery(DATA + "." + MANAGEMENT_REPORT, true))
            .mustNot(termQuery(DATA + "." + INSTANT_PREVIEW_REPORT, true));
    final SearchRequest searchRequest =
        getSearchRequestOmitXml(query, new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME});
    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch process reports to count userTask reports.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    final List<SingleProcessReportDefinitionRequestDto> allProcessReports =
        ElasticsearchReaderUtil.mapHits(
            searchResponse.getHits(), SingleProcessReportDefinitionRequestDto.class, objectMapper);
    return allProcessReports.stream().filter(report -> report.getData().isUserTaskReport()).count();
  }

  private List<ReportDefinitionDto> getAllProcessReportsForDefinitionKeyOmitXml(
      final String definitionKey) {
    log.debug(
        "Fetching all available process reports for process definition key {}", definitionKey);
    final BoolQueryBuilder processReportQuery =
        boolQuery()
            .must(
                termQuery(
                    String.join(
                        ".",
                        DATA,
                        SingleReportDataDto.Fields.definitions,
                        ReportDataDefinitionDto.Fields.key),
                    definitionKey));
    final SearchResponse searchResponse =
        performGetReportRequestOmitXml(
            processReportQuery, new String[] {SINGLE_PROCESS_REPORT_INDEX_NAME}, LIST_FETCH_LIMIT);
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        ReportDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }

  private List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReports(
      final List<String> simpleReportIds) {
    log.debug("Fetching first combined reports using simpleReports with ids {}", simpleReportIds);

    final NestedQueryBuilder getCombinedReportsBySimpleReportIdQuery =
        nestedQuery(
            DATA,
            nestedQuery(
                String.join(".", DATA, REPORTS),
                termsQuery(String.join(".", DATA, REPORTS, REPORT_ITEM_ID), simpleReportIds),
                ScoreMode.None),
            ScoreMode.None);
    final SearchRequest searchRequest =
        getSearchRequestOmitXml(
            getCombinedReportsBySimpleReportIdQuery, new String[] {COMBINED_REPORT_INDEX_NAME});

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch combined reports that contain reports with ids [%s]",
              simpleReportIds);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), CombinedReportDefinitionRequestDto.class, objectMapper);
  }

  private List<ReportDefinitionDto> getReportsForCollection(
      final String collectionId, final boolean includeXml) {
    log.debug("Fetching reports using collection with id {}", collectionId);

    final QueryBuilder qb =
        boolQuery()
            .must(termQuery(COLLECTION_ID, collectionId))
            .mustNot(termQuery(DATA + "." + MANAGEMENT_REPORT, true))
            .mustNot(termQuery(DATA + "." + INSTANT_PREVIEW_REPORT, true));
    final SearchRequest searchRequest;
    final String[] indices = {
      COMBINED_REPORT_INDEX_NAME,
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SINGLE_DECISION_REPORT_INDEX_NAME
    };
    if (includeXml) {
      searchRequest = getSearchRequestIncludingXml(qb, indices);
    } else {
      searchRequest = getSearchRequestOmitXml(qb, indices);
    }

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(
          searchResponse.getHits(), ReportDefinitionDto.class, objectMapper);
    } catch (final IOException e) {
      final String reason =
          String.format("Was not able to fetch reports for collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private <T extends ReportDefinitionDto> List<T> getReportDefinitionDtos(
      final List<String> reportIds, final Class<T> reportType, final String[] indices) {
    if (reportIds.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    final QueryBuilder qb = QueryBuilders.idsQuery().addIds(reportIdsAsArray);
    final SearchResponse searchResponse =
        performGetReportRequestOmitXml(qb, indices, reportIdsAsArray.length);

    return mapResponseToReportList(searchResponse, reportType).stream()
        // make sure that the order of the reports corresponds to the one from the single report ids
        // list
        .sorted(Comparator.comparingInt(a -> reportIds.indexOf(a.getId())))
        .collect(Collectors.toList());
  }

  private <T extends ReportDefinitionDto> List<T> mapResponseToReportList(
      final SearchResponse searchResponse, final Class<T> c) {
    final List<T> reportDefinitionDtos = new ArrayList<>();
    for (final SearchHit hit : searchResponse.getHits().getHits()) {
      final String sourceAsString = hit.getSourceAsString();
      try {
        final T singleReportDefinitionDto = objectMapper.readValue(sourceAsString, c);
        reportDefinitionDtos.add(singleReportDefinitionDto);
      } catch (final IOException e) {
        final String reason =
            "While mapping search results of single report "
                + "it was not possible to deserialize a hit from Elasticsearch!"
                + " Hit response from Elasticsearch: "
                + sourceAsString;
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return reportDefinitionDtos;
  }

  private Optional<ReportDefinitionDto> processGetReportResponse(
      final String reportId, final GetResponse getResponse) {
    Optional<ReportDefinitionDto> result = Optional.empty();
    if (getResponse != null && getResponse.isExists()) {
      final String responseAsString = getResponse.getSourceAsString();
      try {
        final ReportDefinitionDto report =
            objectMapper.readValue(responseAsString, ReportDefinitionDto.class);
        result = Optional.of(report);
      } catch (final IOException e) {
        final String reason =
            "While retrieving report with id ["
                + reportId
                + "] "
                + "could not deserialize report from Elasticsearch!";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  private GetRequest getGetRequestOmitXml(final String index, final String reportId) {
    final GetRequest getRequest = new GetRequest(index).id(reportId);
    final FetchSourceContext fetchSourceContext =
        new FetchSourceContext(true, null, REPORT_LIST_EXCLUDES);
    getRequest.fetchSourceContext(fetchSourceContext);

    return getRequest;
  }

  private SearchRequest getSearchRequestOmitXml(final QueryBuilder query, final String[] indices) {
    return getSearchRequestOmitXml(query, indices, LIST_FETCH_LIMIT);
  }

  private SearchRequest getSearchRequestIncludingXml(
      final QueryBuilder query, final String[] indices) {
    return getSearchRequest(query, indices, LIST_FETCH_LIMIT, new String[] {});
  }

  private SearchRequest getSearchRequestOmitXml(
      final QueryBuilder query, final String[] indices, final int size) {
    return getSearchRequest(query, indices, size, REPORT_LIST_EXCLUDES);
  }

  private SearchRequest getSearchRequest(
      final QueryBuilder query,
      final String[] indices,
      final int size,
      final String[] excludeFields) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().size(size);
    if (excludeFields.length == 0) {
      searchSourceBuilder.fetchSource(true);
    } else {
      searchSourceBuilder.fetchSource(null, excludeFields);
    }
    searchSourceBuilder.query(query);
    return new SearchRequest(indices).source(searchSourceBuilder);
  }

  private SearchResponse performGetReportRequestOmitXml(
      final QueryBuilder query, final String[] indices, final int size) {
    final SearchRequest searchRequest =
        getSearchRequestOmitXml(query, indices, size)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    try {
      return esClient.search(searchRequest);
    } catch (final IOException e) {
      log.error("Was not able to retrieve reports!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve reports!", e);
    }
  }

  private MultiGetResponse performMultiGetReportRequest(final String reportId) {
    final MultiGetRequest request = new MultiGetRequest();
    request.add(new MultiGetRequest.Item(SINGLE_PROCESS_REPORT_INDEX_NAME, reportId));
    request.add(new MultiGetRequest.Item(SINGLE_DECISION_REPORT_INDEX_NAME, reportId));
    request.add(new MultiGetRequest.Item(COMBINED_REPORT_INDEX_NAME, reportId));

    final MultiGetResponse multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request);
    } catch (final IOException e) {
      final String reason = String.format("Could not fetch report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }
}
