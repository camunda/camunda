/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.agentrun;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_RUN_AGENT;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_RUN_MODEL;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_RUN_TOOL;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Groups agent runs by agent, by model or by tool. Model and tool group over the nested per-model
 * token and per-tool call entries, so a run that used two models or several tools contributes to
 * each of them; the view's aggregations then run inside those nested entries.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByAgentRunTermsInterpreterES extends AbstractProcessGroupByInterpreterES {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessGroupByAgentRunTermsInterpreterES.class);
  private static final String NESTED_AGGREGATION = "agentRunNested";
  private static final String TERMS_AGGREGATION = "agentRunTerms";
  private static final String LABEL_AGGREGATION = "agentRunLabel";

  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient esClient;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessGroupByAgentRunTermsInterpreterES(
      final ConfigurationService configurationService,
      final OptimizeElasticsearchClient esClient,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
    this.configurationService = configurationService;
    this.esClient = esClient;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(
        PROCESS_GROUP_BY_AGENT_RUN_AGENT,
        PROCESS_GROUP_BY_AGENT_RUN_MODEL,
        PROCESS_GROUP_BY_AGENT_RUN_TOOL);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Grouping grouping = Grouping.of(context.getPlan().getGroupBy());
    final int size =
        configurationService.getElasticSearchConfiguration().getAggregationBucketLimit();
    final Aggregation.Builder.ContainerBuilder terms =
        new Aggregation.Builder().terms(t -> t.field(grouping.keyField()).size(size));
    if (grouping.labelField() != null) {
      terms.aggregations(
          LABEL_AGGREGATION,
          Aggregation.of(a -> a.terms(t -> t.field(grouping.labelField()).size(1))));
    }
    distributedByInterpreter
        .createAggregations(context, boolQuery)
        .forEach((key, value) -> terms.aggregations(key, value.build()));
    if (grouping.nestedPath() == null) {
      return Map.of(TERMS_AGGREGATION, terms);
    }
    return Map.of(
        NESTED_AGGREGATION,
        new Aggregation.Builder()
            .nested(n -> n.path(grouping.nestedPath()))
            .aggregations(TERMS_AGGREGATION, terms.build()));
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Grouping grouping = Grouping.of(context.getPlan().getGroupBy());
    final List<StringTermsBucket> buckets = extractBuckets(response, grouping);
    final Map<String, String> agentNames =
        grouping == Grouping.AGENT
            ? fetchAgentNames(buckets.stream().map(b -> b.key().stringValue()).toList())
            : Map.of();
    final List<GroupByResult> groups = new ArrayList<>();
    for (final StringTermsBucket bucket : buckets) {
      final String key = bucket.key().stringValue();
      groups.add(
          GroupByResult.createGroupByResult(
              key,
              label(grouping, key, bucket, agentNames),
              distributedByInterpreter.retrieveResult(response, bucket.aggregations(), context)));
    }
    compositeCommandResult.setGroups(groups);
  }

  private static List<StringTermsBucket> extractBuckets(
      final ResponseBody<?> response, final Grouping grouping) {
    if (response.aggregations() == null || response.aggregations().isEmpty()) {
      return List.of();
    }
    final Optional<Aggregate> terms =
        grouping.nestedPath() == null
            ? Optional.ofNullable(response.aggregations().get(TERMS_AGGREGATION))
            : Optional.ofNullable(response.aggregations().get(NESTED_AGGREGATION))
                .map(nested -> nested.nested().aggregations().get(TERMS_AGGREGATION));
    return terms.map(t -> t.sterms().buckets().array()).orElseGet(List::of);
  }

  private static String label(
      final Grouping grouping,
      final String key,
      final StringTermsBucket bucket,
      final Map<String, String> agentNames) {
    return switch (grouping) {
      case AGENT -> agentNames.getOrDefault(key, key);
      case MODEL -> firstLabel(bucket).map(provider -> key + " (" + provider + ")").orElse(key);
      case TOOL -> firstLabel(bucket).orElse(key);
    };
  }

  private static Optional<String> firstLabel(final StringTermsBucket bucket) {
    return Optional.ofNullable(bucket.aggregations().get(LABEL_AGGREGATION))
        .flatMap(a -> a.sterms().buckets().array().stream().findFirst())
        .map(b -> b.key().stringValue());
  }

  private Map<String, String> fetchAgentNames(final List<String> agentIds) {
    if (agentIds.isEmpty()) {
      return Map.of();
    }
    try {
      final var response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, AGENT_DEFINITION_INDEX_NAME)
                          .query(
                              q ->
                                  q.terms(
                                      t ->
                                          t.field(AgentDefinitionIndex.AGENT_ID)
                                              .terms(
                                                  tv ->
                                                      tv.value(
                                                          agentIds.stream()
                                                              .map(FieldValue::of)
                                                              .toList()))))
                          .size(agentIds.size() * 10)),
              AgentDefinitionDto.class);
      final Map<String, String> names = new HashMap<>();
      response.hits().hits().stream()
          .map(Hit::source)
          .filter(Objects::nonNull)
          .filter(definition -> definition.getName() != null)
          .forEach(definition -> names.putIfAbsent(definition.getAgentId(), definition.getName()));
      return names;
    } catch (final IOException | RuntimeException e) {
      LOG.warn("Could not resolve agent names; falling back to agent IDs", e);
      return Map.of();
    }
  }

  @Override
  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
  }

  private enum Grouping {
    AGENT(null, AgentRunIndex.AGENT_ID, null),
    MODEL(
        AgentRunIndex.TOKENS_BY_MODEL,
        AgentRunIndex.TOKENS_BY_MODEL + "." + AgentRunDto.ModelTokensDto.Fields.model,
        AgentRunIndex.TOKENS_BY_MODEL + "." + AgentRunDto.ModelTokensDto.Fields.provider),
    TOOL(
        AgentRunIndex.TOOL_CALLS_BY_TOOL,
        AgentRunIndex.TOOL_CALLS_BY_TOOL + "." + AgentRunIndex.TOOL_CALLS_TOOL_FLOW_NODE_ID,
        AgentRunIndex.TOOL_CALLS_BY_TOOL + "." + AgentRunIndex.TOOL_CALLS_TOOL_NAME);

    private final String nestedPath;
    private final String keyField;
    private final String labelField;

    Grouping(final String nestedPath, final String keyField, final String labelField) {
      this.nestedPath = nestedPath;
      this.keyField = keyField;
      this.labelField = labelField;
    }

    static Grouping of(final ProcessGroupBy groupBy) {
      return switch (groupBy) {
        case PROCESS_GROUP_BY_AGENT_RUN_AGENT -> AGENT;
        case PROCESS_GROUP_BY_AGENT_RUN_MODEL -> MODEL;
        case PROCESS_GROUP_BY_AGENT_RUN_TOOL -> TOOL;
        default -> throw new IllegalStateException("Unsupported agent run group-by " + groupBy);
      };
    }

    String nestedPath() {
      return nestedPath;
    }

    String keyField() {
      return keyField;
    }

    String labelField() {
      return labelField;
    }
  }
}
