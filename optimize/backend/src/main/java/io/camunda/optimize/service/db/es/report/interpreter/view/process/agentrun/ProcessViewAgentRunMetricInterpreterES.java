/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agentrun;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_CACHE_READ_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_CACHE_WRITE_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_DURATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_HUMAN_STEPS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_HUMAN_WAIT;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_INPUT_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_MODEL_CALLS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_OUTPUT_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_REASONING_TOKENS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_TOOL_CALLS;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_TOTAL_TOKENS;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.aggregations.AggregationStrategyES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Numeric per-run metrics with the report's aggregation types (avg, sum, percentiles, ...). Under
 * the model and tool group-bys the aggregations run inside the nested per-model and per-tool
 * entries, so the field is resolved from the group-by.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewAgentRunMetricInterpreterES
    extends AbstractProcessViewMultiAggregationInterpreterES {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(
        PROCESS_VIEW_AGENT_RUN_DURATION,
        PROCESS_VIEW_AGENT_RUN_TOTAL_TOKENS,
        PROCESS_VIEW_AGENT_RUN_MODEL_CALLS,
        PROCESS_VIEW_AGENT_RUN_HUMAN_STEPS,
        PROCESS_VIEW_AGENT_RUN_TOOL_CALLS,
        PROCESS_VIEW_AGENT_RUN_INPUT_TOKENS,
        PROCESS_VIEW_AGENT_RUN_OUTPUT_TOKENS,
        PROCESS_VIEW_AGENT_RUN_CACHE_WRITE_TOKENS,
        PROCESS_VIEW_AGENT_RUN_CACHE_READ_TOKENS,
        PROCESS_VIEW_AGENT_RUN_REASONING_TOKENS,
        PROCESS_VIEW_AGENT_RUN_HUMAN_WAIT);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final String field = fieldFor(context.getPlan());
    return getAggregationStrategies(context.getReportData()).stream()
        .map(strategy -> strategy.createAggregationBuilder(null, field))
        .collect(Collectors.toMap(Pair::key, Pair::value));
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    for (final AggregationStrategyES<?> strategy :
        getAggregationStrategies(context.getReportData())) {
      viewResultBuilder.viewMeasure(
          ViewMeasure.builder()
              .aggregationType(strategy.getAggregationType())
              .value(strategy.getValue(aggs))
              .build());
    }
    return viewResultBuilder.build();
  }

  static String fieldFor(final ProcessExecutionPlan plan) {
    final ProcessGroupBy groupBy = plan.getGroupBy();
    return switch (plan.getView()) {
      // Sum of the agent's element instance durations without user-task tool time (D1, Q2).
      case PROCESS_VIEW_AGENT_RUN_DURATION -> AgentRunIndex.ACTIVE_DURATION_MS;
      case PROCESS_VIEW_AGENT_RUN_HUMAN_WAIT -> AgentRunIndex.HUMAN_WAIT_DURATION_MS;
      case PROCESS_VIEW_AGENT_RUN_MODEL_CALLS -> AgentRunIndex.MODEL_CALLS;
      // User-task tools of the run only, not every user task of the process instance (Q5).
      case PROCESS_VIEW_AGENT_RUN_HUMAN_STEPS -> AgentRunIndex.HUMAN_STEP_COUNT;
      case PROCESS_VIEW_AGENT_RUN_INPUT_TOKENS -> AgentRunIndex.INPUT_TOKENS;
      case PROCESS_VIEW_AGENT_RUN_OUTPUT_TOKENS -> AgentRunIndex.OUTPUT_TOKENS;
      case PROCESS_VIEW_AGENT_RUN_CACHE_WRITE_TOKENS -> AgentRunIndex.CACHE_CREATION_TOKENS;
      case PROCESS_VIEW_AGENT_RUN_CACHE_READ_TOKENS -> AgentRunIndex.CACHE_READ_TOKENS;
      case PROCESS_VIEW_AGENT_RUN_REASONING_TOKENS -> AgentRunIndex.REASONING_TOKENS;
      case PROCESS_VIEW_AGENT_RUN_TOTAL_TOKENS ->
          groupBy == ProcessGroupBy.PROCESS_GROUP_BY_AGENT_RUN_MODEL
              ? AgentRunIndex.TOKENS_BY_MODEL + "." + AgentRunDto.ModelTokensDto.Fields.totalTokens
              : AgentRunIndex.TOTAL_TOKENS;
      case PROCESS_VIEW_AGENT_RUN_TOOL_CALLS ->
          groupBy == ProcessGroupBy.PROCESS_GROUP_BY_AGENT_RUN_TOOL
              ? AgentRunIndex.TOOL_CALLS_BY_TOOL + "." + AgentRunIndex.TOOL_CALLS_COUNT
              : AgentRunIndex.TOOL_CALLS;
      default -> throw new IllegalStateException("Unsupported agent run view " + plan.getView());
    };
  }
}
