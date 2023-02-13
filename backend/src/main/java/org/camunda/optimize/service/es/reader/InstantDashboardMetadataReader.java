/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INSTANT_DASHBOARD_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class InstantDashboardMetadataReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Optional<String> getInstantDashboardIdFor(String processDefinitionKey, String template)
    throws OptimizeRuntimeException {
    log.debug("Fetching Instant Preview Dashboard ID for [{}] with template [{}] ", processDefinitionKey, template);
    InstantDashboardDataDto dashboardDataDto = new InstantDashboardDataDto();
    dashboardDataDto.setTemplateName(template);
    dashboardDataDto.setProcessDefinitionKey(processDefinitionKey);

    final String instantDashboardKey = dashboardDataDto.getInstantDashboardId();
    GetRequest getRequest = new GetRequest(INSTANT_DASHBOARD_INDEX_NAME).id(instantDashboardKey);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      String reason = String.format("Could not fetch Instant Preview Dashboard with key [%s]", instantDashboardKey);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      try {
        final InstantDashboardDataDto dashboardData = objectMapper.readValue(
          getResponse.getSourceAsString(),
          InstantDashboardDataDto.class
        );
        return Optional.of(dashboardData.getDashboardId());
      } catch (IOException e) {
        String reason = "Could not deserialize dashboard data with key [" + instantDashboardKey + "] from " +
          "Elasticsearch.";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      String reason = "Could not find dashboard data for key [" + instantDashboardKey + "] in Elasticsearch.";
      log.error(reason);
      return Optional.empty();
    }
  }
}
