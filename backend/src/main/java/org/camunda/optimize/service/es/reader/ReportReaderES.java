/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.db.reader.ReportReader;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.COLLECTION_ID;
import static org.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.DATA;
import static org.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORTS;
import static org.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORT_ITEM_ID;
import static org.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ReportReaderES implements ReportReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<ReportDefinitionDto> getReport(String reportId) {
    log.debug("Fetching report with id [{}]", reportId);
    MultiGetResponse multiGetItemResponses = performMultiGetReportRequest(reportId);

    Optional<ReportDefinitionDto> result = Optional.empty();
    for (MultiGetItemResponse itemResponse : multiGetItemResponses) {
      GetResponse response = itemResponse.getResponse();
      Optional<ReportDefinitionDto> reportDefinitionDto = processGetReportResponse(reportId, response);
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
    GetRequest getRequest = getGetRequestOmitXml(SINGLE_PROCESS_REPORT_INDEX_NAME, reportId);
    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      String reason = String.format("Could not fetch single process report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.isExists()) {
      return Optional.empty();
    }

    String responseAsString = getResponse.getSourceAsString();
    try {
      return Optional.ofNullable(objectMapper.readValue(
        responseAsString,
        SingleProcessReportDefinitionRequestDto.class
      ));
    } catch (IOException e) {
      log.error("Was not able to retrieve single process report with id [{}] from Elasticsearch.", reportId);
      throw new OptimizeRuntimeException("Can't fetch report");
    }
  }

  @Override
  public Optional<SingleDecisionReportDefinitionRequestDto> getSingleDecisionReportOmitXml(final String reportId) {
    log.debug("Fetching single decision report with id [{}]", reportId);
    GetRequest getRequest = getGetRequestOmitXml(SINGLE_DECISION_REPORT_INDEX_NAME, reportId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      String reason = String.format("Could not fetch single decision report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.isExists()) {
      return Optional.empty();
    }

    String responseAsString = getResponse.getSourceAsString();
    try {
      return Optional.ofNullable(objectMapper.readValue(
        responseAsString,
        SingleDecisionReportDefinitionRequestDto.class
      ));
    } catch (IOException e) {
      log.error("Was not able to retrieve single decision report with id [{}] from Elasticsearch.", reportId);
      throw new OptimizeRuntimeException("Can't fetch report");
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
    QueryBuilder qb = QueryBuilders.idsQuery().addIds(reportIdsAsArray);
    SearchResponse searchResponse = performGetReportRequestOmitXml(
      qb,
      ALL_REPORT_INDICES,
      LIST_FETCH_LIMIT
    );
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      searchResponse,
      ReportDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    );
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(final String definitionKey) {
    final List<ReportDefinitionDto> processReportsForKey = getAllProcessReportsForDefinitionKeyOmitXml(definitionKey);
    final List<String> processReportIds = processReportsForKey.stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toList());
    final List<CombinedReportDefinitionRequestDto> combinedReports =
      getCombinedReportsForSimpleReports(processReportIds);
    processReportsForKey.addAll(combinedReports);
    return processReportsForKey;
  }

  @Override
  public List<ReportDefinitionDto> getAllPrivateReportsOmitXml() {
    log.debug("Fetching all available private reports");
    QueryBuilder qb = boolQuery().mustNot(existsQuery(COLLECTION_ID))
      .minimumShouldMatch(1)
      .should(boolQuery().must(termQuery(DATA + "." + MANAGEMENT_REPORT, false)))
      .should(boolQuery().mustNot(existsQuery(DATA + "." + MANAGEMENT_REPORT)));
    SearchResponse searchResponse = performGetReportRequestOmitXml(
      qb,
      ALL_REPORT_INDICES,
      LIST_FETCH_LIMIT
    );
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      searchResponse,
      ReportDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    );
  }

  @Override
  public List<SingleProcessReportDefinitionRequestDto> getAllSingleProcessReportsForIdsOmitXml(final List<String> reportIds) {
    log.debug("Fetching all available single process reports for IDs [{}]", reportIds);
    final Class<SingleProcessReportDefinitionRequestDto> reportType = SingleProcessReportDefinitionRequestDto.class;
    final String[] indices = new String[]{SINGLE_PROCESS_REPORT_INDEX_NAME};
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
  public List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReport(String simpleReportId) {
    return getCombinedReportsForSimpleReports(Collections.singletonList(simpleReportId));
  }

  @Override
  public long getReportCount(final ReportType reportType) {
    final CountRequest countRequest;
    if (ReportType.PROCESS.equals(reportType)) {
      countRequest = new CountRequest(
        new String[]{SINGLE_PROCESS_REPORT_INDEX_NAME},
        termQuery(String.join(".", DATA, MANAGEMENT_REPORT), false)
      );
    } else {
      countRequest = new CountRequest(SINGLE_DECISION_REPORT_INDEX_NAME);
    }
    try {
      return esClient.count(countRequest).getCount();
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve report counts!", e);
    }
  }

  private List<ReportDefinitionDto> getAllProcessReportsForDefinitionKeyOmitXml(final String definitionKey) {
    log.debug("Fetching all available process reports for process definition key {}", definitionKey);
    final BoolQueryBuilder processReportQuery = boolQuery()
      .must(termQuery(
        String.join(".", DATA, SingleReportDataDto.Fields.definitions, ReportDataDefinitionDto.Fields.key),
        definitionKey
      ));
    SearchResponse searchResponse = performGetReportRequestOmitXml(
      processReportQuery,
      new String[]{SINGLE_PROCESS_REPORT_INDEX_NAME},
      LIST_FETCH_LIMIT
    );
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      searchResponse,
      ReportDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    );
  }

  private List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReports(List<String> simpleReportIds) {
    log.debug("Fetching first combined reports using simpleReports with ids {}", simpleReportIds);

    final NestedQueryBuilder getCombinedReportsBySimpleReportIdQuery = nestedQuery(
      DATA,
      nestedQuery(
        String.join(".", DATA, REPORTS),
        termsQuery(String.join(".", DATA, REPORTS, REPORT_ITEM_ID), simpleReportIds),
        ScoreMode.None
      ),
      ScoreMode.None
    );
    SearchRequest searchRequest = getSearchRequestOmitXml(
      getCombinedReportsBySimpleReportIdQuery,
      new String[]{COMBINED_REPORT_INDEX_NAME}
    );

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch combined reports that contain reports with ids [%s]",
        simpleReportIds
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return ElasticsearchReaderUtil.mapHits(
      searchResponse.getHits(),
      CombinedReportDefinitionRequestDto.class,
      objectMapper
    );
  }

  private List<ReportDefinitionDto> getReportsForCollection(final String collectionId, final boolean includeXml) {
    log.debug("Fetching reports using collection with id {}", collectionId);

    QueryBuilder qb = boolQuery().must(termQuery(COLLECTION_ID, collectionId))
      .minimumShouldMatch(1)
      .should(boolQuery().must(termQuery(DATA + "." + MANAGEMENT_REPORT, false)))
      .should(boolQuery().mustNot(existsQuery(DATA + "." + MANAGEMENT_REPORT)));
    SearchRequest searchRequest;
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

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), ReportDefinitionDto.class, objectMapper);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch reports for collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private <T extends ReportDefinitionDto> List<T> getReportDefinitionDtos(final List<String> reportIds,
                                                                          final Class<T> reportType,
                                                                          final String[] indices) {
    if (reportIds.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    QueryBuilder qb = QueryBuilders.idsQuery().addIds(reportIdsAsArray);
    final SearchResponse searchResponse = performGetReportRequestOmitXml(
      qb,
      indices,
      reportIdsAsArray.length
    );

    return mapResponseToReportList(searchResponse, reportType).stream()
      // make sure that the order of the reports corresponds to the one from the single report ids list
      .sorted(Comparator.comparingInt(a -> reportIds.indexOf(a.getId())))
      .collect(Collectors.toList());
  }

  private <T extends ReportDefinitionDto> List<T> mapResponseToReportList(SearchResponse searchResponse, Class<T> c) {
    List<T> reportDefinitionDtos = new ArrayList<>();
    for (SearchHit hit : searchResponse.getHits().getHits()) {
      String sourceAsString = hit.getSourceAsString();
      try {
        T singleReportDefinitionDto = objectMapper.readValue(
          sourceAsString,
          c
        );
        reportDefinitionDtos.add(singleReportDefinitionDto);
      } catch (IOException e) {
        String reason = "While mapping search results of single report "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: "
          + sourceAsString;
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return reportDefinitionDtos;
  }

  private Optional<ReportDefinitionDto> processGetReportResponse(String reportId, GetResponse getResponse) {
    Optional<ReportDefinitionDto> result = Optional.empty();
    if (getResponse != null && getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        ReportDefinitionDto report = objectMapper.readValue(responseAsString, ReportDefinitionDto.class);
        result = Optional.of(report);
      } catch (IOException e) {
        String reason = "While retrieving report with id [" + reportId + "] "
          + "could not deserialize report from Elasticsearch!";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return result;
  }

  private GetRequest getGetRequestOmitXml(final String index, final String reportId) {
    GetRequest getRequest = new GetRequest(index).id(reportId);
    FetchSourceContext fetchSourceContext = new FetchSourceContext(true, null, REPORT_LIST_EXCLUDES);
    getRequest.fetchSourceContext(fetchSourceContext);

    return getRequest;
  }

  private SearchRequest getSearchRequestOmitXml(final QueryBuilder query, final String[] indices) {
    return getSearchRequestOmitXml(query, indices, LIST_FETCH_LIMIT);
  }

  private SearchRequest getSearchRequestIncludingXml(final QueryBuilder query, final String[] indices) {
    return getSearchRequest(query, indices, LIST_FETCH_LIMIT, new String[]{});
  }

  private SearchRequest getSearchRequestOmitXml(final QueryBuilder query, final String[] indices,
                                                final int size) {
    return getSearchRequest(query, indices, size, REPORT_LIST_EXCLUDES);
  }

  private SearchRequest getSearchRequest(final QueryBuilder query, final String[] indices,
                                         final int size, final String[] excludeFields) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .size(size);
    if (excludeFields.length == 0) {
      searchSourceBuilder.fetchSource(true);
    } else {
      searchSourceBuilder.fetchSource(null, excludeFields);
    }
    searchSourceBuilder.query(query);
    return new SearchRequest(indices)
      .source(searchSourceBuilder);
  }

  private SearchResponse performGetReportRequestOmitXml(final QueryBuilder query, final String[] indices,
                                                        final int size) {
    SearchRequest searchRequest = getSearchRequestOmitXml(
      query,
      indices,
      size
    ).scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    try {
      return esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to retrieve reports!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve reports!", e);
    }
  }

  private MultiGetResponse performMultiGetReportRequest(String reportId) {
    MultiGetRequest request = new MultiGetRequest();
    request.add(new MultiGetRequest.Item(SINGLE_PROCESS_REPORT_INDEX_NAME, reportId));
    request.add(new MultiGetRequest.Item(SINGLE_DECISION_REPORT_INDEX_NAME, reportId));
    request.add(new MultiGetRequest.Item(COMBINED_REPORT_INDEX_NAME, reportId));

    MultiGetResponse multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request);
    } catch (IOException e) {
      String reason = String.format("Could not fetch report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }

}
