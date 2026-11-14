/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agentrun;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_ADOPTION_RATE;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Process instances with at least one agent run, as a percentage of the completed process instances
 * of agentic process definitions. The numerator is a cardinality on the agent index; the
 * denominator is counted on the process instance index by the agent run plan interpreter and passed
 * in under {@link #DENOMINATOR_KEY}.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewAgentRunAdoptionRateInterpreterES implements ProcessViewInterpreterES {

  public static final String DENOMINATOR_KEY = "adoptionRateDenominator";
  static final String PROCESS_INSTANCES_AGGREGATION = "agentRunProcessInstances";
  // Counts are exact up to this many distinct process instances.
  private static final int PRECISION_THRESHOLD = 40_000;

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_AGENT_RUN_ADOPTION_RATE);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Map.of(
        PROCESS_INSTANCES_AGGREGATION,
        new Aggregation.Builder()
            .cardinality(
                c ->
                    c.field(AgentRunIndex.PROCESS_INSTANCE_ID)
                        .precisionThreshold(PRECISION_THRESHOLD)));
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final long withAgent = aggs.get(PROCESS_INSTANCES_AGGREGATION).cardinality().value();
    final Long denominator = context.getUnfilteredInstanceCountsByGroupKey().get(DENOMINATOR_KEY);
    return createViewResult(rate(withAgent, denominator));
  }

  static Double rate(final long withAgent, final Long completedProcessInstances) {
    if (completedProcessInstances == null || completedProcessInstances == 0) {
      return null;
    }
    // Late process-instance imports can briefly leave the denominator behind the agent index.
    return Math.min(100.0, (double) withAgent / completedProcessInstances * 100);
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return createViewResult(null);
  }

  private static ViewResult createViewResult(final Double value) {
    return ViewResult.builder().viewMeasure(ViewMeasure.builder().value(value).build()).build();
  }
}
