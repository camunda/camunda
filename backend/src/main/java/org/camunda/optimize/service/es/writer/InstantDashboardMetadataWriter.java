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
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INSTANT_DASHBOARD_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class InstantDashboardMetadataWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void saveInstantDashboard(InstantDashboardDataDto dashboardDataDto) {
    log.debug("Writing new instant dashboard to Elasticsearch");
    String id = dashboardDataDto.getId();
    try {
      IndexRequest request = new IndexRequest(INSTANT_DASHBOARD_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(dashboardDataDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(DocWriteResponse.Result.CREATED) &&
          !indexResponse.getResult().equals(DocWriteResponse.Result.UPDATED)) {
        String message = "Could not write instant dashboard data to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not write instant dashboard data to Elasticsearch.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    log.debug("Instant dashboard information with id [{}] has been created", id);
  }
}
