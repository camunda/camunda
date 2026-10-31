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
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.FAILED;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.INCIDENT_REASONS;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.LIMITS;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.SUCCEEDED;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.finite;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.firstKey;
import static io.camunda.optimize.service.db.es.reader.AgentRunQueriesES.rate;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto.FailureReason;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto.LimitHit;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto.ModelUsage;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto.RootProcessUsage;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentDetailDto.ToolUsage;
import io.camunda.optimize.dto.optimize.rest.agentic.ProcessFailuresDto.AgentFailures;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.AgentDetailReader;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Reads the single agent view and the process failures from the agent run index, one search each,
 * plus the agent definitions for names and types.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class AgentDetailReaderES implements AgentDetailReader {

  private static final int MAX_AGENTS = 1_000;
  private static final int MAX_TOOLS = 100;
  private static final int MAX_MODELS = 20;
  private static final int MAX_ROOT_PROCESSES = 50;
  private static final String LATEST = "latest";
  private static final String MODEL_CALLS = "modelCalls";
  private static final String TOOLS = "tools";
  private static final String TOOL_INCIDENTS = "toolIncidents";
  private static final String BY_TOOL = "byTool";
  private static final String COUNT = "count";
  private static final String NAME = "name";
  private static final String MODELS = "models";
  private static final String PROVIDER = "provider";
  private static final String TOKENS = "tokens";
  private static final String DURATION = "duration";
  private static final String WHERE_USED = "whereUsed";
  private static final String AGENTS = "agents";

  private final OptimizeElasticsearchClient esClient;

  public AgentDetailReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public Optional<AgentDetailDto> getAgentDetail(
      final String agentId, final OffsetDateTime endedFrom) {
    try {
      final Optional<AgentDefinitionDto> definition =
          fetchDefinitions(AgentDefinitionIndex.AGENT_ID, agentId).values().stream().findFirst();
      final Map<String, Aggregate> aggs = fetchAgentRuns(agentId, endedFrom);
      final long runs =
          aggs.get(FAILED).filter().docCount() + aggs.get(SUCCEEDED).filter().docCount();
      if (definition.isEmpty() && runs == 0) {
        return Optional.empty();
      }
      final Map<String, Object> latest = latestRun(aggs.get(LATEST));
      final List<ToolUsage> tools = tools(aggs, runs);
      return Optional.of(
          new AgentDetailDto(
              agentId,
              definition.map(AgentDetailReaderES::name).orElse(agentId),
              definition.map(AgentDefinitionDto::getAgentType).orElse(null),
              definition
                  .map(AgentDefinitionDto::getProcessDefinitionKey)
                  .orElse(string(latest, AgentRunIndex.PROCESS_DEFINITION_KEY)),
              null,
              string(latest, AgentRunIndex.PROCESS_DEFINITION_VERSION),
              Optional.ofNullable(string(latest, AgentRunIndex.MODEL))
                  .orElse(definition.map(AgentDefinitionDto::getModel).orElse(null)),
              string(latest, AgentRunIndex.PROVIDER),
              Optional.ofNullable(limit(latest, AgentRunIndex.MAX_MODEL_CALLS))
                  .orElse(definition.map(AgentDefinitionDto::getMaxModelCalls).orElse(null)),
              limit(latest, AgentRunIndex.MAX_TOOL_CALLS),
              limit(latest, AgentRunIndex.MAX_TOKENS),
              runs,
              aggs.get(FAILED).filter().docCount(),
              runs == 0 ? null : finite(aggs.get(MODEL_CALLS).avg().value()),
              aggs.get(LIMITS).sterms().buckets().array().stream()
                  .map(bucket -> new LimitHit(bucket.key().stringValue(), bucket.docCount()))
                  .toList(),
              AgentRunQueriesES.failureReasons(aggs),
              tools,
              !tools.isEmpty(),
              models(aggs.get(MODELS)),
              whereUsed(aggs.get(WHERE_USED))));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not load agent " + agentId, e);
    }
  }

  @Override
  public List<AgentFailures> getProcessFailures(
      final String processDefinitionKey,
      final OffsetDateTime endedFrom,
      final int maxReasonsPerAgent) {
    try {
      final Map<String, AgentDefinitionDto> definitions =
          fetchDefinitions(AgentDefinitionIndex.PROCESS_DEFINITION_KEY, processDefinitionKey);
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
                                          FAILED,
                                          f ->
                                              f.filter(AgentRunQueriesES.failedRuns().filter())
                                                  .aggregations(LIMITS, AgentRunQueriesES.limits())
                                                  .aggregations(
                                                      INCIDENT_REASONS,
                                                      AgentRunQueriesES.incidentReasons())))),
              Object.class);
      final List<AgentFailures> failures = new ArrayList<>();
      for (final StringTermsBucket agent :
          response.aggregations().get(AGENTS).sterms().buckets().array()) {
        final var failed = agent.aggregations().get(FAILED).filter();
        if (failed.docCount() == 0) {
          continue;
        }
        final String agentId = agent.key().stringValue();
        final List<FailureReason> reasons = AgentRunQueriesES.failureReasons(failed.aggregations());
        failures.add(
            new AgentFailures(
                agentId,
                Optional.ofNullable(definitions.get(agentId))
                    .map(AgentDetailReaderES::name)
                    .orElse(agentId),
                agent.docCount(),
                failed.docCount(),
                rate(agent.docCount() - failed.docCount(), agent.docCount()),
                reasons.subList(0, Math.min(maxReasonsPerAgent, reasons.size()))));
      }
      failures.sort(Comparator.comparingLong(AgentFailures::failedRuns).reversed());
      return failures;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Could not load agent failures of process " + processDefinitionKey, e);
    }
  }

  private Map<String, Aggregate> fetchAgentRuns(
      final String agentId, final OffsetDateTime endedFrom) throws IOException {
    final var response =
        esClient.search(
            OptimizeSearchRequestBuilderES.of(
                b ->
                    b.optimizeIndex(esClient, AGENT_INSTANCE_INDEX_NAME)
                        .query(
                            q -> q.bool(AgentRunQueriesES.completedRuns(endedFrom, null, agentId)))
                        .size(0)
                        .aggregations(
                            LATEST,
                            a ->
                                a.topHits(
                                    t ->
                                        t.size(1)
                                            .sort(
                                                s ->
                                                    s.field(
                                                        f ->
                                                            f.field(AgentRunIndex.START_DATE)
                                                                .order(SortOrder.Desc)))
                                            .source(
                                                s ->
                                                    s.filter(
                                                        f ->
                                                            f.includes(
                                                                AgentRunIndex
                                                                    .PROCESS_DEFINITION_KEY,
                                                                AgentRunIndex
                                                                    .PROCESS_DEFINITION_VERSION,
                                                                AgentRunIndex.MODEL,
                                                                AgentRunIndex.PROVIDER,
                                                                AgentRunIndex.MAX_MODEL_CALLS,
                                                                AgentRunIndex.MAX_TOOL_CALLS,
                                                                AgentRunIndex.MAX_TOKENS)))))
                        .aggregations(FAILED, AgentRunQueriesES.failedRuns())
                        .aggregations(SUCCEEDED, AgentRunQueriesES.succeededRuns())
                        .aggregations(
                            MODEL_CALLS, a -> a.avg(v -> v.field(AgentRunIndex.MODEL_CALLS)))
                        .aggregations(LIMITS, AgentRunQueriesES.limits())
                        .aggregations(INCIDENT_REASONS, AgentRunQueriesES.incidentReasons())
                        .aggregations(
                            TOOLS,
                            a ->
                                a.nested(n -> n.path(AgentRunIndex.TOOL_CALLS_BY_TOOL))
                                    .aggregations(
                                        BY_TOOL, byTool(AgentRunIndex.TOOL_CALLS_BY_TOOL)))
                        .aggregations(
                            TOOL_INCIDENTS,
                            a ->
                                a.nested(n -> n.path(AgentRunIndex.TOOL_INCIDENTS_BY_TOOL))
                                    .aggregations(
                                        BY_TOOL, byTool(AgentRunIndex.TOOL_INCIDENTS_BY_TOOL)))
                        .aggregations(
                            MODELS,
                            a ->
                                a.terms(t -> t.field(AgentRunIndex.MODEL).size(MAX_MODELS))
                                    .aggregations(
                                        PROVIDER,
                                        p -> p.terms(t -> t.field(AgentRunIndex.PROVIDER).size(1)))
                                    .aggregations(
                                        TOKENS,
                                        s -> s.avg(v -> v.field(AgentRunIndex.TOTAL_TOKENS)))
                                    .aggregations(
                                        DURATION,
                                        s -> s.avg(v -> v.field(AgentRunIndex.ACTIVE_DURATION_MS)))
                                    .aggregations(SUCCEEDED, AgentRunQueriesES.succeededRuns()))
                        .aggregations(
                            WHERE_USED,
                            a ->
                                a.terms(
                                        t ->
                                            t.field(AgentRunIndex.ROOT_PROCESS_DEFINITION_KEY)
                                                .size(MAX_ROOT_PROCESSES))
                                    .aggregations(
                                        TOKENS,
                                        s -> s.sum(v -> v.field(AgentRunIndex.TOTAL_TOKENS))))),
            Object.class);
    return response.aggregations();
  }

  private static co.elastic.clients.elasticsearch._types.aggregations.Aggregation byTool(
      final String path) {
    return co.elastic.clients.elasticsearch._types.aggregations.Aggregation.of(
        a ->
            a.terms(
                    t ->
                        t.field(path + "." + AgentRunIndex.TOOL_CALLS_TOOL_FLOW_NODE_ID)
                            .size(MAX_TOOLS))
                .aggregations(
                    COUNT, s -> s.sum(v -> v.field(path + "." + AgentRunIndex.TOOL_CALLS_COUNT)))
                .aggregations(
                    NAME,
                    s ->
                        s.terms(
                            t ->
                                t.field(path + "." + AgentRunIndex.TOOL_CALLS_TOOL_NAME).size(1))));
  }

  /** Calls per tool element, with the incidents on it; tools never called are left out. */
  private static List<ToolUsage> tools(final Map<String, Aggregate> aggs, final long runs) {
    final Map<String, Long> incidents = new HashMap<>();
    aggs.get(TOOL_INCIDENTS)
        .nested()
        .aggregations()
        .get(BY_TOOL)
        .sterms()
        .buckets()
        .array()
        .forEach(
            bucket ->
                incidents.put(
                    bucket.key().stringValue(),
                    (long) bucket.aggregations().get(COUNT).sum().value()));
    final List<ToolUsage> tools = new ArrayList<>();
    for (final StringTermsBucket bucket :
        aggs.get(TOOLS).nested().aggregations().get(BY_TOOL).sterms().buckets().array()) {
      final String toolFlowNodeId = bucket.key().stringValue();
      final long calls = (long) bucket.aggregations().get(COUNT).sum().value();
      final long toolIncidents = incidents.getOrDefault(toolFlowNodeId, 0L);
      tools.add(
          new ToolUsage(
              toolFlowNodeId,
              Optional.ofNullable(firstKey(bucket.aggregations().get(NAME))).orElse(toolFlowNodeId),
              calls,
              runs == 0 ? null : (double) calls / runs,
              toolIncidents,
              calls == 0 ? null : rate(toolIncidents, calls)));
    }
    tools.sort(Comparator.comparingLong(ToolUsage::calls).reversed());
    return tools;
  }

  private static List<ModelUsage> models(final Aggregate models) {
    return models.sterms().buckets().array().stream()
        .map(
            bucket ->
                new ModelUsage(
                    bucket.key().stringValue(),
                    firstKey(bucket.aggregations().get(PROVIDER)),
                    bucket.docCount(),
                    finite(bucket.aggregations().get(TOKENS).avg().value()),
                    finite(bucket.aggregations().get(DURATION).avg().value()),
                    rate(
                        bucket.aggregations().get(SUCCEEDED).filter().docCount(),
                        bucket.docCount())))
        .toList();
  }

  private static List<RootProcessUsage> whereUsed(final Aggregate roots) {
    return roots.sterms().buckets().array().stream()
        .map(
            bucket ->
                new RootProcessUsage(
                    bucket.key().stringValue(),
                    null,
                    bucket.docCount(),
                    finite(bucket.aggregations().get(TOKENS).sum().value())))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> latestRun(final Aggregate topHits) {
    return topHits.topHits().hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .map(source -> (Map<String, Object>) source.to(Map.class))
        .findFirst()
        .orElse(Map.of());
  }

  private static String string(final Map<String, Object> source, final String field) {
    final Object value = source.get(field);
    return value == null ? null : value.toString();
  }

  /** A limit of 0 or below means the limit is not set. */
  private static Long limit(final Map<String, Object> source, final String field) {
    return source.get(field) instanceof final Number value && value.longValue() > 0
        ? value.longValue()
        : null;
  }

  private Map<String, AgentDefinitionDto> fetchDefinitions(final String field, final String value)
      throws IOException {
    final var response =
        esClient.search(
            OptimizeSearchRequestBuilderES.of(
                b ->
                    b.optimizeIndex(esClient, AGENT_DEFINITION_INDEX_NAME)
                        .query(Query.of(q -> q.term(t -> t.field(field).value(value))))
                        .size(MAX_AGENTS)),
            AgentDefinitionDto.class);
    final Map<String, AgentDefinitionDto> byAgentId = new LinkedHashMap<>();
    response.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .forEach(definition -> byAgentId.putIfAbsent(definition.getAgentId(), definition));
    return byAgentId;
  }

  private static String name(final AgentDefinitionDto definition) {
    return definition.getName() != null ? definition.getName() : definition.getFlowNodeId();
  }
}
