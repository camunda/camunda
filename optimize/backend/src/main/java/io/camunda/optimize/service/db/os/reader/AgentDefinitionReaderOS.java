/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.os.util.DefinitionQueryUtilOS;
import io.camunda.optimize.service.db.reader.AgentDefinitionReader;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AgentDefinitionReaderOS implements AgentDefinitionReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  public AgentDefinitionReaderOS(
      final OptimizeOpenSearchClient osClient, final ConfigurationService configurationService) {
    this.osClient = osClient;
    this.configurationService = configurationService;
  }

  @Override
  public List<AgentDefinitionDto> getAgentDefinitions(
      final Map<String, Set<String>> tenantIdsByProcessDefinitionKey) {
    if (tenantIdsByProcessDefinitionKey.isEmpty()) {
      return List.of();
    }
    final List<Query> definitionQueries = new ArrayList<>();
    tenantIdsByProcessDefinitionKey.forEach(
        (processDefinitionKey, tenantIds) ->
            definitionQueries.add(
                QueryDSL.and(
                    QueryDSL.term(
                        AgentDefinitionIndex.PROCESS_DEFINITION_KEY, processDefinitionKey),
                    DefinitionQueryUtilOS.createTenantIdQuery(
                        AgentDefinitionIndex.TENANT_ID, new ArrayList<>(tenantIds)))));
    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(AGENT_DEFINITION_INDEX_NAME)
            .size(LIST_FETCH_LIMIT)
            .query(QueryDSL.or("1", definitionQueries.toArray(Query[]::new)))
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));
    final OpenSearchDocumentOperations.AggregatedResult<Hit<AgentDefinitionDto>> response;
    try {
      response = osClient.retrieveAllScrollResults(searchRequest, AgentDefinitionDto.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve agent definitions!", e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(response);
  }
}
