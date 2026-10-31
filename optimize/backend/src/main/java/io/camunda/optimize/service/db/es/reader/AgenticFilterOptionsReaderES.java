/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.agentic.AgenticFilterOptionsDto;
import io.camunda.optimize.dto.optimize.rest.agentic.AgenticFilterOptionsDto.AgentOption;
import io.camunda.optimize.dto.optimize.rest.agentic.AgenticFilterOptionsDto.ModelOption;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.AgenticFilterOptionsReader;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AgenticFilterOptionsReaderES implements AgenticFilterOptionsReader {

  private static final int MAX_OPTIONS = 1_000;
  private static final String MODELS_AGGREGATION = "models";
  private static final String PROVIDERS_AGGREGATION = "providers";

  private final OptimizeElasticsearchClient esClient;

  public AgenticFilterOptionsReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public AgenticFilterOptionsDto getFilterOptions(final String processDefinitionKey) {
    try {
      return new AgenticFilterOptionsDto(
          fetchAgents(processDefinitionKey), fetchModels(processDefinitionKey));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not load agentic filter options", e);
    }
  }

  private List<AgentOption> fetchAgents(final String processDefinitionKey) throws IOException {
    final var response =
        esClient.search(
            OptimizeSearchRequestBuilderES.of(
                b ->
                    b.optimizeIndex(esClient, AGENT_DEFINITION_INDEX_NAME)
                        .query(
                            scopeTo(
                                AgentDefinitionIndex.PROCESS_DEFINITION_KEY, processDefinitionKey))
                        .size(MAX_OPTIONS)),
            AgentDefinitionDto.class);
    // One agent can exist in several tenants; the filter works on the tenant-independent agentId.
    final Map<String, AgentOption> byAgentId = new LinkedHashMap<>();
    response.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .forEach(
            definition ->
                byAgentId.putIfAbsent(
                    definition.getAgentId(),
                    new AgentOption(
                        definition.getAgentId(),
                        definition.getName() != null
                            ? definition.getName()
                            : definition.getFlowNodeId(),
                        definition.getProcessDefinitionKey())));
    return byAgentId.values().stream().sorted(Comparator.comparing(AgentOption::name)).toList();
  }

  private List<ModelOption> fetchModels(final String processDefinitionKey) throws IOException {
    final var response =
        esClient.search(
            OptimizeSearchRequestBuilderES.of(
                b ->
                    b.optimizeIndex(esClient, AGENT_INSTANCE_INDEX_NAME)
                        .query(scopeTo(AgentRunIndex.PROCESS_DEFINITION_KEY, processDefinitionKey))
                        .size(0)
                        .aggregations(
                            MODELS_AGGREGATION,
                            a ->
                                a.terms(t -> t.field(AgentRunIndex.MODEL).size(MAX_OPTIONS))
                                    .aggregations(
                                        PROVIDERS_AGGREGATION,
                                        p ->
                                            p.terms(
                                                t -> t.field(AgentRunIndex.PROVIDER).size(1))))),
            Object.class);
    return response.aggregations().get(MODELS_AGGREGATION).sterms().buckets().array().stream()
        .map(
            bucket ->
                new ModelOption(
                    bucket.key().stringValue(),
                    bucket
                        .aggregations()
                        .get(PROVIDERS_AGGREGATION)
                        .sterms()
                        .buckets()
                        .array()
                        .stream()
                        .findFirst()
                        .map(provider -> provider.key().stringValue())
                        .orElse(null)))
        .sorted(Comparator.comparing(ModelOption::model))
        .toList();
  }

  private static Query scopeTo(final String field, final String processDefinitionKey) {
    return processDefinitionKey == null || processDefinitionKey.isBlank()
        ? Query.of(q -> q.matchAll(m -> m))
        : Query.of(q -> q.term(t -> t.field(field).value(processDefinitionKey)));
  }
}
