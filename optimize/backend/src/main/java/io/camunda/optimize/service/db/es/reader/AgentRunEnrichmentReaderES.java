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
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.AgentRunEnrichmentReader;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AgentRunEnrichmentReaderES implements AgentRunEnrichmentReader {

  static final List<String> ENRICHMENT_SOURCE_FIELDS =
      List.of(
          ProcessInstanceIndex.PROCESS_DEFINITION_KEY,
          ProcessInstanceIndex.STATE,
          ProcessInstanceIndex.END_DATE,
          flowNodeField(ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID),
          flowNodeField(ProcessInstanceIndex.FLOW_NODE_ID),
          flowNodeField(ProcessInstanceIndex.FLOW_NODE_TYPE),
          flowNodeField(ProcessInstanceIndex.FLOW_NODE_START_DATE),
          flowNodeField(ProcessInstanceIndex.FLOW_NODE_END_DATE),
          flowNodeField(ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION),
          incidentField(ProcessInstanceIndex.INCIDENT_ACTIVITY_ID),
          incidentField(ProcessInstanceIndex.INCIDENT_INCIDENT_TYPE),
          incidentField(ProcessInstanceIndex.INCIDENT_MESSAGE),
          incidentField(ProcessInstanceIndex.INCIDENT_CREATE_TIME));
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AgentRunEnrichmentReaderES.class);
  // A process instance rarely has more than a handful of agent runs; this bounds one lookup.
  private static final int MAX_RUNS_PER_SEARCH = 10_000;
  private static final int MAX_AGENTIC_PROCESSES = 10_000;
  private static final String AGENTIC_PROCESSES = "agenticProcesses";

  private final OptimizeElasticsearchClient esClient;

  public AgentRunEnrichmentReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public List<AgentRunDto> getRunsByIds(final Collection<String> agentInstanceIds) {
    if (agentInstanceIds.isEmpty()) {
      return List.of();
    }
    final String index =
        DatabaseClient.convertToPrefixedAliasName(AGENT_INSTANCE_INDEX_NAME, esClient);
    final List<MultiGetOperation> docs =
        agentInstanceIds.stream()
            .map(id -> MultiGetOperation.of(m -> m.index(index).id(id)))
            .toList();
    return multiGet(docs, AgentRunDto.class).values().stream().toList();
  }

  @Override
  public List<AgentRunDto> getCompletedRunsByProcessInstanceIds(
      final Collection<String> processInstanceIds) {
    if (processInstanceIds.isEmpty()) {
      return List.of();
    }
    final SearchRequest request =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, AGENT_INSTANCE_INDEX_NAME)
                    .query(
                        q ->
                            q.bool(
                                bq ->
                                    bq.filter(
                                            f ->
                                                f.terms(
                                                    t ->
                                                        t.field(AgentRunIndex.PROCESS_INSTANCE_ID)
                                                            .terms(
                                                                tv ->
                                                                    tv.value(
                                                                        processInstanceIds.stream()
                                                                            .map(FieldValue::of)
                                                                            .toList()))))
                                        .filter(
                                            f -> f.exists(e -> e.field(AgentRunIndex.END_DATE)))))
                    .size(MAX_RUNS_PER_SEARCH));
    try {
      final var response = esClient.search(request, AgentRunDto.class);
      if (response.hits().total() != null
          && response.hits().total().value() > MAX_RUNS_PER_SEARCH) {
        LOG.warn(
            "Found more than {} agent runs for {} process instances; only the first {} are enriched.",
            MAX_RUNS_PER_SEARCH,
            processInstanceIds.size(),
            MAX_RUNS_PER_SEARCH);
      }
      return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not search agent runs by process instance", e);
    }
  }

  @Override
  public Set<String> getAgenticProcessDefinitionKeys() {
    try {
      final var response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, AGENT_DEFINITION_INDEX_NAME)
                          .size(0)
                          .aggregations(
                              AGENTIC_PROCESSES,
                              a ->
                                  a.terms(
                                      t ->
                                          t.field(AgentDefinitionIndex.PROCESS_DEFINITION_KEY)
                                              .size(MAX_AGENTIC_PROCESSES)))),
              Object.class);
      return response.aggregations().get(AGENTIC_PROCESSES).sterms().buckets().array().stream()
          .map(bucket -> bucket.key().stringValue())
          .collect(Collectors.toSet());
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read the agentic processes", e);
    }
  }

  @Override
  public Map<String, ProcessInstanceDto> getProcessInstances(
      final Map<String, String> processDefinitionKeyByProcessInstanceId) {
    if (processDefinitionKeyByProcessInstanceId.isEmpty()) {
      return Map.of();
    }
    final List<MultiGetOperation> docs =
        processDefinitionKeyByProcessInstanceId.entrySet().stream()
            .map(
                entry -> {
                  final String index =
                      DatabaseClient.convertToPrefixedAliasName(
                          getProcessInstanceIndexAliasName(entry.getValue()), esClient);
                  return processInstanceGet(index, entry.getKey());
                })
            .toList();
    return multiGet(docs, ProcessInstanceDto.class);
  }

  /**
   * Reads only what enrichment uses. A long-running agentic process instance holds every flow node
   * instance, incident and user task it ever had, most of which the enrichment never looks at.
   */
  static MultiGetOperation processInstanceGet(final String index, final String processInstanceId) {
    return MultiGetOperation.of(
        m ->
            m.index(index)
                .id(processInstanceId)
                .source(s -> s.filter(f -> f.includes(ENRICHMENT_SOURCE_FIELDS))));
  }

  @Override
  public Map<String, String> getProcessDefinitionKeys(final Collection<String> processInstanceIds) {
    if (processInstanceIds.isEmpty()) {
      return Map.of();
    }
    final SearchRequest request =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, PROCESS_INSTANCE_MULTI_ALIAS)
                    .query(
                        q ->
                            q.terms(
                                t ->
                                    t.field(ProcessInstanceIndex.PROCESS_INSTANCE_ID)
                                        .terms(
                                            tv ->
                                                tv.value(
                                                    processInstanceIds.stream()
                                                        .map(FieldValue::of)
                                                        .toList()))))
                    .source(
                        s ->
                            s.filter(
                                f ->
                                    f.includes(
                                        ProcessInstanceIndex.PROCESS_INSTANCE_ID,
                                        ProcessInstanceIndex.PROCESS_DEFINITION_KEY)))
                    .size(processInstanceIds.size()));
    try {
      final Map<String, String> keys = new HashMap<>();
      esClient.search(request, ProcessInstanceDto.class).hits().hits().stream()
          .map(Hit::source)
          .filter(Objects::nonNull)
          .forEach(
              instance ->
                  keys.put(instance.getProcessInstanceId(), instance.getProcessDefinitionKey()));
      return keys;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not look up root process instances", e);
    }
  }

  @Override
  public Map<String, String> getAgentTypes(final Collection<String> agentDefinitionDocumentIds) {
    if (agentDefinitionDocumentIds.isEmpty()) {
      return Map.of();
    }
    final String index =
        DatabaseClient.convertToPrefixedAliasName(AGENT_DEFINITION_INDEX_NAME, esClient);
    final List<MultiGetOperation> docs =
        agentDefinitionDocumentIds.stream()
            .distinct()
            .map(
                id ->
                    MultiGetOperation.of(
                        m ->
                            m.index(index)
                                .id(id)
                                .source(
                                    s ->
                                        s.filter(
                                            f -> f.includes(AgentDefinitionIndex.AGENT_TYPE)))))
            .toList();
    final Map<String, String> types = new HashMap<>();
    multiGet(docs, AgentDefinitionDto.class)
        .forEach(
            (id, definition) -> {
              if (definition.getAgentType() != null) {
                types.put(id, definition.getAgentType());
              }
            });
    return types;
  }

  private static String flowNodeField(final String field) {
    return ProcessInstanceIndex.FLOW_NODE_INSTANCES + "." + field;
  }

  private static String incidentField(final String field) {
    return ProcessInstanceIndex.INCIDENTS + "." + field;
  }

  private <T> Map<String, T> multiGet(final List<MultiGetOperation> docs, final Class<T> type) {
    try {
      final var response = esClient.mget(MgetRequest.of(r -> r.docs(docs)), type);
      final Map<String, T> found = new HashMap<>();
      for (final MultiGetResponseItem<T> item : response.docs()) {
        // A failure here is a missing index (e.g. no process instance imported yet for the
        // definition), which enrichment treats like a missing document.
        if (item.isResult() && item.result().found() && item.result().source() != null) {
          found.put(item.result().id(), item.result().source());
        }
      }
      return found;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not fetch documents for agent run enrichment", e);
    }
  }
}
