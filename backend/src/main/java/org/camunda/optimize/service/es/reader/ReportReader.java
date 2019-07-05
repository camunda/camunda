/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.report.AbstractReportType.DATA;
import static org.camunda.optimize.service.es.schema.type.report.CombinedReportType.REPORTS;
import static org.camunda.optimize.service.es.schema.type.report.CombinedReportType.REPORT_ITEM_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportReader {

  private final RestHighLevelClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  /**
   * Obtain report by it's ID from elasticsearch
   *
   * @param reportId - id of report, expected not null
   * @throws OptimizeRuntimeException if report with specified ID does not
   *                                  exist or deserialization was not successful.
   */
  public ReportDefinitionDto getReport(String reportId) {
    log.debug("Fetching report with id [{}]", reportId);
    MultiGetResponse multiGetItemResponses = performGetReportRequest(reportId);

    Optional<ReportDefinitionDto> result = Optional.empty();
    for (MultiGetItemResponse itemResponse : multiGetItemResponses) {
      GetResponse response = itemResponse.getResponse();
      Optional<ReportDefinitionDto> reportDefinitionDto = processGetReportResponse(reportId, response);
      if (reportDefinitionDto.isPresent()) {
        result = reportDefinitionDto;
        break;
      }
    }

    if (!result.isPresent()) {
      String reason = "Was not able to retrieve report with id [" + reportId + "]"
        + "from Elasticsearch. Report does not exist.";
      log.error(reason);
      throw new NotFoundException(reason);
    }
    return result.get();
  }

  private MultiGetResponse performGetReportRequest(String reportId) {
    MultiGetRequest request = new MultiGetRequest();
    request.add(new MultiGetRequest.Item(
      getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE),
      SINGLE_PROCESS_REPORT_TYPE,
      reportId
    ));
    request.add(new MultiGetRequest.Item(
      getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE),
      SINGLE_DECISION_REPORT_TYPE,
      reportId
    ));
    request.add(new MultiGetRequest.Item(
      getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE),
      COMBINED_REPORT_TYPE,
      reportId
    ));

    MultiGetResponse multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }

  public SingleProcessReportDefinitionDto getSingleProcessReport(String reportId) {
    log.debug("Fetching single process report with id [{}]", reportId);
    GetRequest getRequest = new GetRequest(
      getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE),
      SINGLE_PROCESS_REPORT_TYPE,
      reportId
    );

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch single process report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SingleProcessReportDefinitionDto.class);
      } catch (IOException e) {
        log.error("Was not able to retrieve single process report with id [{}] from Elasticsearch.", reportId);
        throw new OptimizeRuntimeException("Can't fetch alert");
      }
    } else {
      log.error("Was not able to retrieve single process report with id [{}] from Elasticsearch.", reportId);
      throw new NotFoundException("Single process report does not exist!");
    }
  }

  public SingleDecisionReportDefinitionDto getSingleDecisionReport(String reportId) {
    log.debug("Fetching single decision report with id [{}]", reportId);
    GetRequest getRequest = new GetRequest(
      getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE),
      SINGLE_DECISION_REPORT_TYPE,
      reportId
    );

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch single decision report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SingleDecisionReportDefinitionDto.class);
      } catch (IOException e) {
        log.error("Was not able to retrieve single decision report with id [{}] from Elasticsearch.", reportId);
        throw new OptimizeRuntimeException("Can't fetch alert");
      }
    } else {
      log.error("Was not able to retrieve single decision report with id [{}] from Elasticsearch.", reportId);
      throw new NotFoundException("single decision report does not exist!");
    }
  }

  public List<ReportDefinitionDto> getAllReports(final boolean withXml) {
    log.debug("Fetching all available reports");

    final String[] fieldsToExclude = withXml ? null : new String[]{"*.xml"};
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    SearchRequest searchRequest =
      new SearchRequest(
        getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE),
        getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE),
        getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE)
      )
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve reports!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve reports!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      ReportDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public List<SingleProcessReportDefinitionDto> getAllSingleProcessReportsForIds(final List<String> reportIds,
                                                                                 final boolean withXml) {
    log.debug("Fetching all available single process reports for ids [{}]", reportIds);

    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    final SearchResponse searchResponse = performGetReportRequestForIds(reportIdsAsArray, withXml);
    final List<SingleProcessReportDefinitionDto> singleReportDefinitionDtos =
      mapResponseToReportList(searchResponse).stream()
        // make sure that the order of the reports corresponds to the one from the single report ids list
        .sorted(Comparator.comparingInt(a -> reportIds.indexOf(a.getId())))
        .collect(Collectors.toList());

    if (reportIds.size() != singleReportDefinitionDtos.size()) {
      List<String> fetchedReportIds = singleReportDefinitionDtos.stream()
        .map(SingleProcessReportDefinitionDto::getId)
        .collect(Collectors.toList());
      String errorMessage =
        String.format("Error trying to fetch reports for given ids. Given ids [%s] and fetched [%s]. " +
                        "There is a mismatch here. Maybe one report does not exist?",
                      reportIds, fetchedReportIds
        );
      log.error(errorMessage);
      throw new NotFoundException(errorMessage);
    }
    return singleReportDefinitionDtos;
  }

  private List<SingleProcessReportDefinitionDto> mapResponseToReportList(SearchResponse searchResponse) {
    List<SingleProcessReportDefinitionDto> singleReportDefinitionDtos = new ArrayList<>();
    for (SearchHit hit : searchResponse.getHits().getHits()) {
      String sourceAsString = hit.getSourceAsString();
      try {
        SingleProcessReportDefinitionDto singleReportDefinitionDto = objectMapper.readValue(
          sourceAsString,
          SingleProcessReportDefinitionDto.class
        );
        singleReportDefinitionDtos.add(singleReportDefinitionDto);
      } catch (IOException e) {
        String reason = "While mapping search results of single report "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: "
          + sourceAsString;
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return singleReportDefinitionDtos;
  }

  private SearchResponse performGetReportRequestForIds(final String[] reportIdsAsArray, final boolean withXml) {
    final String[] fieldsToExclude = withXml ? null : new String[]{"*.xml"};

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.idsQuery().addIds(reportIdsAsArray));
    searchSourceBuilder.size(reportIdsAsArray.length);
    searchSourceBuilder.fetchSource(null, fieldsToExclude);

    final SearchRequest searchRequest = new SearchRequest(getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE))
      .types(SINGLE_PROCESS_REPORT_TYPE)
      .source(searchSourceBuilder);

    try {
      return esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch reports for ids [%s]", Arrays.asList(reportIdsAsArray));
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  public List<CombinedReportDefinitionDto> findFirstCombinedReportsForSimpleReport(String simpleReportId) {
    log.debug("Fetching first combined reports using simpleReport with id {}", simpleReportId);

    final NestedQueryBuilder getCombinedReportsBySimpleReportIdQuery = nestedQuery(
      DATA,
      nestedQuery(
        String.join(".", DATA, REPORTS),
        termQuery(String.join(".", DATA, REPORTS, REPORT_ITEM_ID), simpleReportId),
        ScoreMode.None
      ),
      ScoreMode.None
    );
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(getCombinedReportsBySimpleReportIdQuery);
    searchSourceBuilder.size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE))
        .types(COMBINED_REPORT_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch combined reports that contain report with id [%s]",
        simpleReportId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return ElasticsearchHelper.mapHits(searchResponse.getHits(), CombinedReportDefinitionDto.class, objectMapper);
  }

  private Optional<ReportDefinitionDto> processGetReportResponse(String reportId, GetResponse getResponse) {
    Optional<ReportDefinitionDto> result = Optional.empty();
    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        ReportDefinitionDto report = objectMapper.readValue(responseAsString, ReportDefinitionDto.class);
        result = Optional.of(report);
      } catch (IOException e) {
        String reason = "While retrieving report with id [" + reportId + "]"
          + "could not deserialize report from Elasticsearch!";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return result;

  }

}
