/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.report.interpreter.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.db.reader.AgentDefinitionReader;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AgentDefinitionReaderES implements AgentDefinitionReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public AgentDefinitionReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<AgentDefinitionDto> getAgentDefinitions(
      final Map<String, Set<String>> tenantIdsByProcessDefinitionKey) {
    if (tenantIdsByProcessDefinitionKey.isEmpty()) {
      return List.of();
    }
    final int scrollTimeoutInSeconds =
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds();
    final SearchResponse<AgentDefinitionDto> response;
    try {
      response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(esClient, AGENT_DEFINITION_INDEX_NAME)
                          .query(
                              DefinitionQueryUtilES.createDefinitionQuery(
                                  tenantIdsByProcessDefinitionKey,
                                  AgentDefinitionIndex.PROCESS_DEFINITION_KEY,
                                  AgentDefinitionIndex.TENANT_ID))
                          .size(LIST_FETCH_LIMIT)
                          .scroll(ss -> ss.time(scrollTimeoutInSeconds + "s"))),
              AgentDefinitionDto.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve agent definitions!", e);
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        response, AgentDefinitionDto.class, objectMapper, esClient, scrollTimeoutInSeconds);
  }
}
