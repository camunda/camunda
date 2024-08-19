/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.INSTANT_DASHBOARD_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.InstantDashboardMetadataReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Optional;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class InstantDashboardMetadataReaderES implements InstantDashboardMetadataReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(InstantDashboardMetadataReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public InstantDashboardMetadataReaderES(
      final OptimizeElasticsearchClient esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<String> getInstantDashboardIdFor(
      final String processDefinitionKey, final String template) throws OptimizeRuntimeException {
    log.debug(
        "Fetching Instant preview dashboard ID for [{}] with template [{}] ",
        processDefinitionKey,
        template);
    final InstantDashboardDataDto dashboardDataDto = new InstantDashboardDataDto();
    dashboardDataDto.setTemplateName(template);
    dashboardDataDto.setProcessDefinitionKey(processDefinitionKey);

    final String instantDashboardKey = dashboardDataDto.getInstantDashboardId();
    final GetRequest getRequest =
        new GetRequest(INSTANT_DASHBOARD_INDEX_NAME).id(instantDashboardKey);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Could not fetch Instant preview dashboard with key [%s]", instantDashboardKey);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      try {
        final InstantDashboardDataDto dashboardData =
            objectMapper.readValue(getResponse.getSourceAsString(), InstantDashboardDataDto.class);
        return Optional.of(dashboardData.getDashboardId());
      } catch (final IOException e) {
        final String reason =
            "Could not deserialize dashboard data with key ["
                + instantDashboardKey
                + "] from "
                + "Elasticsearch.";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      final String reason =
          "Could not find dashboard data for key [" + instantDashboardKey + "] in Elasticsearch.";
      log.error(reason);
      return Optional.empty();
    }
  }
}
