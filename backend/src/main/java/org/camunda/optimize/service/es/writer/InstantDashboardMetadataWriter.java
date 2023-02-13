/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INSTANT_DASHBOARD_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class InstantDashboardMetadataWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void saveInstantDashboard(InstantDashboardDataDto dashboardDataDto) {
    log.debug("Writing new Instant Preview Dashboard to Elasticsearch");
    String id = dashboardDataDto.getInstantDashboardId();
    try {
      IndexRequest request = new IndexRequest(INSTANT_DASHBOARD_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(dashboardDataDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(DocWriteResponse.Result.CREATED) &&
          !indexResponse.getResult().equals(DocWriteResponse.Result.UPDATED)) {
        String message = "Could not write Instant Preview Dashboard data to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not write Instant Preview Dashboard data to Elasticsearch.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    log.debug("Instant Preview Dashboard information with id [{}] has been created", id);
  }

  public List<String> deleteOutdatedTemplateEntries(List<Long> hashesAllowed) throws IOException {

    List<String> dashboardIdsToBeDeleted = new ArrayList<>();
    SearchRequest searchRequest = new SearchRequest(INSTANT_DASHBOARD_INDEX_NAME);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
    .mustNot(QueryBuilders.termsQuery(InstantDashboardDataDto.Fields.templateHash, hashesAllowed));
    searchSourceBuilder.query(boolQueryBuilder);
    searchRequest.source(searchSourceBuilder);

    SearchResponse searchResponse = esClient.search(searchRequest);
    searchResponse.getHits().forEach(hit -> {
      dashboardIdsToBeDeleted.add((String)hit.getSourceAsMap().get(InstantDashboardDataDto.Fields.dashboardId));
      DeleteRequest deleteRequest = new DeleteRequest(INSTANT_DASHBOARD_INDEX_NAME, hit.getId());
      try {
        esClient.delete(deleteRequest);
      } catch (IOException e) {
        log.error(String.format("There was an error deleting data from an outdated Instant Preview Dashboard: %s",
                                hit.getId()), e);
      }
    });
    return dashboardIdsToBeDeleted;
  }
}
