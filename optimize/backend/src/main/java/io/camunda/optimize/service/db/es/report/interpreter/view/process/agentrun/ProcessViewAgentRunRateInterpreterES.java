/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agentrun;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_INCIDENT_RATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_LIMIT_HIT_RATE;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_AGENT_RUN_SUCCESS_RATE;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
 * Share of runs with a flag set, in percent. Numerator and denominator come from the same response,
 * so the rate holds per group as well. A run counts as flagged only when the flag is true; a run
 * not enriched yet counts as not flagged. Success is the reverse: no incident on the agent or its
 * tools and no limit hit (D2), so a run not enriched yet counts as successful.
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessViewAgentRunRateInterpreterES implements ProcessViewInterpreterES {

  static final String ALL_RUNS_AGGREGATION = "agentRunsAll";
  static final String FLAGGED_RUNS_AGGREGATION = "agentRunsFlagged";

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(
        PROCESS_VIEW_AGENT_RUN_INCIDENT_RATE,
        PROCESS_VIEW_AGENT_RUN_LIMIT_HIT_RATE,
        PROCESS_VIEW_AGENT_RUN_SUCCESS_RATE);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final ProcessView view = context.getPlan().getView();
    return Map.of(
        ALL_RUNS_AGGREGATION,
        new Aggregation.Builder().filter(f -> f.matchAll(m -> m)),
        FLAGGED_RUNS_AGGREGATION,
        new Aggregation.Builder()
            .filter(
                view == PROCESS_VIEW_AGENT_RUN_SUCCESS_RATE
                    ? succeededQuery()
                    : Query.of(q -> q.term(t -> t.field(flagFieldFor(view)).value(true)))));
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final long all = aggs.get(ALL_RUNS_AGGREGATION).filter().docCount();
    final long flagged = aggs.get(FLAGGED_RUNS_AGGREGATION).filter().docCount();
    return createViewResult(rate(flagged, all));
  }

  static Double rate(final long flagged, final long all) {
    return all == 0 ? null : (double) flagged / all * 100;
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return createViewResult(null);
  }

  private static ViewResult createViewResult(final Double value) {
    return ViewResult.builder().viewMeasure(ViewMeasure.builder().value(value).build()).build();
  }

  /** Runs without an incident on the agent or its tools and without a limit hit. */
  public static Query succeededQuery() {
    return Query.of(
        q ->
            q.bool(
                b ->
                    b.mustNot(m -> m.term(t -> t.field(AgentRunIndex.HAS_INCIDENT).value(true)))
                        .mustNot(m -> m.term(t -> t.field(AgentRunIndex.HIT_LIMIT).value(true)))
                        .mustNot(
                            m ->
                                m.range(
                                    r ->
                                        r.number(
                                            n ->
                                                n.field(AgentRunIndex.TOOL_INCIDENT_COUNT)
                                                    .gt(0.0))))));
  }

  private static String flagFieldFor(final ProcessView view) {
    return view == PROCESS_VIEW_AGENT_RUN_INCIDENT_RATE
        ? AgentRunIndex.HAS_INCIDENT
        : AgentRunIndex.HIT_LIMIT;
  }
}
