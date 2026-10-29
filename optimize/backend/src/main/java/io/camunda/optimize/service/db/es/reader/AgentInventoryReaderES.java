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
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.finite;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.keys;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.percentile;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.rate;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto.AgentInventoryEntry;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.AgentInventoryReader;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Reads the agent inventory in two searches: agent definitions for name, type and configured
 * limits, and one terms aggregation per agent over its runs. Runs are restricted the same way as
 * the dashboard tiles: completed process instances, by the run's process instance end date.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class AgentInventoryReaderES implements AgentInventoryReader {

  private static final int MAX_AGENTS = 1_000;
  private static final int MAX_MODELS_PER_AGENT = 10;
  private static final String AGENTS = "agents";
  private static final String INCIDENTS = "incidents";
  private static final String LIMIT_HITS = "limitHits";
  private static final String DURATION = "duration";
  private static final String DURATION_AVG = "durationAvg";
  private static final String TOKENS = "tokens";
  private static final String TOKENS_P50 = "tokensP50";
  private static final String TOKENS_TOTAL = "tokensTotal";
  private static final String HUMAN_STEPS = "humanSteps";
  private static final String LAST_RUN = "lastRun";
  private static final String MODELS = "models";
  private static final String PROCESS = "process";

  private final OptimizeElasticsearchClient esClient;

  public AgentInventoryReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public List<AgentInventoryEntry> getAgentInventory(
      final OffsetDateTime endedFrom, final String processDefinitionKey) {
    try {
      final Map<String, AgentDefinitionDto> definitions = fetchDefinitions(processDefinitionKey);
      final Map<String, StringTermsBucket> runsByAgent =
          fetchRunsByAgent(endedFrom, processDefinitionKey);
      final List<AgentInventoryEntry> entries = new ArrayList<>();
      definitions.forEach(
          (agentId, definition) ->
              entries.add(toEntry(agentId, definition, runsByAgent.get(agentId))));
      // Runs whose definition was not imported (yet) still belong in the inventory.
      runsByAgent.forEach(
          (agentId, bucket) -> {
            if (!definitions.containsKey(agentId)) {
              entries.add(toEntry(agentId, null, bucket));
            }
          });
      return entries;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not load the agent inventory", e);
    }
  }

  private Map<String, AgentDefinitionDto> fetchDefinitions(final String processDefinitionKey)
      throws IOException {
    final Query query =
        processDefinitionKey == null
            ? Query.of(q -> q.matchAll(m -> m))
            : Query.of(
                q ->
                    q.term(
                        t ->
                            t.field(AgentDefinitionIndex.PROCESS_DEFINITION_KEY)
                                .value(processDefinitionKey)));
    final var response =
        esClient.search(
            OptimizeSearchRequestBuilderES.of(
                b ->
                    b.optimizeIndex(esClient, AGENT_DEFINITION_INDEX_NAME)
                        .query(query)
                        .size(MAX_AGENTS)),
            AgentDefinitionDto.class);
    // One agent can exist in several tenants; the inventory lists the tenant-independent agentId.
    final Map<String, AgentDefinitionDto> byAgentId = new LinkedHashMap<>();
    response.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .forEach(definition -> byAgentId.putIfAbsent(definition.getAgentId(), definition));
    return byAgentId;
  }

  private Map<String, StringTermsBucket> fetchRunsByAgent(
      final OffsetDateTime endedFrom, final String processDefinitionKey) throws IOException {
    final var response =
        esClient.search(
            OptimizeSearchRequestBuilderES.of(
                b ->
                    b.optimizeIndex(esClient, AGENT_INSTANCE_INDEX_NAME)
                        .query(
                            q ->
                                q.bool(
                                    AgentRunQueriesES.completedRuns(
                                        endedFrom, processDefinitionKey, null)))
                        .size(0)
                        .aggregations(
                            AGENTS,
                            a ->
                                a.terms(t -> t.field(AgentRunIndex.AGENT_ID).size(MAX_AGENTS))
                                    .aggregations(
                                        INCIDENTS,
                                        s ->
                                            s.filter(
                                                f ->
                                                    f.term(
                                                        t ->
                                                            t.field(AgentRunIndex.HAS_INCIDENT)
                                                                .value(true))))
                                    .aggregations(
                                        LIMIT_HITS,
                                        s ->
                                            s.filter(
                                                f ->
                                                    f.term(
                                                        t ->
                                                            t.field(AgentRunIndex.HIT_LIMIT)
                                                                .value(true))))
                                    .aggregations(
                                        AgentRunQueriesES.SUCCEEDED,
                                        AgentRunQueriesES.succeededRuns())
                                    .aggregations(
                                        DURATION,
                                        s ->
                                            s.percentiles(
                                                p ->
                                                    p.field(AgentRunIndex.ACTIVE_DURATION_MS)
                                                        .percents(50.0, 95.0)))
                                    .aggregations(
                                        DURATION_AVG,
                                        s -> s.avg(v -> v.field(AgentRunIndex.ACTIVE_DURATION_MS)))
                                    .aggregations(
                                        TOKENS,
                                        s -> s.avg(v -> v.field(AgentRunIndex.TOTAL_TOKENS)))
                                    .aggregations(
                                        TOKENS_P50,
                                        s ->
                                            s.percentiles(
                                                p ->
                                                    p.field(AgentRunIndex.TOTAL_TOKENS)
                                                        .percents(50.0)))
                                    .aggregations(
                                        TOKENS_TOTAL,
                                        s -> s.sum(v -> v.field(AgentRunIndex.TOTAL_TOKENS)))
                                    .aggregations(
                                        HUMAN_STEPS,
                                        s -> s.avg(v -> v.field(AgentRunIndex.HUMAN_STEP_COUNT)))
                                    .aggregations(
                                        LAST_RUN,
                                        s -> s.max(v -> v.field(AgentRunIndex.START_DATE)))
                                    .aggregations(
                                        MODELS,
                                        s ->
                                            s.terms(
                                                t ->
                                                    t.field(AgentRunIndex.MODEL)
                                                        .size(MAX_MODELS_PER_AGENT)))
                                    .aggregations(
                                        PROCESS,
                                        s ->
                                            s.terms(
                                                t ->
                                                    t.field(AgentRunIndex.PROCESS_DEFINITION_KEY)
                                                        .size(1))))),
            Object.class);
    final Map<String, StringTermsBucket> byAgentId = new LinkedHashMap<>();
    response
        .aggregations()
        .get(AGENTS)
        .sterms()
        .buckets()
        .array()
        .forEach(bucket -> byAgentId.put(bucket.key().stringValue(), bucket));
    return byAgentId;
  }

  private static AgentInventoryEntry toEntry(
      final String agentId, final AgentDefinitionDto definition, final StringTermsBucket runs) {
    final long runCount = runs == null ? 0 : runs.docCount();
    final Map<String, Aggregate> aggs = runs == null ? Map.of() : runs.aggregations();
    final List<String> models = keys(aggs.get(MODELS));
    final List<String> runModels =
        models.isEmpty() && definition != null && definition.getModel() != null
            ? List.of(definition.getModel())
            : models;
    final boolean hasRuns = runCount > 0;
    return new AgentInventoryEntry(
        agentId,
        definition != null && definition.getName() != null
            ? definition.getName()
            : Optional.ofNullable(definition)
                .map(AgentDefinitionDto::getFlowNodeId)
                .orElse(agentId),
        definition == null ? null : definition.getAgentType(),
        definition != null
            ? definition.getProcessDefinitionKey()
            : keys(aggs.get(PROCESS)).stream().findFirst().orElse(null),
        null,
        runModels,
        definition == null ? null : definition.getMaxModelCalls(),
        runCount,
        hasRuns ? rate(aggs.get(INCIDENTS).filter().docCount(), runCount) : null,
        hasRuns ? rate(aggs.get(LIMIT_HITS).filter().docCount(), runCount) : null,
        hasRuns ? percentile(aggs.get(DURATION), 50.0) : null,
        hasRuns ? finite(aggs.get(TOKENS).avg().value()) : null,
        hasRuns ? lastRun(aggs.get(LAST_RUN)) : null,
        hasRuns ? finite(aggs.get(TOKENS_TOTAL).sum().value()) : null,
        hasRuns ? percentile(aggs.get(TOKENS_P50), 50.0) : null,
        hasRuns ? rate(aggs.get(AgentRunQueriesES.SUCCEEDED).filter().docCount(), runCount) : null,
        hasRuns ? finite(aggs.get(DURATION_AVG).avg().value()) : null,
        hasRuns ? percentile(aggs.get(DURATION), 95.0) : null,
        hasRuns ? finite(aggs.get(HUMAN_STEPS).avg().value()) : null);
  }

  private static OffsetDateTime lastRun(final Aggregate max) {
    final Double millis = finite(max.max().value());
    return millis == null
        ? null
        : OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis.longValue()), ZoneOffset.UTC);
  }
}
