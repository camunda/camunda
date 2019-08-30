/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchHelper;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.steps.schema.version25dto.Version25CollectionDefinitionDto;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

@Slf4j
@RequiredArgsConstructor
public class CollectionEntityDao {
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  private final ReportWriter reportWriter;
  private final DashboardWriter dashboardWriter;


  public List<CollectionEntity> getPrivateCombinedReportsAndDashboards() {
    log.debug("Fetching all private dashboards and combined reports for upgrade.");

    final QueryBuilder query = boolQuery().mustNot(existsQuery(COLLECTION_ID));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(10000);
    SearchRequest searchRequest = new SearchRequest(COMBINED_REPORT_INDEX_NAME, DASHBOARD_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new UpgradeRuntimeException("Was not able to retrieve private dashboards and comb reports!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      CollectionEntity.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public String copySingleReportToCollection(final ReportDefinitionDto report, final String collectionId) {
    switch (report.getReportType()) {
      case DECISION:
        return copySingleReportToCollection(report, collectionId, SINGLE_DECISION_REPORT_INDEX_NAME).getId();
      case PROCESS:
        return copySingleReportToCollection(report, collectionId, SINGLE_PROCESS_REPORT_INDEX_NAME).getId();
      default:
        throw new IllegalStateException("Uncovered type: " + report.getReportType());
    }
  }

  private IdDto copySingleReportToCollection(final ReportDefinitionDto report,
                                             final String collectionId,
                                             final String index) {
    log.debug("Writing new single report to Elasticsearch");

    final String id = IdGenerator.getNextId();
    final ReportDefinitionDto copiedReport = report.toBuilder().id(id).collectionId(collectionId).build();

    try {
      IndexRequest request = new IndexRequest(index, index, id)
        .source(objectMapper.writeValueAsString(copiedReport), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write single report to Elasticsearch.";
        log.error(message);
        throw new UpgradeRuntimeException(message);
      }

      log.debug("Single report with id [{}] has successfully been created.", id);
      return new IdDto(id);
    } catch (IOException e) {
      String errorMessage = "Was not able to insert single report.";
      log.error(errorMessage, e);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }


  public IdDto copyDashboardToCollection(final DashboardDefinitionDto dashboard,
                                         final String collectionId) {
    final String id = IdGenerator.getNextId();

    DashboardDefinitionDto newDashboard = new DashboardDefinitionDto();
    newDashboard.setCreated(dashboard.getCreated());
    newDashboard.setLastModified(dashboard.getLastModified());
    newDashboard.setOwner(dashboard.getOwner());
    newDashboard.setLastModifier(dashboard.getLastModifier());
    newDashboard.setName(dashboard.getName());
    newDashboard.setId(id);
    newDashboard.setCollectionId(collectionId);
    newDashboard.setReports(dashboard.getReports());

    try {
      IndexRequest request = new IndexRequest(DASHBOARD_INDEX_NAME, DASHBOARD_INDEX_NAME, id)
        .source(objectMapper.writeValueAsString(newDashboard), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write dashboard to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create dashboard.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("Dashboard with id [{}] has successfully been created.", id);
    return new IdDto(id);
  }


  public String copyCombinedReportToCollection(final ReportDefinitionDto report,
                                               final String collectionId,
                                               final String owner) {

    final String id = IdGenerator.getNextId();

    final ReportDefinitionDto combinedReportDefinitionDto = report.toBuilder()
      .id(id)
      .collectionId(collectionId)
      .owner(owner != null ? owner : report.getOwner())
      .build();

    try {
      IndexRequest request = new IndexRequest(COMBINED_REPORT_INDEX_NAME, COMBINED_REPORT_INDEX_NAME, id)
        .source(objectMapper.writeValueAsString(combinedReportDefinitionDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write report to Elasticsearch. ";
        log.error(message);
        throw new UpgradeRuntimeException(message);
      }

      log.debug("Report with id [{}] has successfully been created.", id);
      return id;
    } catch (IOException e) {
      String errorMessage = "Was not able to insert combined report.!";
      log.error(errorMessage, e);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void updateDashboardReports(final String dashboardid,
                                     final List<ReportLocationDto> newReportLocations) {
    DashboardDefinitionUpdateDto updateDto = new DashboardDefinitionUpdateDto();
    if (newReportLocations != null) {
      updateDto.setReports(newReportLocations);
    }
    dashboardWriter.updateDashboard(updateDto, dashboardid);
  }

  public void updateCombinedReportChildren(final String reportId,
                                           final List<CombinedReportItemDto> newCombinedReportItems) {

    final CombinedReportDefinitionDto report = (CombinedReportDefinitionDto) getResolvedEntity(reportId);
    final CombinedReportDataDto data = report.getData();
    data.setReports(newCombinedReportItems);

    CombinedProcessReportDefinitionUpdateDto updateDto = new CombinedProcessReportDefinitionUpdateDto();
    updateDto.setId(report.getId());
    updateDto.setData(data);

    reportWriter.updateCombinedReport(updateDto);

  }

  public List<Version25CollectionDefinitionDto> getAllCollectionsWithEntities() {

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(nestedQuery("data", existsQuery("data.entities"), ScoreMode.None))
      .sort(SimpleCollectionDefinitionDto.Fields.name.name(), SortOrder.ASC)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(COLLECTION_INDEX_NAME)
      .types(COLLECTION_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve collections!", e);
      throw new UpgradeRuntimeException("Was not able to retrieve collections!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      Version25CollectionDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }


  public CollectionEntity getResolvedEntity(final String entityId) {
    log.debug("Fetching entity with id [{}]", entityId);
    MultiGetResponse multiGetItemResponses = performGetEntityRequest(entityId);

    Optional<CollectionEntity> result = Optional.empty();
    for (MultiGetItemResponse itemResponse : multiGetItemResponses) {
      GetResponse response = itemResponse.getResponse();
      final Optional<CollectionEntity> collectionEntity = processGetEntityResponse(entityId, response);

      if (collectionEntity.isPresent()) {
        result = collectionEntity;
        break;
      }
    }

    return result.orElseThrow(() -> {
      String reason = "Was not able to retrieve entity with id [" + entityId + "]"
        + "from Elasticsearch. Entity does not exist.";
      log.error(reason);
      return new NotFoundException(reason);
    });
  }

  private MultiGetResponse performGetEntityRequest(String id) {
    MultiGetRequest request = new MultiGetRequest();
    request.add(new MultiGetRequest.Item(SINGLE_PROCESS_REPORT_INDEX_NAME, SINGLE_PROCESS_REPORT_INDEX_NAME, id));
    request.add(new MultiGetRequest.Item(SINGLE_DECISION_REPORT_INDEX_NAME, SINGLE_DECISION_REPORT_INDEX_NAME, id));
    request.add(new MultiGetRequest.Item(COMBINED_REPORT_INDEX_NAME, COMBINED_REPORT_INDEX_NAME, id));
    request.add(new MultiGetRequest.Item(DASHBOARD_INDEX_NAME, DASHBOARD_INDEX_NAME, id));

    MultiGetResponse multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch entity with id [%s]", id);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }

  private Optional<CollectionEntity> processGetEntityResponse(String entityId, GetResponse getResponse) {
    if (getResponse != null && getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return Optional.of(objectMapper.readValue(responseAsString, CollectionEntity.class));
      } catch (IOException e) {
        String reason = "While retrieving entity with id [" + entityId + "]"
          + "could not deserialize entity from Elasticsearch!";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return Optional.empty();
  }

}

