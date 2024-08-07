/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.schema.index.DashboardIndex.COLLECTION_ID;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class DashboardReaderES implements DashboardReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public long getDashboardCount() {
    final CountRequest countRequest =
        new CountRequest(
            new String[] {DASHBOARD_INDEX_NAME},
            termQuery(DashboardIndex.MANAGEMENT_DASHBOARD, false));
    try {
      return esClient.count(countRequest).getCount();
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve dashboard count!", e);
    }
  }

  @Override
  public Optional<DashboardDefinitionRestDto> getDashboard(String dashboardId) {
    log.debug("Fetching dashboard with id [{}]", dashboardId);
    GetRequest getRequest = new GetRequest(DASHBOARD_INDEX_NAME).id(dashboardId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      String reason = String.format("Could not fetch dashboard with id [%s]", dashboardId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.isExists()) {
      return Optional.empty();
    }

    String responseAsString = getResponse.getSourceAsString();
    try {
      return Optional.ofNullable(
          objectMapper.readValue(responseAsString, DashboardDefinitionRestDto.class));
    } catch (IOException e) {
      String reason = "Could not deserialize dashboard information for dashboard " + dashboardId;
      log.error(
          "Was not able to retrieve dashboard with id [{}] from Elasticsearch. Reason: {}",
          dashboardId,
          reason);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboards(Set<String> dashboardIds) {
    log.debug("Fetching dashboards with IDs {}", dashboardIds);
    final String[] dashboardIdsAsArray = dashboardIds.toArray(new String[0]);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.idsQuery().addIds(dashboardIdsAsArray));
    searchSourceBuilder.size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(DASHBOARD_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch dashboards for IDs [%s]", dashboardIds);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), DashboardDefinitionRestDto.class, objectMapper);
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForCollection(String collectionId) {
    log.debug("Fetching dashboards using collection with id {}", collectionId);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.termQuery(COLLECTION_ID, collectionId));
    searchSourceBuilder.size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(DASHBOARD_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason =
          String.format(
              "Was not able to fetch dashboards for collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), DashboardDefinitionRestDto.class, objectMapper);
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForReport(String reportId) {
    log.debug("Fetching dashboards using report with id {}", reportId);

    final QueryBuilder getCombinedReportsBySimpleReportIdQuery =
        boolQuery()
            .filter(
                QueryBuilders.nestedQuery(
                    DashboardIndex.TILES,
                    QueryBuilders.termQuery(
                        DashboardIndex.TILES + "." + DashboardIndex.REPORT_ID, reportId),
                    ScoreMode.None));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(getCombinedReportsBySimpleReportIdQuery);
    searchSourceBuilder.size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(DASHBOARD_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason =
          String.format("Was not able to fetch dashboards for report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), DashboardDefinitionRestDto.class, objectMapper);
  }
}
