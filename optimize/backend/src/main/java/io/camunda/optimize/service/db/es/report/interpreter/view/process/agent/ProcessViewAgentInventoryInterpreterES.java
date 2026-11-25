/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agent;

import static io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentInventoryRows.AGENTS_AGGREGATION;
import static io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentInventoryRows.LAST_END_DATE_AGGREGATION;
import static io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentInventoryRows.TOTAL_TOKENS_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_RAW_DATA;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterES;
import io.camunda.optimize.service.db.reader.AgentDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.AgentRunExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentInventoryRows;
import io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentInventoryRows.AgentRunStats;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** The agent inventory: one raw row per agent, aggregated over the agent's runs. */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewAgentInventoryInterpreterES implements ProcessViewInterpreterES {

  private final AgentDefinitionReader agentDefinitionReader;

  public ProcessViewAgentInventoryInterpreterES(final AgentDefinitionReader agentDefinitionReader) {
    this.agentDefinitionReader = agentDefinitionReader;
  }

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_AGENT_RUN_RAW_DATA);
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  // The paging applies to the agent buckets, not to the run documents, so no hits are fetched.
  @Override
  public void adjustSearchRequest(
      final SearchRequest.Builder searchRequestBuilder,
      final BoolQuery.Builder baseQueryBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    searchRequestBuilder.from(0).size(0);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Map.of(
        AGENTS_AGGREGATION,
        new Aggregation.Builder()
            .terms(
                t ->
                    t.field(AgentRunIndex.AGENT_ID)
                        .size(AgentInventoryRows.bucketCount(context))
                        .order(
                            List.of(
                                NamedValue.of("_count", SortOrder.Desc),
                                NamedValue.of("_key", SortOrder.Asc))))
            .aggregations(
                TOTAL_TOKENS_AGGREGATION, a -> a.sum(s -> s.field(AgentRunIndex.TOTAL_TOKENS)))
            .aggregations(
                LAST_END_DATE_AGGREGATION, a -> a.max(m -> m.field(AgentRunIndex.END_DATE))));
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregate agents = aggregations.get(AGENTS_AGGREGATION);
    if (agents == null) {
      return createEmptyResult(context);
    }
    final List<AgentRunStats> buckets =
        agents.sterms().buckets().array().stream()
            .map(
                bucket ->
                    new AgentRunStats(
                        bucket.key().stringValue(),
                        bucket.docCount(),
                        bucket.aggregations().get(TOTAL_TOKENS_AGGREGATION).sum().value(),
                        bucket.aggregations().get(LAST_END_DATE_AGGREGATION).max().value()))
            .toList();
    return ViewResult.builder()
        .rawData(
            AgentInventoryRows.toPage(
                buckets,
                agentDefinitionReader.getAgentDefinitions(
                    AgentRunExecutionPlanInterpreter.tenantIdsByProcessDefinitionKey(
                        context.getReportData())),
                context))
        .build();
  }
}
