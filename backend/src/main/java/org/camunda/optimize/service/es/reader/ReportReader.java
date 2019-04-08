/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

@Component
public class ReportReader {
  private static final Logger logger = LoggerFactory.getLogger(ReportReader.class);

  private RestHighLevelClient esClient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;

  @Autowired
  public ReportReader(RestHighLevelClient esClient, ConfigurationService configurationService,
                      ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  /**
   * Obtain report by it's ID from elasticsearch
   *
   * @param reportId - id of report, expected not null
   * @throws OptimizeRuntimeException if report with specified ID does not
   *                                  exist or deserialization was not successful.
   */
  public ReportDefinitionDto getReport(String reportId) {
    logger.debug("Fetching report with id [{}]", reportId);
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
      logger.error(reason);
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
    request.realtime(false);

    MultiGetResponse multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch report with id [%s]", reportId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }

  public SingleProcessReportDefinitionDto getSingleProcessReport(String reportId) {
    logger.debug("Fetching single process report with id [{}]", reportId);
    GetRequest getRequest = new GetRequest(
        getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE),
        SINGLE_PROCESS_REPORT_TYPE,
        reportId)
      .realtime(true);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch single process report with id [%s]", reportId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SingleProcessReportDefinitionDto.class);
      } catch (IOException e) {
        logger.error("Was not able to retrieve single process report with id [{}] from Elasticsearch.", reportId);
        throw new OptimizeRuntimeException("Can't fetch alert");
      }
    } else {
      logger.error("Was not able to retrieve single process report with id [{}] from Elasticsearch.", reportId);
      throw new NotFoundException("Single process report does not exist!");
    }
  }

  public SingleDecisionReportDefinitionDto getSingleDecisionReport(String reportId) {
    logger.debug("Fetching single decision report with id [{}]", reportId);
    GetRequest getRequest = new GetRequest(
        getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE),
        SINGLE_DECISION_REPORT_TYPE,
        reportId)
      .realtime(true);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch single decision report with id [%s]", reportId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SingleDecisionReportDefinitionDto.class);
      } catch (IOException e) {
        logger.error("Was not able to retrieve single decision report with id [{}] from Elasticsearch.", reportId);
        throw new OptimizeRuntimeException("Can't fetch alert");
      }
    } else {
      logger.error("Was not able to retrieve single decision report with id [{}] from Elasticsearch.", reportId);
      throw new NotFoundException("single decision report does not exist!");
    }
  }

  public List<ReportDefinitionDto> getAllReports() {
    logger.debug("Fetching all available reports");
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .size(LIST_FETCH_LIMIT);
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
      logger.error("Was not able to retrieve reports!", e);
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

  public List<SingleProcessReportDefinitionDto> getAllSingleProcessReportsForIds(List<String> reportIds) {
    logger.debug("Fetching all available single process reports for ids [{}]", reportIds);

    String[] reportIdsAsArray = reportIds.toArray(new String[0]);

    SearchResponse searchResponse = performGetReportRequestForIds(reportIdsAsArray);
    List<SingleProcessReportDefinitionDto> singleReportDefinitionDtos =
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
      logger.error(errorMessage);
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
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return singleReportDefinitionDtos;
}

  private SearchResponse performGetReportRequestForIds(String[] reportIdsAsArray) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.idsQuery().addIds(reportIdsAsArray));
    searchSourceBuilder.size(reportIdsAsArray.length);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE))
        .types(SINGLE_PROCESS_REPORT_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch reports for ids [%s]", Arrays.asList(reportIdsAsArray));
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return searchResponse;
  }

  public List<CombinedReportDefinitionDto> findFirstCombinedReportsForSimpleReport(String simpleReportId) {
    logger.debug("Fetching first combined reports using simpleReport with id {}", simpleReportId);

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
      logger.error(reason, e);
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
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return result;

  }

}
